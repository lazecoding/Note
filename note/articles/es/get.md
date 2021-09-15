# Get 流程

Elasticsearch 文档的读取分为 Get 和 Search，本章讲解 Get 请求执行流程。

请求格式：

```C
GET <index>/_doc/<_id>
```

基本流程：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/Get基本流程.png" width="600px">
</div>

1. 客户端向 `Node 1` 发送 Get 请求。
2. `Node 1` 使用文档的 `_id` 来确定文档属于 `分片 0`，通过集群状态中得知 `分片 0` 的副本分片位于三个节点上。此时它可以将请求发送到任其中任意一个节点上，这里它将请求转发到 `Node 2`。
3. `Node 2` 将文档返回给 `Node 1`，`Node 1` 将文档返回给客户端。

这里，`Node 1` 作为协调节点，会将来自客户端的请求负载均衡地请求集群中地各个副本分片。

详细流程：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/Get详细流程.png" width="600px">
</div>

Get 请求涉及两个节点：协调节点和数据节点。

- 协调节点运行转换并处理转换 API 请求。
- 数据节点包含已建立索引文档的分片，负责处理与数据相关的操作。

### RestGetAction

写入 Document 对应的网络请求处理器是 RestGetAction。

RestGetAction 构造函数：

```java
public RestGetAction(final RestController controller) {
    controller.registerHandler(GET, "/{index}/_doc/{id}", this);
    controller.registerHandler(HEAD, "/{index}/_doc/{id}", this);

    // Deprecated typed endpoints.
    // 弃用的输入，type 7.x 开始弃用
    controller.registerHandler(GET, "/{index}/{type}/{id}", this);
    controller.registerHandler(HEAD, "/{index}/{type}/{id}", this);
}
```

- RestGetAction#prepareRequest

```java
@Override
// org/elasticsearch/rest/action/document/RestGetAction.java#prepareRequest
public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
    GetRequest getRequest;
    // 构造 GetRequest
    if (request.hasParam("type")) {
        // 兼容处理 {type}
        deprecationLogger.deprecatedAndMaybeLog("get_with_types", TYPES_DEPRECATION_MESSAGE);
        getRequest = new GetRequest(request.param("index"), request.param("type"), request.param("id"));
    } else {
        // 处理 _doc
        getRequest = new GetRequest(request.param("index"), request.param("id"));
    }
    // 处理请求参数
    getRequest.refresh(request.paramAsBoolean("refresh", getRequest.refresh()));
    getRequest.routing(request.param("routing"));
    getRequest.preference(request.param("preference"));
    getRequest.realtime(request.paramAsBoolean("realtime", getRequest.realtime()));
    if (request.param("fields") != null) {
        throw new IllegalArgumentException("the parameter [fields] is no longer supported, " +
            "please use [stored_fields] to retrieve stored fields or [_source] to load the field from _source");
    }
    final String fieldsParam = request.param("stored_fields");
    if (fieldsParam != null) {
        final String[] fields = Strings.splitStringByCommaToArray(fieldsParam);
        if (fields != null) {
            getRequest.storedFields(fields);
        }
    }

    getRequest.version(RestActions.parseVersion(request));
    getRequest.versionType(VersionType.fromString(request.param("version_type"), getRequest.versionType()));

    getRequest.fetchSourceContext(FetchSourceContext.parseFromRestRequest(request));
    // 调用 client get 操作 ，由 NodeClient 执行  
    return channel -> client.get(getRequest, new RestToXContentListener<GetResponse>(channel) {
        @Override
        protected RestStatus getStatus(final GetResponse response) {
            return response.isExists() ? OK : NOT_FOUND;
        }
    });
}
```

`RestGetAction#prepareRequest` 做处理 action 前准备工作，request 中封装了客户端的 REST 请求，通过解析 request 得到 getRequest。完成准备工作之后，调用 client.get 执行 get 操作，其中此处的 client 是 NodeClient。

- NodeClient#executeLocally

```java
// org/elasticsearch/client/node/NodeClient.java#executeLocally
public <    Request extends ActionRequest,
            Response extends ActionResponse
        > Task executeLocally(ActionType<Response> action, Request request, TaskListener<Response> listener) {
    return transportAction(action).execute(request, listener);
}
```

NodeClient#executeLocally 执行 transportAction(action).execute(request, listener)。transportAction(action) 获取的 transportAction 是 TransportGetAction，执行的 execute 操作继承自 TransportAction。

### TransportGetAction

TransportAction 是请求处理类，每种 action 都实现了各自的 TransportAction。所有的 TransportAction 都保持着相同的处理模型：当接收到请求时，首先判断本节点能否处理，如果能够处理则调用相关的方法处理得到结果返回，否则内部转发到其它节点进行处理。

TransportGetAction 是 TransportAction 的实现之一，类图如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/TransportGetAction类图.png" width="600px">
</div>

TransportIndexAction 的 execute 操作继承自 TransportAction，接着会调用 doExecute 方法，该方法继承自 TransportSingleShardAction。

```java
// org/elasticsearch/action/support/single/shard/TransportSingleShardAction.java#doExecute
@Override
protected void doExecute(Task task, Request request, ActionListener<Response> listener) {
    new AsyncSingleAction(request, listener).start();
}
```

TransportSingleShardAction 用来处理分片上的请求，AsyncSingleAction 是 TransportSingleShardAction 的内部类。

- AsyncSingleAction 构造函数

```java
// org/elasticsearch/action/support/single/shard/TransportSingleShardAction.java#AsyncSingleAction
private AsyncSingleAction(Request request, ActionListener<Response> listener) {
    this.listener = listener;
    // 获取集群状态
    ClusterState clusterState = clusterService.state();
    if (logger.isTraceEnabled()) {
        logger.trace("executing [{}] based on cluster state version [{}]", request, clusterState.version());
    }
    // 接群 node  列表
    nodes = clusterState.nodes();
    ClusterBlockException blockException = checkGlobalBlock(clusterState);
    if (blockException != null) {
        throw blockException;
    }

    String concreteSingleIndex;
    if (resolveIndex(request)) {
        concreteSingleIndex = indexNameExpressionResolver.concreteSingleIndex(clusterState, request).getName();
    } else {
        concreteSingleIndex = request.index();
    }
    this.internalRequest = new InternalRequest(request, concreteSingleIndex);
    resolveRequest(clusterState, internalRequest);

    blockException = checkRequestBlock(clusterState, internalRequest);
    if (blockException != null) {
        throw blockException;
    }
    // 根据路由算法计算得到目的 shard 迭代器，或者根据优先级选择目标节点
    this.shardIt = shards(clusterState, internalRequest);
}
```

AsyncSingleAction 构造函数准备集群状态、节点列表等属性，根据路径算法计算 出 shardid。

- AsyncSingleAction#start

```java
// org/elasticsearch/action/support/single/shard/TransportSingleShardAction.java#AsyncSingleAction#start
public void start() {
    if (shardIt == null) {
        // just execute it on the local node
        final Writeable.Reader<Response> reader = getResponseReader();
        transportService.sendRequest(clusterService.localNode(), transportShardAction, internalRequest.request(),
            new TransportResponseHandler<Response>() {
            @Override
            public Response read(StreamInput in) throws IOException {
                return reader.read(in);
            }
    
            @Override
            public String executor() {
                return ThreadPool.Names.SAME;
            }
    
            @Override
            public void handleResponse(final Response response) {
                listener.onResponse(response);
            }
    
            @Override
            public void handleException(TransportException exp) {
                listener.onFailure(exp);
            }
        });
    } else {
        perform(null);
    }
}
```

`AsyncSingleAction#start` 通过 `transportService.sendRequest` 发起请求。

`TransportService#sendRequest` 会检查目标节点是否是当前节点，如果是当前节点就进行本地执行，否则进行远程调用。

- 本地执行

```java
// org/elasticsearch/transport/TransportService.java#sendRequest
public void sendRequest(long requestId, String action, TransportRequest request, TransportRequestOptions options)
    throws TransportException {
    // 发送本地请求
    sendLocalRequest(requestId, action, request, options);
}
```

本地执行会调用 sendLocalRequest，sendLocalRequest 不发送消息到网络，直接根据 action 获取注册的 reg，执行 processMessageReceived 处理请求。

- 远程调用

远程调用会将请求发送到数据节点，数据节点进行请求处理。

> 以上是协调节点的任务，下面是数据节点的任务