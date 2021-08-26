# 写入 Index 流程

- 目录
    - [RestCreateIndexAction](#RestCreateIndexAction)
    - [TransportCreateIndexAction](#TransportCreateIndexAction)
    - [总结](#总结)

Index 是一组同构 Document 的集合，分布于不同节点上的不同分片中，它的写入操作包括但不限于 create、delete、close、open 等。

本文以创建索引为例，讲解写入 Index 流程。创建索引的过程，从 ElasticSearch 集群上来说就是写入 Index 元数据的过程，这一操作由 Master 节点完成。

### RestCreateIndexAction

Netty4HttpRequestHandler 是 ElasticSearch 的网络请求处理器。当客户端发起一个 `PUT /<index>` （创建 Index）请求，由 Netty4HttpRequestHandler 接收，
交由 RestController 调度对应的 action handler 处理该请求，即 `RestCreateIndexAction`。

- RestCreateIndexAction#prepareRequest

```java
@Override
public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
final boolean includeTypeName = request.paramAsBoolean(INCLUDE_TYPE_NAME_PARAMETER,
        DEFAULT_INCLUDE_TYPE_NAME_POLICY);

        if (request.hasParam(INCLUDE_TYPE_NAME_PARAMETER)) {
        deprecationLogger.deprecatedAndMaybeLog("create_index_with_types", TYPES_DEPRECATION_MESSAGE);
        }

        CreateIndexRequest createIndexRequest = new CreateIndexRequest(request.param("index"));

        if (request.hasContent()) {
        Map<String, Object> sourceAsMap = XContentHelper.convertToMap(request.requiredContent(), false,
        request.getXContentType()).v2();
        sourceAsMap = prepareMappings(sourceAsMap, includeTypeName);
        createIndexRequest.source(sourceAsMap, LoggingDeprecationHandler.INSTANCE);
        }

        createIndexRequest.timeout(request.paramAsTime("timeout", createIndexRequest.timeout()));
        createIndexRequest.masterNodeTimeout(request.paramAsTime("master_timeout", createIndexRequest.masterNodeTimeout()));
        createIndexRequest.waitForActiveShards(ActiveShardCount.parseString(request.param("wait_for_active_shards")));
        return channel -> client.admin().indices().create(createIndexRequest, new RestToXContentListener<>(channel));
        }
```

`RestCreateIndexAction#prepareRequest` 做处理 action 前准备工作，request 中封装了客户端的 REST 请求，通过解析 request 得到 createIndexRequest，
最终把 createIndexRequest 作为参数调用 `client.admin().indices().create` 方法执行相关业务。

- NodeClient#doExecute

```java
// org/elasticsearch/client/node/NodeClient.java#doExecute
public <Request extends ActionRequest, Response extends ActionResponse>
void doExecute(ActionType<Response> action, Request request, ActionListener<Response> listener) {
        executeLocally(action, request, listener);
        }
```

程序会执行到 `NodeClient#doExecute`，它又调了 executeLocally，从方法名上看，该方法以为本地执行。

- NodeClient#executeLocally

```java
// org/elasticsearch/client/node/NodeClient.java#executeLocally
public <    Request extends ActionRequest,
        Response extends ActionResponse
        > Task executeLocally(ActionType<Response> action, Request request, ActionListener<Response> listener) {
        // 获取对应的 ACTION，执行请求
        return transportAction(action).execute(request, listener);
        }
```

这里需要关注 `transportAction(action).execute(request, listener);`，它分为两个步骤：第一步，transportAction(action) 获取对应的 action handler；第二步，使用获取到 handler 处理请求。

- NodeClient#transportAction

```java
// org/elasticsearch/client/node/NodeClient.java#transportAction
private <    Request extends ActionRequest,
        Response extends ActionResponse
        > TransportAction<Request, Response> transportAction(ActionType<Response> action) {
        if (actions == null) {
        throw new IllegalStateException("NodeClient has not been initialized");
        }
        TransportAction<Request, Response> transportAction = actions.get(action);
        if (transportAction == null) {
        throw new IllegalStateException("failed to find action [" + action + "] to execute");
        }
        return transportAction;
        }
```

这里行为很简单，actions 是一个 map，以 actions 为键获取值，此处是获取 TransportAction，即 handler。
这个 actions 是在 `ElasticSearch 启动 > 实例化 Node > 初始化 node client` 时缓存了 action 对应的 handler。

CreateIndex 对应的 handler 是 TransportCreateIndexAction。

### TransportCreateIndexAction

TransportAction 是最终的请求处理类，每种 action 都实现了各自的 TransportAction。所有的 TransportAction 都保持着相同的处理模型：当接收到请求时，首先判断本节点能否处理，如果能够处理则调用相关的方法处理得到结果返回，否则内部转发到其它节点进行处理。

TransportCreateIndexAction 是 TransportAction 的实现之一，类图如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/TransportCreateIndexAction类图.png" width="400px">
</div>

创建索引的过程，从 ElasticSearch 集群上来说就是写入 Index 元数据的过程，这一操作由 Master 节点完成。因此，TransportCreateIndexAction 继承了 TransportMasterNodeAction，，并实现了 materOperation 方法。

- TransportAction#execute

承接前面讲到的 `transportAction(action).execute(request, listener);`，此处调用的是 `TransportCreateIndexAction#execute`。`TransportCreateIndexAction#execute` 继承自 TransportAction，代码如下：

```java
// org/elasticsearch/action/support/TransportAction.java#execute
public final void execute(Task task, Request request, ActionListener<Response> listener) {
    ActionRequestValidationException validationException = request.validate();
    if (validationException != null) {
        listener.onFailure(validationException);
        return;
    }

    if (task != null && request.getShouldStoreResult()) {
        listener = new TaskResultStoringActionListener<>(taskManager, task, listener);
    }

    RequestFilterChain<Request, Response> requestFilterChain = new RequestFilterChain<>(this, logger);
    requestFilterChain.proceed(task, actionName, request, listener);
}
```

最终对调用到 `TransportCreateIndexAction#doExecute` 方法，改方法继承自

- TransportMasterNodeAction#doExecute

```java
// org/elasticsearch/action/support/master/TransportMasterNodeAction.java#doExecute
protected void doExecute(Task task, final Request request, ActionListener<Response> listener) {
    new AsyncSingleAction(task, request, listener).start();
}
```

AsyncSingleAction# 是个内部类。

- AsyncSingleAction#start

```java
// org/elasticsearch/action/support/master/TransportMasterNodeAction.java#start
public void start() {
    ClusterState state = clusterService.state();
    this.observer
        = new ClusterStateObserver(state, clusterService, request.masterNodeTimeout(), logger, threadPool.getThreadContext());
    doStart(state);
}
```

该方法获取了 ClusterState，实例化 ClusterStateObserver，将 ClusterState 作为参数调用 doStart。

- AsyncSingleAction#doStart

```java
// org/elasticsearch/action/support/master/TransportMasterNodeAction.java#doStart
protected void doStart(ClusterState clusterState) {
    try {
        final Predicate<ClusterState> masterChangePredicate = MasterNodeChangePredicate.build(clusterState);
        final DiscoveryNodes nodes = clusterState.nodes();
        if (nodes.isLocalNodeElectedMaster() || localExecute(request)) {
            // check for block, if blocked, retry, else, execute locally
            final ClusterBlockException blockException = checkBlock(request, clusterState);
            if (blockException != null) {
                if (!blockException.retryable()) {
                    listener.onFailure(blockException);
                } else {
                    logger.trace("can't execute due to a cluster block, retrying", blockException);
                    retry(blockException, newState -> {
                        try {
                            ClusterBlockException newException = checkBlock(request, newState);
                            return (newException == null || !newException.retryable());
                        } catch (Exception e) {
                            // accept state as block will be rechecked by doStart() and listener.onFailure() then called
                            logger.trace("exception occurred during cluster block checking, accepting state", e);
                            return true;
                        }
                    });
                }
            } else {
                ActionListener<Response> delegate = ActionListener.delegateResponse(listener, (delegatedListener, t) -> {
                    if (t instanceof FailedToCommitClusterStateException || t instanceof NotMasterException) {
                        logger.debug(() -> new ParameterizedMessage("master could not publish cluster state or " +
                            "stepped down before publishing action [{}], scheduling a retry", actionName), t);
                        retry(t, masterChangePredicate);
                    } else {
                        delegatedListener.onFailure(t);
                    }
                });
                threadPool.executor(executor)
                    .execute(ActionRunnable.wrap(delegate, l -> masterOperation(task, request, clusterState, l)));
            }
        } else {
            if (nodes.getMasterNode() == null) {
                logger.debug("no known master node, scheduling a retry");
                retry(null, masterChangePredicate);
            } else {
                DiscoveryNode masterNode = nodes.getMasterNode();
                final String actionName = getMasterActionName(masterNode);
                transportService.sendRequest(masterNode, actionName, request,
                    new ActionListenerResponseHandler<Response>(listener, TransportMasterNodeAction.this::read) {
                        @Override
                        public void handleException(final TransportException exp) {
                            Throwable cause = exp.unwrapCause();
                            if (cause instanceof ConnectTransportException) {
                                // we want to retry here a bit to see if a new master is elected
                                logger.debug("connection exception while trying to forward request with action name [{}] to " +
                                        "master node [{}], scheduling a retry. Error: [{}]",
                                    actionName, nodes.getMasterNode(), exp.getDetailedMessage());
                                retry(cause, masterChangePredicate);
                            } else {
                                listener.onFailure(exp);
                            }
                        }
                });
            }
        }
    } catch (Exception e) {
        listener.onFailure(e);
    }
}
```

改方法会判断本节点是否是 Master 节点，如果是才执行 CreateIndex 操作，如果不是会将请求转发到 Master 节点执行。

当本节点是 Master 节点时会执行 `masterOperation(task, request, clusterState, l)`，最终调用的是由 TransportCreateIndexAction 实现的 masterOperation 方法。

- TransportCreateIndexAction#masterOperation

```java
// org/elasticsearch/action/admin/indices/create/TransportCreateIndexAction.java#masterOperation
@Override
protected void masterOperation(final CreateIndexRequest request, final ClusterState state,
                               final ActionListener<CreateIndexResponse> listener) {
    String cause = request.cause();
    if (cause.length() == 0) {
        cause = "api";
    }
    // 获取 Index 名
    final String indexName = indexNameExpressionResolver.resolveDateMathExpression(request.index());
    // 初始化 CreateIndexClusterStateUpdateRequest 属性，用于构建 StateUpdateTask
    final CreateIndexClusterStateUpdateRequest updateRequest =
        new CreateIndexClusterStateUpdateRequest(cause, indexName, request.index())
            .ackTimeout(request.timeout()).masterNodeTimeout(request.masterNodeTimeout())
            .settings(request.settings()).mappings(request.mappings())
            .aliases(request.aliases())
            .waitForActiveShards(request.waitForActiveShards());
    
    // 执行 CreateIndex
    createIndexService.createIndex(updateRequest, ActionListener.map(listener, response ->
        new CreateIndexResponse(response.isAcknowledged(), response.isShardsAcknowledged(), indexName)));
}
```

该方法比较简单，初始化 CreateIndexClusterStateUpdateRequest 属性，用于构建 StateUpdateTask，然后执行 `MetaDataCreateIndexService#createIndex` 创建索引。

- MetaDataCreateIndexService#createIndex

```java
// org/elasticsearch/cluster/metadata/MetaDataCreateIndexService.java#createIndex
// 创建一个 Index 并等待该 Index 的副本分片转变为活跃状态。
public void createIndex(final CreateIndexClusterStateUpdateRequest request,
                        final ActionListener<CreateIndexClusterStateUpdateResponse> listener) {
    onlyCreateIndex(request, ActionListener.wrap(response -> {
        if (response.isAcknowledged()) {
            activeShardsObserver.waitForActiveShards(new String[]{request.index()}, request.waitForActiveShards(), request.ackTimeout(),
                shardsAcknowledged -> {
                    if (shardsAcknowledged == false) {
                        logger.debug("[{}] index created, but the operation timed out while waiting for " +
                                         "enough shards to be started.", request.index());
                    }
                    listener.onResponse(new CreateIndexClusterStateUpdateResponse(response.isAcknowledged(), shardsAcknowledged));
                }, listener::onFailure);
        } else {
            listener.onResponse(new CreateIndexClusterStateUpdateResponse(false, false));
        }
    }, listener::onFailure));
}
```

该方法负责 CreateIndex，并等待该 Index 相应的副本分片状态转变为活跃状态。

进一步，onlyCreateIndex。

- MetaDataCreateIndexService#onlyCreateIndex

```java
// org/elasticsearch/cluster/metadata/MetaDataCreateIndexService.java#onlyCreateIndex
private void onlyCreateIndex(final CreateIndexClusterStateUpdateRequest request,
                             final ActionListener<ClusterStateUpdateResponse> listener) {
    // 构建 settings 属性，如 number_of_shards、number_of_replicas
    Settings.Builder updatedSettingsBuilder = Settings.builder();
    Settings build = updatedSettingsBuilder.put(request.settings()).normalizePrefix(IndexMetaData.INDEX_SETTING_PREFIX).build();
    indexScopedSettings.validate(build, true); // we do validate here - index setting must be consistent
    request.settings(build);
    // 提交 StateUpdateTask 任务
    clusterService.submitStateUpdateTask(
            "create-index [" + request.index() + "], cause [" + request.cause() + "]",
            new IndexCreationTask(
                    logger,
                    allocationService,
                    request,
                    listener,
                    indicesService,
                    aliasValidator,
                    xContentRegistry,
                    settings,
                    this::validate,
                    indexScopedSettings));
}
```

该方法构建了 settings 属性和创建了 StateUpdateTask，最终提交 StateUpdateTask。

- IndexCreationTask

IndexCreationTask 构造函数：

```java
IndexCreationTask(Logger logger, AllocationService allocationService, CreateIndexClusterStateUpdateRequest request,
                  ActionListener<ClusterStateUpdateResponse> listener, IndicesService indicesService,
                  AliasValidator aliasValidator, NamedXContentRegistry xContentRegistry,
                  Settings settings, IndexValidator validator, IndexScopedSettings indexScopedSettings) {
    super(Priority.URGENT, request, listener);
    this.request = request;
    this.logger = logger;
    this.allocationService = allocationService;
    this.indicesService = indicesService;
    this.aliasValidator = aliasValidator;
    this.xContentRegistry = xContentRegistry;
    this.settings = settings;
    this.validator = validator;
    this.indexScopedSettings = indexScopedSettings;
}
```

IndexCreationTask 存储了写入 Index 需要的相关属性和 priority （优先级）参数，CreateIndex 对应的 priority 为 1。

- MasterService#submitStateUpdateTasks

```java
// org/elasticsearch/cluster/service/MasterService.java#submitStateUpdateTasks
public <T> void submitStateUpdateTasks(final String source,
                                       final Map<T, ClusterStateTaskListener> tasks, final ClusterStateTaskConfig config,
                                       final ClusterStateTaskExecutor<T> executor) {
    if (!lifecycle.started()) {
        return;
    }
    final ThreadContext threadContext = threadPool.getThreadContext();
    final Supplier<ThreadContext.StoredContext> supplier = threadContext.newRestorableContext(true);
    try (ThreadContext.StoredContext ignore = threadContext.stashContext()) {
        threadContext.markAsSystemContext();
        // 对 IndexCreationTask 做安全校验并转变为 UpdateTask，UpdateTask 是个包含优先级的任务，是 PrioritizedRunnable 的子类
        List<Batcher.UpdateTask> safeTasks = tasks.entrySet().stream()
            .map(e -> taskBatcher.new UpdateTask(config.priority(), source, e.getKey(), safe(e.getValue(), supplier), executor))
            .collect(Collectors.toList());
        // 批量提交任务
        taskBatcher.submitTasks(safeTasks, config.timeout());
    } catch (EsRejectedExecutionException e) {
        // ignore cases where we are shutting down..., there is really nothing interesting
        // to be done here...
        if (!lifecycle.stoppedOrClosed()) {
            throw e;
        }
    }
}
```

该方法对 IndexCreationTask 做安全校验并转变为 UpdateTask，UpdateTask 是个包含优先级的任务，是 PrioritizedRunnable 的子类。之后通过 `taskBatcher.submitTasks(safeTasks, config.timeout());` 继续提交任务，经过一些校验，最终调用 execute 执行任务，完成 CreateIndex。

### 总结

上述内容以创建 Index 流程展示了写入 Index 流程，从本质上说，写入 Index 是对 Index 元数据的变更，必须由 Master 节点完成，并更新涉及到的主从分片状态。