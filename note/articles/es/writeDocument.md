# 写入 Document 流程

- 目录
  - [RestIndexAction](#RestIndexAction)
  - [TransportIndexAction](#TransportIndexAction)
  - [perform](#perform)

Index 是一组同构 Document 的集合，分布于不同节点上的不同分片中。

Document 的基本写入模型：

- 当一个节点接收到写入操作，这个节点将作为`协调节点`，它会通过解析 routing 参数来确定副本组，之后转发该操作到相关副本组所在的节点。
- 当主分片接收到操作请求，会对操作进行验证并本地执行。操作成功执行后，主分片并行转发该操作到副本分片，一旦所有副本分片成功执行操作并恢复主分片，主分片会把请求执行成功的信息返回给协调节点，由协调节点返回给客户端。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/写入Document流程.png" width="600px">
</div>

本文主要讲解写入 Document 流程。

### RestIndexAction

Netty4HttpRequestHandler 是 ElasticSearch 的网络请求处理器。当客户端发起一个 REST 请求，由 Netty4HttpRequestHandler 接收，交由 RestController 调度对应的 action handler 处理该请求。

写入 Document 对应的处理器是 `RestIndexAction`。

RestIndexAction 构造函数

```java
// org/elasticsearch/rest/action/document/RestIndexAction.java#RestIndexAction
public RestIndexAction(RestController controller, ClusterService clusterService) {
    // 注入 clusterService
    this.clusterService = clusterService;

    AutoIdHandler autoIdHandler = new AutoIdHandler();
    // _doc 不管 id 存在与否都会创建或更新
    controller.registerHandler(POST, "/{index}/_doc", autoIdHandler); // auto id creation
    controller.registerHandler(PUT, "/{index}/_doc/{id}", this);
    controller.registerHandler(POST, "/{index}/_doc/{id}", this);

    CreateHandler createHandler = new CreateHandler();
    // _create 必须是 id 不存在才会写入成功
    controller.registerHandler(PUT, "/{index}/_create/{id}", createHandler);
    controller.registerHandler(POST, "/{index}/_create/{id}/", createHandler);

    // Deprecated typed endpoints.
    // 弃用的输入，type 7.x 开始弃用
    controller.registerHandler(POST, "/{index}/{type}", autoIdHandler); // auto id creation
    controller.registerHandler(PUT, "/{index}/{type}/{id}", this);
    controller.registerHandler(POST, "/{index}/{type}/{id}", this);
    controller.registerHandler(PUT, "/{index}/{type}/{id}/_create", createHandler);
    controller.registerHandler(POST, "/{index}/{type}/{id}/_create", createHandler);
}
```

ElasticSearch 7.x 开始弃用 type，仅支持 `_doc` 和 `_create`。它们的区别主要在于 `_create` 仅仅支持新建文档操作，如果 id 存在会抛出异常，新增失败。

- RestIndexAction#prepareRequest

```java
@Override
// org/elasticsearch/rest/action/document/RestIndexAction.java#prepareRequest
public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
    IndexRequest indexRequest;
    // 获取 type
    final String type = request.param("type");

    // 构建一个 IndexRequest
    if (type != null && type.equals(MapperService.SINGLE_MAPPING_NAME) == false) {
        // 如果存在 type，提示 8.x 之后正式移除
        deprecationLogger.deprecatedAndMaybeLog("index_with_types", TYPES_DEPRECATION_MESSAGE);
        indexRequest = new IndexRequest(request.param("index"), type, request.param("id"));
    } else {
        indexRequest = new IndexRequest(request.param("index"));
        indexRequest.id(request.param("id"));
    }
    indexRequest.routing(request.param("routing"));
    indexRequest.setPipeline(request.param("pipeline"));
    indexRequest.source(request.requiredContent(), request.getXContentType());
    indexRequest.timeout(request.paramAsTime("timeout", IndexRequest.DEFAULT_TIMEOUT));
    indexRequest.setRefreshPolicy(request.param("refresh"));
    indexRequest.version(RestActions.parseVersion(request));
    indexRequest.versionType(VersionType.fromString(request.param("version_type"), indexRequest.versionType()));
    indexRequest.setIfSeqNo(request.paramAsLong("if_seq_no", indexRequest.ifSeqNo()));
    indexRequest.setIfPrimaryTerm(request.paramAsLong("if_primary_term", indexRequest.ifPrimaryTerm()));
    String sOpType = request.param("op_type");
    String waitForActiveShards = request.param("wait_for_active_shards");
    if (waitForActiveShards != null) {
        indexRequest.waitForActiveShards(ActiveShardCount.parseString(waitForActiveShards));
    }
    if (sOpType != null) {
        indexRequest.opType(sOpType);
    }
    // 调用 client index 操作 ，由 NodeClient 执行  
    return channel ->
            client.index(indexRequest, new RestStatusToXContentListener<>(channel, r -> r.getLocation(indexRequest.routing())));
}
```

`RestIndexAction#prepareRequest` 做处理 action 前准备工作，request 中封装了客户端的 REST 请求，通过解析 request 得到 indexRequest。完成准备工作之后，调用 `client.index` 执行 index 操作，其中此处的 client 是 NodeClient。

- NodeClient#executeLocally

```java
// org/elasticsearch/client/node/NodeClient.java#executeLocally
public <    Request extends ActionRequest,
            Response extends ActionResponse
        > Task executeLocally(ActionType<Response> action, Request request, TaskListener<Response> listener) {
    return transportAction(action).execute(request, listener);
}
```

`NodeClient#executeLocally` 执行 `transportAction(action).execute(request, listener)`。`transportAction(action)` 获取的 transportAction 是 TransportIndexAction，执行的 execute 操作继承自 TransportAction。

### TransportIndexAction

TransportAction 是请求处理类，每种 action 都实现了各自的 TransportAction。所有的 TransportAction 都保持着相同的处理模型：当接收到请求时，首先判断本节点能否处理，如果能够处理则调用相关的方法处理得到结果返回，否则内部转发到其它节点进行处理。

TransportIndexAction 是 TransportAction 的实现之一，类图如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/TransportIndexAction类图.png" width="600px">
</div>

- TransportIndexAction#execute

TransportIndexAction 的 execute 操作继承自 TransportAction，接着会调用 doExecute 方法，该方法继承自 TransportSingleItemBulkWriteAction。

- TransportSingleItemBulkWriteAction#doExecute

```java
// org/elasticsearch/action/bulk/TransportSingleItemBulkWriteAction.java#doExecute
protected void doExecute(Task task, final Request request, final ActionListener<Response> listener) {
    bulkAction.execute(task, toSingleItemBulkRequest(request), wrapBulkResponse(listener));
}
```

这里的 bulkAction 是 TransportBulkAction，用于处理批量操作。它也是 TransportAction 的实现类，execute 方法会调用自己的 doExecute 方法。

- TransportBulkAction.java#doExecute

```
// org/elasticsearch/action/bulk/TransportBulkAction.java#doExecute
@Override
protected void doExecute(Task task, BulkRequest bulkRequest, ActionListener<BulkResponse> listener) {
    // 获取开始时间
    final long startTime = relativeTime();
    final AtomicArray<BulkItemResponse> responses = new AtomicArray<>(bulkRequest.requests.size());

    // 请求是否使用 pipeline
    boolean hasIndexRequestsWithPipelines = false;
    // 获取集群的状态 metaData
    final MetaData metaData = clusterService.state().getMetaData();
    final Version minNodeVersion = clusterService.state().getNodes().getMinNodeVersion();
    // 遍历 bulk 中请求
    for (DocWriteRequest<?> actionRequest : bulkRequest.requests) {
        // 将 actionRequest 转换成 indexRequest
        IndexRequest indexRequest = getIndexWriteRequest(actionRequest);
        if (indexRequest != null) {
            // Each index request needs to be evaluated, because this method also modifies the IndexRequest
            // 每个索引请求都需要进行评估，因为此方法还会修改 IndexRequest
            boolean indexRequestHasPipeline = resolvePipelines(actionRequest, indexRequest, metaData);
            hasIndexRequestsWithPipelines |= indexRequestHasPipeline;
        }

        if (actionRequest instanceof IndexRequest) {
            IndexRequest ir = (IndexRequest) actionRequest;
            ir.checkAutoIdWithOpTypeCreateSupportedByVersion(minNodeVersion);
            if (ir.getAutoGeneratedTimestamp() != IndexRequest.UNSET_AUTO_GENERATED_TIMESTAMP) {
                throw new IllegalArgumentException("autoGeneratedTimestamp should not be set externally");
            }
        }
    }

    // 请求是否使用 pipeline
    if (hasIndexRequestsWithPipelines) {
        // this method (doExecute) will be called again, but with the bulk requests updated from the ingest node processing but
        // also with IngestService.NOOP_PIPELINE_NAME on each request. This ensures that this on the second time through this method,
        // this path is never taken.
        try {
            if (Assertions.ENABLED) {
                final boolean arePipelinesResolved = bulkRequest.requests()
                    .stream()
                    .map(TransportBulkAction::getIndexWriteRequest)
                    .filter(Objects::nonNull)
                    .allMatch(IndexRequest::isPipelineResolved);
                assert arePipelinesResolved : bulkRequest;
            }
            // 本节点是否是 ingest 节点
            if (clusterService.localNode().isIngestNode()) {
                // ingest 处理请求
                processBulkIndexIngestRequest(task, bulkRequest, listener);
            } else {
                // 如果本节点不是 ingest 节点，应该转发给 ingest 节点去处理请求
                ingestForwarder.forwardIngestRequest(BulkAction.INSTANCE, bulkRequest, listener);
            }
        } catch (Exception e) {
            listener.onFailure(e);
        }
        return;
    }

    // 是否可以自动创建索引
    if (needToCheck()) {
        // 在开始之前，尝试创建批量操作期间需要的所有索引。
        // Step 1: 收集请求中的所有索引
        final Set<String> indices = bulkRequest.requests.stream()
                // delete requests should not attempt to create the index (if the index does not
                // exists), unless an external versioning is used
            .filter(request -> request.opType() != DocWriteRequest.OpType.DELETE
                    || request.versionType() == VersionType.EXTERNAL
                    || request.versionType() == VersionType.EXTERNAL_GTE)
            .map(DocWriteRequest::index)
            .collect(Collectors.toSet());
        // Step 2: 过滤到不存在的索引，然后创建。与此同时，构建一个索引映射，其中包含我们无法创建的索引，以便在尝试运行请求时使用。
        final Map<String, IndexNotFoundException> indicesThatCannotBeCreated = new HashMap<>();
        Set<String> autoCreateIndices = new HashSet<>();
        ClusterState state = clusterService.state();
        for (String index : indices) {
            boolean shouldAutoCreate;
            try {
                shouldAutoCreate = shouldAutoCreate(index, state);
            } catch (IndexNotFoundException e) {
                shouldAutoCreate = false;
                indicesThatCannotBeCreated.put(index, e);
            }
            if (shouldAutoCreate) {
                autoCreateIndices.add(index);
            }
        }
        // Step 3: 创建所有缺失的索引(如果有缺失的话)。在所有创建返回后启动批量。
        if (autoCreateIndices.isEmpty()) {
            // 批量处理请求
            executeBulk(task, bulkRequest, startTime, listener, responses, indicesThatCannotBeCreated);
        } else {
            // autoCreateIndices 不等于空，说明需要等待 Index 创建完毕
            final AtomicInteger counter = new AtomicInteger(autoCreateIndices.size());
            for (String index : autoCreateIndices) {
                // 创建 Index
                createIndex(index, bulkRequest.timeout(), new ActionListener<CreateIndexResponse>() {
                    @Override
                    public void onResponse(CreateIndexResponse result) {
                        // 创建索引完毕后，开始批量处理写入文档请求
                        if (counter.decrementAndGet() == 0) {
                            threadPool.executor(ThreadPool.Names.WRITE).execute(
                                () -> executeBulk(task, bulkRequest, startTime, listener, responses, indicesThatCannotBeCreated));
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (!(ExceptionsHelper.unwrapCause(e) instanceof ResourceAlreadyExistsException)) {
                            // fail all requests involving this index, if create didn't work
                            for (int i = 0; i < bulkRequest.requests.size(); i++) {
                                DocWriteRequest<?> request = bulkRequest.requests.get(i);
                                if (request != null && setResponseFailureIfIndexMatches(responses, i, request, index, e)) {
                                    bulkRequest.requests.set(i, null);
                                }
                            }
                        }
                        if (counter.decrementAndGet() == 0) {
                            executeBulk(task, bulkRequest, startTime, ActionListener.wrap(listener::onResponse, inner -> {
                                inner.addSuppressed(e);
                                listener.onFailure(inner);
                            }), responses, indicesThatCannotBeCreated);
                        }
                    }
                });
            }
        }
    } else {
        // 批量处理请求
        executeBulk(task, bulkRequest, startTime, listener, responses, emptyMap());
    }
}
```

这里会判断是否需要 pipeline，然后就开始准备处理写入 Document 请求。首先会检查是否可以自动创建索引，如果不可以直接进入处理批量请求流程，如果可以会获取并检查索引是否需要创建。如果需要创建索引，批量请求会等待索引创建完毕才开始处理。

- TransportBulkAction#executeBulk

```java
// org/elasticsearch/action/bulk/TransportBulkAction.java#executeBulk
void executeBulk(Task task, final BulkRequest bulkRequest, final long startTimeNanos, final ActionListener<BulkResponse> listener,
        final AtomicArray<BulkItemResponse> responses, Map<String, IndexNotFoundException> indicesThatCannotBeCreated) {
    new BulkOperation(task, bulkRequest, listener, responses, startTimeNanos, indicesThatCannotBeCreated).run();
}
```

BulkOperation 是 Runnable 的实现类，表示批量操作任务。

- BulkOperation#doRun

BulkOperation 是 TransportBulkAction 的内部类，也是 Runnable 的实现类。

```java
// org/elasticsearch/action/bulk/TransportBulkAction.java#doRun
@Override
protected void doRun() {
    // 通过观察者获取集群状态
    final ClusterState clusterState = observer.setAndGetObservedState();
    // 如果集群 Block，则不执行
    if (handleBlockExceptions(clusterState)) {
        return;
    }
    // 获取当前的索引信息，包括不可以创建的索引
    final ConcreteIndices concreteIndices = new ConcreteIndices(clusterState, indexNameExpressionResolver);
    // 获取集群的 metaData 信息
    MetaData metaData = clusterState.metaData();
    // 遍历请求
    for (int i = 0; i < bulkRequest.requests.size(); i++) {
        DocWriteRequest<?> docWriteRequest = bulkRequest.requests.get(i);
        //the request can only be null because we set it to null in the previous step, so it gets ignored
        if (docWriteRequest == null) {
            continue;
        }
        // 如果索引不可用的，添加失败操作
        if (addFailureIfIndexIsUnavailable(docWriteRequest, i, concreteIndices, metaData)) {
            continue;
        }
        Index concreteIndex = concreteIndices.resolveIfAbsent(docWriteRequest);
        try {
            // 根据操作类型，解析请求和相关的元数据
            switch (docWriteRequest.opType()) {
                case CREATE:
                case INDEX:
                    IndexRequest indexRequest = (IndexRequest) docWriteRequest;
                    final IndexMetaData indexMetaData = metaData.index(concreteIndex);
                    MappingMetaData mappingMd = indexMetaData.mappingOrDefault();
                    Version indexCreated = indexMetaData.getCreationVersion();
                    indexRequest.resolveRouting(metaData);
                    indexRequest.process(indexCreated, mappingMd, concreteIndex.getName());
                    break;
                case UPDATE:
                    TransportUpdateAction.resolveAndValidateRouting(metaData, concreteIndex.getName(),
                        (UpdateRequest) docWriteRequest);
                    break;
                case DELETE:
                    docWriteRequest.routing(metaData.resolveWriteIndexRouting(docWriteRequest.routing(), docWriteRequest.index()));
                    // check if routing is required, if so, throw error if routing wasn't specified
                    if (docWriteRequest.routing() == null && metaData.routingRequired(concreteIndex.getName())) {
                        throw new RoutingMissingException(concreteIndex.getName(), docWriteRequest.type(), docWriteRequest.id());
                    }
                    break;
                default: throw new AssertionError("request type not supported: [" + docWriteRequest.opType() + "]");
            }
        } catch (ElasticsearchParseException | IllegalArgumentException | RoutingMissingException e) {
            BulkItemResponse.Failure failure = new BulkItemResponse.Failure(concreteIndex.getName(), docWriteRequest.type(),
                docWriteRequest.id(), e);
            BulkItemResponse bulkItemResponse = new BulkItemResponse(i, docWriteRequest.opType(), failure);
            responses.set(i, bulkItemResponse);
            // make sure the request gets never processed again
            bulkRequest.requests.set(i, null);
        }
    }

    // 首先，检查所有的请求并创建一个 ShardId -> Operations 映射
    // 根据分片，来合并请求
    Map<ShardId, List<BulkItemRequest>> requestsByShard = new HashMap<>();
    for (int i = 0; i < bulkRequest.requests.size(); i++) {
        DocWriteRequest<?> request = bulkRequest.requests.get(i);
        if (request == null) {
            continue;
        }
        String concreteIndex = concreteIndices.getConcreteIndex(request.index()).getName();
        ShardId shardId = clusterService.operationRouting().indexShards(clusterState, concreteIndex, request.id(),
            request.routing()).shardId();
        List<BulkItemRequest> shardRequests = requestsByShard.computeIfAbsent(shardId, shard -> new ArrayList<>());
        shardRequests.add(new BulkItemRequest(i, request));
    }

    if (requestsByShard.isEmpty()) {
        listener.onResponse(new BulkResponse(responses.toArray(new BulkItemResponse[responses.length()]),
            buildTookInMillis(startTimeNanos)));
        return;
    }

    // 分片数量
    final AtomicInteger counter = new AtomicInteger(requestsByShard.size());
    String nodeId = clusterService.localNode().getId();
    for (Map.Entry<ShardId, List<BulkItemRequest>> entry : requestsByShard.entrySet()) {
        final ShardId shardId = entry.getKey();
        // 每个分片需要处理的请求
        final List<BulkItemRequest> requests = entry.getValue();
        // 封装 bulkShardRequest，即 分片需要处理的批量请求
        BulkShardRequest bulkShardRequest = new BulkShardRequest(shardId, bulkRequest.getRefreshPolicy(),
                requests.toArray(new BulkItemRequest[requests.size()]));
        bulkShardRequest.waitForActiveShards(bulkRequest.waitForActiveShards());
        bulkShardRequest.timeout(bulkRequest.timeout());
        if (task != null) {
            bulkShardRequest.setParentTask(nodeId, task.getId());
        }
        // 执行批量请求
        shardBulkAction.execute(bulkShardRequest, new ActionListener<BulkShardResponse>() {
            @Override
            public void onResponse(BulkShardResponse bulkShardResponse) {
                for (BulkItemResponse bulkItemResponse : bulkShardResponse.getResponses()) {
                    // we may have no response if item failed
                    if (bulkItemResponse.getResponse() != null) {
                        bulkItemResponse.getResponse().setShardInfo(bulkShardResponse.getShardInfo());
                    }
                    responses.set(bulkItemResponse.getItemId(), bulkItemResponse);
                }
                if (counter.decrementAndGet() == 0) {
                    finishHim();
                }
            }

            @Override
            public void onFailure(Exception e) {
                // create failures for all relevant requests
                for (BulkItemRequest request : requests) {
                    final String indexName = concreteIndices.getConcreteIndex(request.index()).getName();
                    DocWriteRequest<?> docWriteRequest = request.request();
                    responses.set(request.id(), new BulkItemResponse(request.id(), docWriteRequest.opType(),
                            new BulkItemResponse.Failure(indexName, docWriteRequest.type(), docWriteRequest.id(), e)));
                }
                if (counter.decrementAndGet() == 0) {
                    finishHim();
                }
            }

            private void finishHim() {
                listener.onResponse(new BulkResponse(responses.toArray(new BulkItemResponse[responses.length()]),
                    buildTookInMillis(startTimeNanos)));
            }
        });
    }
}
```

`BulkOperation#doRun` 是 BulkOperation 的任务内容，它对请求根据分片进行合并和提交。

`shardBulkAction.execute` 提交请求，这里的 shardBulkAction 是 TransportShardBulkAction。TransportShardBulkAction 也是 TransportAction 的实现类，execute 方法会调用继承自 TransportReplicationAction 的 doExecute 方法。

- TransportReplicationAction#doExecute

```java
// org/elasticsearch/action/support/replication/TransportReplicationAction.java#doExecute
protected void doExecute(Task task, Request request, ActionListener<Response> listener) {
    assert request.shardId() != null : "request shardId must be set";
    new ReroutePhase((ReplicationTask) task, request, listener).run();
}
```

ReroutePhase 也是 Runnable 的实现类，作用是在将请求路由到目标节点之前，解析请求的索引和分片 Id。

- TransportReplicationAction#ReroutePhase#doRun

ReroutePhase 是 TransportReplicationAction 的内部类。

```java
org/elasticsearch/action/support/replication/TransportReplicationAction.java#doRun
@Override
protected void doRun() {
    setPhase(task, "routing");
    // 通过观察者获取集群状态
    final ClusterState state = observer.setAndGetObservedState();
    // 获取当前索引
    final String concreteIndex = concreteIndex(state, request);
    // 查看集群是否阻塞
    final ClusterBlockException blockException = blockExceptions(state, concreteIndex);
    if (blockException != null) {
        if (blockException.retryable()) {
            logger.trace("cluster is blocked, scheduling a retry", blockException);
            retry(blockException);
        } else {
            finishAsFailed(blockException);
        }
    } else {
        // request does not have a shardId yet, we need to pass the concrete index to resolve shardId
        // 获取索引元数据
        final IndexMetaData indexMetaData = state.metaData().index(concreteIndex);
        if (indexMetaData == null) {
            retry(new IndexNotFoundException(concreteIndex));
            return;
        }
        // 索引是否关闭
        if (indexMetaData.getState() == IndexMetaData.State.CLOSE) {
            throw new IndexClosedException(indexMetaData.getIndex());
        }

        // resolve all derived request fields, so we can route and apply it
        // 设定执行需要存在的分片数
        resolveRequest(indexMetaData, request);
        assert request.waitForActiveShards() != ActiveShardCount.DEFAULT :
            "request waitForActiveShards must be set in resolveRequest";
        // 查看分片的主分片信息
        final ShardRouting primary = primary(state);
        if (retryIfUnavailable(state, primary)) {
            return;
        }
        // 获取主分片上所在的节点信息
        final DiscoveryNode node = state.nodes().get(primary.currentNodeId());
        // 主分片所在节点是否是当前节点
        if (primary.currentNodeId().equals(state.nodes().getLocalNodeId())) {
            // 本地执行
            performLocalAction(state, primary, node, indexMetaData);
        } else {
            // 远程调用
            performRemoteAction(state, primary, node);
        }
    }
}
```

如果主分片所在节点是当前节点，则调用 performLocalAction 本地执行，否则 performRemoteAction 远程调用。

### perform

performLocalAction 和 performRemoteAction 都会调用 `TransportReplicationAction#performAction`，最终会调用 `TransportService#sendRequest`。

TransportReplicationAction 构造函数：

```java
// org/elasticsearch/action/support/replication/TransportReplicationAction.java#TransportReplicationAction
protected TransportReplicationAction(Settings settings, String actionName, TransportService transportService,
                                     ClusterService clusterService, IndicesService indicesService,
                                     ThreadPool threadPool, ShardStateAction shardStateAction,
                                     ActionFilters actionFilters,
                                     IndexNameExpressionResolver indexNameExpressionResolver, Writeable.Reader<Request> requestReader,
                                     Writeable.Reader<ReplicaRequest> replicaRequestReader, String executor,
                                     boolean syncGlobalCheckpointAfterOperation, boolean forceExecutionOnPrimary) {
    super(actionName, actionFilters, transportService.getTaskManager());
    this.threadPool = threadPool;
    this.transportService = transportService;
    this.clusterService = clusterService;
    this.indicesService = indicesService;
    this.shardStateAction = shardStateAction;
    this.indexNameExpressionResolver = indexNameExpressionResolver;
    this.executor = executor;

    this.transportPrimaryAction = actionName + "[p]";
    this.transportReplicaAction = actionName + "[r]";

    transportService.registerRequestHandler(actionName, ThreadPool.Names.SAME, requestReader, this::handleOperationRequest);
    
    // indices:data/write/bulk[s][p]
    transportService.registerRequestHandler(transportPrimaryAction, executor, forceExecutionOnPrimary, true,
        in -> new ConcreteShardRequest<>(requestReader, in), this::handlePrimaryRequest);
    
    // indices:data/write/bulk[s][r]
    // we must never reject on because of thread pool capacity on replicas
    transportService.registerRequestHandler(transportReplicaAction, executor, true, true,
        in -> new ConcreteReplicaRequest<>(replicaRequestReader, in), this::handleReplicaRequest);

    this.transportOptions = transportOptions(settings);

    this.syncGlobalCheckpointAfterOperation = syncGlobalCheckpointAfterOperation;
}
```

TransportReplicationAction 构造函数初始化了 `indices:data/write/bulk[s][p]` 和 `indices:data/write/bulk[s][r]` 的处理器，
`TransportReplicationAction.java#handlePrimaryRequest` 处理主分片写入请求，`TransportReplicationAction.java#handleReplicaRequest` 处理副本分片复制请求。

- 本地执行

如果是本地执行，调用的方法如下：

```java
// org/elasticsearch/transport/TransportService.java#sendRequest
public void sendRequest(long requestId, String action, TransportRequest request, TransportRequestOptions options)
    throws TransportException {
    // 发送本地请求
    sendLocalRequest(requestId, action, request, options);
}
```

本地执行会调用 sendLocalRequest。

```java
// org/elasticsearch/transport/TransportService.java#sendLocalRequest
private void sendLocalRequest(long requestId, final String action, final TransportRequest request, TransportRequestOptions options) {
      final DirectResponseChannel channel = new DirectResponseChannel(localNode, action, requestId, this, threadPool);
      try {
          onRequestSent(localNode, requestId, action, request, options);
          onRequestReceived(requestId, action);
          // 获取处理器
          final RequestHandlerRegistry reg = getRequestHandler(action);
          if (reg == null) {
              throw new ActionNotFoundTransportException("Action [" + action + "] not found");
          }
          // 获取执行任务名称，如 write
          final String executor = reg.getExecutor();
          if (ThreadPool.Names.SAME.equals(executor)) {
              //noinspection unchecked
              // 处理请求
              reg.processMessageReceived(request, channel);
          } else {
              threadPool.executor(executor).execute(new AbstractRunnable() {
                  @Override
                  protected void doRun() throws Exception {
                      //noinspection unchecked
                      // 处理请求
                      reg.processMessageReceived(request, channel);
                  }

                  @Override
                  public boolean isForceExecution() {
                      return reg.isForceExecution();
                  }

                  @Override
                  public void onFailure(Exception e) {
                      try {
                          channel.sendResponse(e);
                      } catch (Exception inner) {
                          inner.addSuppressed(e);
                          logger.warn(() -> new ParameterizedMessage(
                                  "failed to notify channel of error message for action [{}]", action), inner);
                      }
                  }

                  @Override
                  public String toString() {
                      return "processing of [" + requestId + "][" + action + "]: " + request;
                  }
              });
          }

      } catch (Exception e) {
          try {
              channel.sendResponse(e);
          } catch (Exception inner) {
              inner.addSuppressed(e);
              logger.warn(
                  () -> new ParameterizedMessage(
                      "failed to notify channel of error message for action [{}]", action), inner);
          }
      }
  }
```

processMessageReceived 会进一步调用 messageReceived 方法，最终根据 request 的 actionName 匹配到的处理器处理请求，即调用 `TransportReplicationAction#handlePrimaryRequest`

```java
protected void handlePrimaryRequest(final ConcreteShardRequest<Request> request, final TransportChannel channel, final Task task) {
    // 主分片处理任务
    new AsyncPrimaryAction(
        request, new ChannelActionListener<>(channel, transportPrimaryAction, request), (ReplicationTask) task).run();
}
```

AsyncPrimaryAction 实现了 Runnable，

```java
// org/elasticsearch/action/support/replication/TransportReplicationAction.java#AsyncPrimaryAction#doRun
@Override
protected void doRun() throws Exception {
    // 获取分片 Id
    final ShardId shardId = primaryRequest.getRequest().shardId();
    // 根据分片 Id 获取分片
    final IndexShard indexShard = getIndexShard(shardId);
    // 获取分片路由
    final ShardRouting shardRouting = indexShard.routingEntry();
    // we may end up here if the cluster state used to route the primary is so stale that the underlying
    // index shard was replaced with a replica. For example - in a two node cluster, if the primary fails
    // the replica will take over and a replica will be assigned to the first node.
    // 是否是主分片
    if (shardRouting.primary() == false) {
        throw new ReplicationOperation.RetryOnPrimaryException(shardId, "actual shard is not a primary " + shardRouting);
    }
    // allocationId 是否是预期值
    final String actualAllocationId = shardRouting.allocationId().getId();
    if (actualAllocationId.equals(primaryRequest.getTargetAllocationID()) == false) {
        throw new ShardNotFoundException(shardId, "expected allocation id [{}] but found [{}]",
            primaryRequest.getTargetAllocationID(), actualAllocationId);
    }
    // PrimaryTerm 是否是预期值
    final long actualTerm = indexShard.getPendingPrimaryTerm();
    if (actualTerm != primaryRequest.getPrimaryTerm()) {
        throw new ShardNotFoundException(shardId, "expected allocation id [{}] with term [{}] but found [{}]",
            primaryRequest.getTargetAllocationID(), primaryRequest.getPrimaryTerm(), actualTerm);
    }
    // 获取主分片操作许可
    acquirePrimaryOperationPermit(
            indexShard,
            primaryRequest.getRequest(),
            ActionListener.wrap(
                    // 执行主分片操作
                    releasable -> runWithPrimaryShardReference(new PrimaryShardReference(indexShard, releasable)),
                    e -> {
                        if (e instanceof ShardNotInPrimaryModeException) {
                            onFailure(new ReplicationOperation.RetryOnPrimaryException(shardId, "shard is not in primary mode", e));
                        } else {
                            onFailure(e);
                        }
                    }));
}
```

该方法会调用主分片、allocationId 和 PrimaryTerm，接着获取操作许可，通过 runWithPrimaryShardReference 方法执行主分片操作。

```java
void runWithPrimaryShardReference(final PrimaryShardReference primaryShardReference) {
    try {
        // 获取集群状态
        final ClusterState clusterState = clusterService.state();
        // 获取 Index 元数据
        final IndexMetaData indexMetaData = clusterState.metaData().getIndexSafe(primaryShardReference.routingEntry().index());

        final ClusterBlockException blockException = blockExceptions(clusterState, indexMetaData.getIndex().getName());
        if (blockException != null) {
            logger.trace("cluster is blocked, action failed on primary", blockException);
            throw blockException;
        }
        // 主分片是否迁移
        // 如果迁移了需要请求转发到新的主分片所在的节点上
        if (primaryShardReference.isRelocated()) {
            primaryShardReference.close(); // release shard operation lock as soon as possible
            // 设置任务状态为 primary_delegation
            setPhase(replicationTask, "primary_delegation");
            // delegate primary phase to relocation target
            // it is safe to execute primary phase on relocation target as there are no more in-flight operations where primary
            // phase is executed on local shard and all subsequent operations are executed on relocation target as primary phase.
            final ShardRouting primary = primaryShardReference.routingEntry();
            assert primary.relocating() : "indexShard is marked as relocated but routing isn't" + primary;
            final Writeable.Reader<Response> reader = TransportReplicationAction.this::newResponseInstance;
            DiscoveryNode relocatingNode = clusterState.nodes().get(primary.relocatingNodeId());
            // 转发请求
            transportService.sendRequest(relocatingNode, transportPrimaryAction,
                new ConcreteShardRequest<>(primaryRequest.getRequest(), primary.allocationId().getRelocationId(),
                    primaryRequest.getPrimaryTerm()),
                transportOptions,
                new ActionListenerResponseHandler<Response>(onCompletionListener, reader) {
                    @Override
                    public void handleResponse(Response response) {
                        // 设置任务状态为 finished
                        setPhase(replicationTask, "finished");
                        super.handleResponse(response);
                    }

                    @Override
                    public void handleException(TransportException exp) {
                        // 设置任务状态为 finished
                        setPhase(replicationTask, "finished");
                        super.handleException(exp);
                    }
                });
        } else {
            // 设置任务状态为 primary，标识可以执行主分片操作
            setPhase(replicationTask, "primary");

            final ActionListener<Response> referenceClosingListener = ActionListener.wrap(response -> {
                primaryShardReference.close(); // release shard operation lock before responding to caller
                setPhase(replicationTask, "finished");
                onCompletionListener.onResponse(response);
            }, e -> handleException(primaryShardReference, e));

            final ActionListener<Response> globalCheckpointSyncingListener = ActionListener.wrap(response -> {
                if (syncGlobalCheckpointAfterOperation) {
                    final IndexShard shard = primaryShardReference.indexShard;
                    try {
                        shard.maybeSyncGlobalCheckpoint("post-operation");
                    } catch (final Exception e) {
                        // only log non-closed exceptions
                        if (ExceptionsHelper.unwrap(
                            e, AlreadyClosedException.class, IndexShardClosedException.class) == null) {
                            // intentionally swallow, a missed global checkpoint sync should not fail this operation
                            logger.info(
                                new ParameterizedMessage(
                                    "{} failed to execute post-operation global checkpoint sync", shard.shardId()), e);
                        }
                    }
                }
                referenceClosingListener.onResponse(response);
            }, referenceClosingListener::onFailure);

            // 执行复制操作任务
            new ReplicationOperation<>(primaryRequest.getRequest(), primaryShardReference,
                ActionListener.wrap(result -> result.respond(globalCheckpointSyncingListener), referenceClosingListener::onFailure),
                newReplicasProxy(), logger, actionName, primaryRequest.getPrimaryTerm()).execute();
        }
    } catch (Exception e) {
        handleException(primaryShardReference, e);
    }
}
```

`ReplicationOperation#execute` 主副分片开始执行写入和复制。

```java
// org/elasticsearch/action/support/replication/ReplicationOperation.java#execute
public void execute() throws Exception {
    final String activeShardCountFailure = checkActiveShardCount();
    final ShardRouting primaryRouting = primary.routingEntry();
    final ShardId primaryId = primaryRouting.shardId();
    if (activeShardCountFailure != null) {
        finishAsFailed(new UnavailableShardsException(primaryId,
            "{} Timeout: [{}], request: [{}]", activeShardCountFailure, request.timeout(), request));
        return;
    }

    totalShards.incrementAndGet();
    pendingActions.incrementAndGet(); // increase by 1 until we finish all primary coordination
    // 主分片写入和响应处理，即复制操作到副本分片
    // perform 处理主分片
    // 响应处理，即处理副本分片复制 ActionListener.wrap(this::handlePrimaryResult, resultListener::onFailure)
    primary.perform(request, ActionListener.wrap(this::handlePrimaryResult, resultListener::onFailure));
}
```

`primary.perform` 在主分片上执行写入请求，`ActionListener.wrap(this::handlePrimaryResult, resultListener::onFailure)` 负责响应处理，当主分片写入成功就开始复制操作到副本分片。

```java
// org/elasticsearch/action/support/replication/TransportReplicationAction.java#perform
@Override
public void perform(Request request, ActionListener<PrimaryResult<ReplicaRequest, Response>> listener) {
    if (Assertions.ENABLED) {
        listener = ActionListener.map(listener, result -> {
            assert result.replicaRequest() == null || result.finalFailure == null : "a replica request [" + result.replicaRequest()
                + "] with a primary failure [" + result.finalFailure + "]";
            return result;
        });
    }
    assert indexShard.getActiveOperationsCount() != 0 : "must perform shard operation under a permit";
    // 在主分片上执行分片操作
    shardOperationOnPrimary(request, indexShard, listener);
}
```

shardOperationOnPrimary 在主分片上执行分片写入操作，顺着代码执行，快进到 org/elasticsearch/action/bulk/TransportShardBulkAction#executeBulkItemRequest 方法。

executeBulkItemRequest 执行批量项请求并处理请求执行异常。

```java
// org/elasticsearch/action/bulk/TransportShardBulkAction.java#executeBulkItemRequest
static boolean executeBulkItemRequest(BulkPrimaryExecutionContext context, UpdateHelper updateHelper, LongSupplier nowInMillisSupplier,
                                   MappingUpdatePerformer mappingUpdater, Consumer<ActionListener<Void>> waitForMappingUpdate,
                                   ActionListener<Void> itemDoneListener) throws Exception {
    // ...
    if (isDelete) {
        final DeleteRequest request = context.getRequestToExecute();
        result = primary.applyDeleteOperationOnPrimary(version, request.type(), request.id(), request.versionType(),
            request.ifSeqNo(), request.ifPrimaryTerm());
    } else {
        final IndexRequest request = context.getRequestToExecute();
        result = primary.applyIndexOperationOnPrimary(version, request.versionType(), new SourceToParse(
                request.index(), request.type(), request.id(), request.source(), request.getContentType(), request.routing()),
            request.ifSeqNo(), request.ifPrimaryTerm(), request.getAutoGeneratedTimestamp(), request.isRetry());
    }
    // ...
}
```

写入操作，如果是新增或者修改操作会调用 applyIndexOperationOnPrimary 方法，申请在主分片上执行 Index 操作；接着调用 applyIndexOperation 方法实例化 Engine.Index 对象，为写入文档做准备。

写入文档调用链路：`Engine#index > InternalEngine#index > InternalEngine#indexIntoLucene`。

```java
// org/elasticsearch/index/engine/InternalEngine.java#indexIntoLucene
private IndexResult indexIntoLucene(Index index, IndexingStrategy plan)
    throws IOException {
    assert index.seqNo() >= 0 : "ops should have an assigned seq no.; origin: " + index.origin();
    assert plan.versionForIndexing >= 0 : "version must be set. got " + plan.versionForIndexing;
    assert plan.indexIntoLucene || plan.addStaleOpToLucene;
    /* Update the document's sequence number and primary term; the sequence number here is derived here from either the sequence
     * number service if this is on the primary, or the existing document's sequence number if this is on the replica. The
     * primary term here has already been set, see IndexShard#prepareIndex where the Engine$Index operation is created.
     */
    index.parsedDoc().updateSeqID(index.seqNo(), index.primaryTerm());
    index.parsedDoc().version().setLongValue(plan.versionForIndexing);
    try {
        if (plan.addStaleOpToLucene) {
            // 如果该 Document 已经是过时的了，则首先在 Document 中增加 soft-deleted 标志字段，然后再 IndexWriter.addDocument 进行写入
            addStaleDocs(index.docs(), indexWriter);
        } else if (plan.useLuceneUpdateDocument) {
            // 如果该 Document 已经存在则调用 IndexWriter.updateDocument 进行更新
            assert assertMaxSeqNoOfUpdatesIsAdvanced(index.uid(), index.seqNo(), true, true);
            updateDocs(index.uid(), index.docs(), indexWriter);
        } else {
            // document does not exists, we can optimize for create, but double check if assertions are running
            // Document 不存在，则调用 IndexWriter.addDocument 创建新 Document
            assert assertDocDoesNotExist(index, canOptimizeAddDocument(index) == false);
            addDocs(index.docs(), indexWriter);
        }
        return new IndexResult(plan.versionForIndexing, index.primaryTerm(), index.seqNo(), plan.currentNotFoundOrDeleted);
    } catch (Exception ex) {
        if (ex instanceof AlreadyClosedException == false &&
            indexWriter.getTragicException() == null && treatDocumentFailureAsTragicError(index) == false) {
            return new IndexResult(ex, Versions.MATCH_ANY, index.primaryTerm(), index.seqNo());
        } else {
            throw ex;
        }
    }
}
```

这里根据文档状态，才去不同的行为写入 Lucene，完成主分片的写入。

当主分片写入成功，`ActionListener.wrap(this::handlePrimaryResult, resultListener::onFailure)` 负责响应处理，即将操作复制到副本分片。

```java
// org/elasticsearch/action/support/replication/ReplicationOperation.java#handlePrimaryResult
// 处理主分片操作响应结构
private void handlePrimaryResult(final PrimaryResultT primaryResult) {
    this.primaryResult = primaryResult;
    primary.updateLocalCheckpointForShard(primary.routingEntry().allocationId().getId(), primary.localCheckpoint());
    primary.updateGlobalCheckpointForShard(primary.routingEntry().allocationId().getId(), primary.globalCheckpoint());
    final ReplicaRequest replicaRequest = primaryResult.replicaRequest();
    // 将请求转发给副分片
    if (replicaRequest != null) {
        if (logger.isTraceEnabled()) {
            logger.trace("[{}] op [{}] completed on primary for request [{}]", primary.routingEntry().shardId(), opType, request);
        }
        // we have to get the replication group after successfully indexing into the primary in order to honour recovery semantics.
        // we have to make sure that every operation indexed into the primary after recovery start will also be replicated
        // to the recovery target. If we used an old replication group, we may miss a recovery that has started since then.
        // we also have to make sure to get the global checkpoint before the replication group, to ensure that the global checkpoint
        // is valid for this replication group. If we would sample in the reverse, the global checkpoint might be based on a subset
        // of the sampled replication group, and advanced further than what the given replication group would allow it to.
        // This would entail that some shards could learn about a global checkpoint that would be higher than its local checkpoint.
        // 获取全局检查点
        final long globalCheckpoint = primary.computedGlobalCheckpoint();
        // we have to capture the max_seq_no_of_updates after this request was completed on the primary to make sure the value of
        // max_seq_no_of_updates on replica when this request is executed is at least the value on the primary when it was executed
        // on.
        final long maxSeqNoOfUpdatesOrDeletes = primary.maxSeqNoOfUpdatesOrDeletes();
        assert maxSeqNoOfUpdatesOrDeletes != SequenceNumbers.UNASSIGNED_SEQ_NO : "seqno_of_updates still uninitialized";
        // 获取分片组
        final ReplicationGroup replicationGroup = primary.getReplicationGroup();
        markUnavailableShardsAsStale(replicaRequest, replicationGroup);
        // 执行副本分片写入操作
        performOnReplicas(replicaRequest, globalCheckpoint, maxSeqNoOfUpdatesOrDeletes, replicationGroup);
    }
    successfulShards.incrementAndGet();  // mark primary as successful
    // 当所有的分片都处理完这个请求后，不管是查询失败还是成功、超时，每执行一次，都会总查询的总分片数减 1，直到所有分片都返回了，再调用 finish() 方法响应客户端
    decPendingAndFinishIfNeeded();
}
```

接着调用 performOnReplicas 执行副本分片写入操作，

```java
// org/elasticsearch/action/support/replication/ReplicationOperation.java#performOnReplicas
// 执行副本分片写入操作
private void performOnReplicas(final ReplicaRequest replicaRequest, final long globalCheckpoint,
                               final long maxSeqNoOfUpdatesOrDeletes, final ReplicationGroup replicationGroup) {
    // for total stats, add number of unassigned shards and
    // number of initializing shards that are not ready yet to receive operations (recovery has not opened engine yet on the target)
    totalShards.addAndGet(replicationGroup.getSkippedShards().size());

    final ShardRouting primaryRouting = primary.routingEntry();

    for (final ShardRouting shard : replicationGroup.getReplicationTargets()) {
        // 不同的 allocation 才执行，相同是当前分片，即主分片（并且过滤掉了未准备好的分片）
        if (shard.isSameAllocation(primaryRouting) == false) {
            // 执行副本分片写入操作
            performOnReplica(shard, replicaRequest, globalCheckpoint, maxSeqNoOfUpdatesOrDeletes);
        }
    }
}
```

这里会过滤掉未准备好的分片，performOnReplica 方法只处理可写入的副本，这个方法里面会调用 `ReplicasProxy#performOn`。

```java
@Override
public void performOn(
        final ShardRouting replica,
        final ReplicaRequest request,
        final long primaryTerm,
        final long globalCheckpoint,
        final long maxSeqNoOfUpdatesOrDeletes,
        final ActionListener<ReplicationOperation.ReplicaResponse> listener) {
    // 获取副本分片所在节点 Id
    String nodeId = replica.currentNodeId();
    final DiscoveryNode node = clusterService.state().nodes().get(nodeId);
    if (node == null) {
        listener.onFailure(new NoNodeAvailableException("unknown node [" + nodeId + "]"));
        return;
    }
    final ConcreteReplicaRequest<ReplicaRequest> replicaRequest = new ConcreteReplicaRequest<>(
        request, replica.allocationId().getId(), primaryTerm, globalCheckpoint, maxSeqNoOfUpdatesOrDeletes);
    final ActionListenerResponseHandler<ReplicaResponse> handler = new ActionListenerResponseHandler<>(listener,
        ReplicaResponse::new);
    // 发送副本分片复制操作请求
    transportService.sendRequest(node, transportReplicaAction, replicaRequest, transportOptions, handler);
}
```

这个方法很简单，获取分别分片所在节点，通过 TransportService 与该节点通信，发起复制操作请求。

Netty4MessageChannelHandler 接收到请求后，进行相关处理。

TransportReplicationAction 构造函数初始化了 `indices:data/write/bulk[s][p]` 和 `indices:data/write/bulk[s][r]` 的处理器，
`TransportReplicationAction.java#handlePrimaryRequest` 处理主分片写入请求，`TransportReplicationAction.java#handleReplicaRequest` 处理副本分片复制请求。

```java
// TransportReplicationAction.java#handlePrimaryRequest
// indices:data/write/bulk[s][p]
transportService.registerRequestHandler(transportPrimaryAction, executor, forceExecutionOnPrimary, true,
    in -> new ConcreteShardRequest<>(requestReader, in), this::handlePrimaryRequest);

// TransportReplicationAction.java#handleReplicaRequest
// indices:data/write/bulk[s][r]
// we must never reject on because of thread pool capacity on replicas
transportService.registerRequestHandler(transportReplicaAction, executor, true, true,
    in -> new ConcreteReplicaRequest<>(replicaRequestReader, in), this::handleReplicaRequest);
```

AsyncReplicaAction 是 TransportReplicationAction 的内部类，它负责副本写入的处理。

```java
// org/elasticsearch/action/support/replication/TransportReplicationAction.java#AsyncReplicaAction#doRun
@Override
protected void doRun() throws Exception {
    setPhase(task, "replica");
    final String actualAllocationId = this.replica.routingEntry().allocationId().getId();
    if (actualAllocationId.equals(replicaRequest.getTargetAllocationID()) == false) {
        throw new ShardNotFoundException(this.replica.shardId(), "expected allocation id [{}] but found [{}]",
            replicaRequest.getTargetAllocationID(), actualAllocationId);
    }
    // 副本分片写入请求
    acquireReplicaOperationPermit(replica, replicaRequest.getRequest(), this, replicaRequest.getPrimaryTerm(),
        replicaRequest.getGlobalCheckpoint(), replicaRequest.getMaxSeqNoOfUpdatesOrDeletes());
}
```

acquireReplicaOperationPermit 会申请副本分片写入执行操作，并回复主分片。当主分片接收到所有响应，会调用 finish 方法处理响应信息和回复客户端。对于响应异常的副本分片，主分片会通知 Master 节点移除问题分片并创建新的副本分片。

- 远程调用

如果是远程调用，会调用 `TransportService#sendRequest` 的其他重载方法，与涉及到的主分片所在节点通信，发送请求。

根据 `传输模块` 学习的内容可知，`Netty4MessageChannelHandler#channelRead` 接收来自其他 Node 的消息，经过转码，进行请求处理，即调用 messageReceived 方法。

具体处理是调用 `TransportReplicationAction.java#handlePrimaryRequest` 处理主分片写入请求，至此之后和本地执行逻辑相同。