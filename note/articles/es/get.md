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

### 数据节点

无论是本地执行还是远程调用，最终都通过 `handler.messageReceived` 处理请求，也就是开始进行数据节点的任务。

TransportSingleShardAction 用来处理分片上的请求。对于 get 请求，`handler.messageReceived` 对应的是 `ShardTransportHandler#messageReceived`，ShardTransportHandler 是 TransportSingleShardAction 的内部类。

```java
// org/elasticsearch/action/support/single/shard/TransportSingleShardAction.java#ShardTransportHandler#messageReceived
private class ShardTransportHandler implements TransportRequestHandler<Request> {

    @Override
    public void messageReceived(final Request request, final TransportChannel channel, Task task) throws Exception {
        if (logger.isTraceEnabled()) {
            logger.trace("executing [{}] on shard [{}]", request, request.internalShardId);
        }
        asyncShardOperation(request, request.internalShardId, new ChannelActionListener<>(channel, transportShardAction, request));
    }
}
```

asyncShardOperation 会调用 `TransportGetAction#shardOperation`，顾名思义这个方法负责分片操作

- TransportGetAction#shardOperation

```java
// org/elasticsearch/action/get/TransportGetAction.java#shardOperation
@Override
protected GetResponse shardOperation(GetRequest request, ShardId shardId) {
    IndexService indexService = indicesService.indexServiceSafe(shardId.getIndex());
    IndexShard indexShard = indexService.getShard(shardId.id());

    // 检查是否需要 refresh（realtime 为 false,request.refresh() 为 true 才会刷新，realtime 意味是否需要实时数据）
    if (request.refresh() && !request.realtime()) {
        indexShard.refresh("refresh_flag_get");
    }
    
    // 执行 get 操作，获取返回值并封装成 GetResult
    GetResult result = indexShard.getService().get(request.type(), request.id(), request.storedFields(),
            request.realtime(), request.version(), request.versionType(), request.fetchSourceContext());
    return new GetResponse(result);
}
```

这里会校验是否需要 refresh，然后再调用 `ShardGetService#get` 方法，在 `ShardGetService#get` 中会调用  `GetResult getResult = ShardGetService#innerGet` 读取并过滤结果，最终封装成 GetResult 返回。

- ShardGetService#innerGet

```java
// org/elasticsearch/action/get/TransportGetAction.java#innerGet
private GetResult innerGet(String type, String id, String[] gFields, boolean realtime, long version, VersionType versionType,
                           long ifSeqNo, long ifPrimaryTerm, FetchSourceContext fetchSourceContext, boolean readFromTranslog) {
    fetchSourceContext = normalizeFetchSourceContent(fetchSourceContext, gFields);
    // 处理 _all 选项
    if (type == null || type.equals("_all")) {
        DocumentMapper mapper = mapperService.documentMapper();
        type = mapper == null ? null : mapper.type();
    }

    Engine.GetResult get = null;
    if (type != null) {
        Term uidTerm = new Term(IdFieldMapper.NAME, Uid.encodeId(id));
        // 调用 Engine 执行 get 操作读取并返回数据
        get = indexShard.get(new Engine.Get(realtime, readFromTranslog, type, id, uidTerm)
                .version(version).versionType(versionType).setIfSeqNo(ifSeqNo).setIfPrimaryTerm(ifPrimaryTerm));
        if (get.exists() == false) {
            get.close();
        }
    }

    if (get == null || get.exists() == false) {
        return new GetResult(shardId.getIndexName(), type, id, UNASSIGNED_SEQ_NO, UNASSIGNED_PRIMARY_TERM, -1, false, null, null, null);
    }

    try {
        // break between having loaded it from translog (so we only have _source), and having a document to load
        // 过滤返回结果，返回用户需要的字段或属性
        return innerGetLoadFromStoredFields(type, id, gFields, fetchSourceContext, get, mapperService);
    } finally {
        get.close();
    }
}
```

这里首先处理 _all 选项，然后调用 Engine 执行 get 操作读取并返回数据，最终根据用户的需要过滤返回结果并封装成 GetResult 返回。

- InternalEngine#get

```java
// org/elasticsearch/index/engine/InternalEngine#.java#get
@Override
public GetResult get(Get get, BiFunction<String, SearcherScope, Engine.Searcher> searcherFactory) throws EngineException {
    assert Objects.equals(get.uid().field(), IdFieldMapper.NAME) : get.uid().field();
    // 加锁
    try (ReleasableLock ignored = readLock.acquire()) {
        ensureOpen();
        SearcherScope scope;
        // 是否需要实时数据
        if (get.realtime()) {
            VersionValue versionValue = null;
            try (Releasable ignore = versionMap.acquireLock(get.uid().bytes())) {
                // we need to lock here to access the version map to do this truly in RT
                versionValue = getVersionFromMap(get.uid().bytes());
            }
            if (versionValue != null) {
                if (versionValue.isDelete()) {
                    return GetResult.NOT_EXISTS;
                }
                if (get.versionType().isVersionConflictForReads(versionValue.version, get.version())) {
                    throw new VersionConflictEngineException(shardId, get.id(),
                        get.versionType().explainConflictForReads(versionValue.version, get.version()));
                }
                if (get.getIfSeqNo() != SequenceNumbers.UNASSIGNED_SEQ_NO && (
                    get.getIfSeqNo() != versionValue.seqNo || get.getIfPrimaryTerm() != versionValue.term
                    )) {
                    throw new VersionConflictEngineException(shardId, get.id(),
                        get.getIfSeqNo(), get.getIfPrimaryTerm(), versionValue.seqNo, versionValue.term);
                }
                // 是否读取 translog
                // 如果读取 translog，就不需要 refresh 了，提前 return 了。
                // 但从注释看 this is only used for updates，这只作用于 update 操作，在 5.x 版本已经移除，get 操作都是通过 refresh 来达到实时数据
                if (get.isReadFromTranslog()) {
                    // this is only used for updates - API _GET calls will always read form a reader for consistency
                    // the update call doesn't need the consistency since it's source only + _parent but parent can go away in 7.0
                    if (versionValue.getLocation() != null) {
                        try {
                            Translog.Operation operation = translog.readOperation(versionValue.getLocation());
                            if (operation != null) {
                                // in the case of a already pruned translog generation we might get null here - yet very unlikely
                                final Translog.Index index = (Translog.Index) operation;
                                TranslogLeafReader reader = new TranslogLeafReader(index);
                                return new GetResult(new Engine.Searcher("realtime_get", reader,
                                    IndexSearcher.getDefaultSimilarity(), null, IndexSearcher.getDefaultQueryCachingPolicy(), reader),
                                    new VersionsAndSeqNoResolver.DocIdAndVersion(0, index.version(), index.seqNo(), index.primaryTerm(),
                                        reader, 0));
                            }
                        } catch (IOException e) {
                            maybeFailEngine("realtime_get", e); // lets check if the translog has failed with a tragic event
                            throw new EngineException(shardId, "failed to read operation from translog", e);
                        }
                    } else {
                        trackTranslogLocation.set(true);
                    }
                }
                assert versionValue.seqNo >= 0 : versionValue;
                // refresh
                refreshIfNeeded("realtime_get", versionValue.seqNo);
            }
            scope = SearcherScope.INTERNAL;
        } else {
            // we expose what has been externally expose in a point in time snapshot via an explicit refresh
            scope = SearcherScope.EXTERNAL;
        }

        // no version, get the version from the index, we know that we refresh on flush
        // 获取 Searcher 用于读取数据
        return getFromSearcher(get, searcherFactory, scope);
    }
}
```

`InternalEngine#get` 会加锁执行，通过 realtime 判断是否需要实时数据决定是否需要刷盘，最后获取  Searcher 用于读取数据。

这个方法返回的 GetResult 是 Engine 的内部类，它包含 exists、version、docIdAndVersion、searcher 几个属性，提供了文档信息和 Searcher，以便后续 `ShardGetService#innerGetLoadFromStoredFields` 读取文档。

```java
private final boolean exists;
private final long version;
private final DocIdAndVersion docIdAndVersion;
private final Engine.Searcher searcher;
```

- ShardGetService#innerGetLoadFromStoredFields

```java
private GetResult innerGetLoadFromStoredFields(String type, String id, String[] gFields, FetchSourceContext fetchSourceContext,
                                                    Engine.GetResult get, MapperService mapperService) {
    Map<String, DocumentField> documentFields = null;
    Map<String, DocumentField> metaDataFields = null;
    BytesReference source = null;
    DocIdAndVersion docIdAndVersion = get.docIdAndVersion();
    FieldsVisitor fieldVisitor = buildFieldsVisitors(gFields, fetchSourceContext);
    if (fieldVisitor != null) {
        try {
            // 调用 lucene 读取文档，构建 fieldVisitor
            docIdAndVersion.reader.document(docIdAndVersion.docId, fieldVisitor);
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to get type [" + type + "] and id [" + id + "]", e);
        }
        source = fieldVisitor.source();

        if (!fieldVisitor.fields().isEmpty()) {
            fieldVisitor.postProcess(mapperService);
            documentFields = new HashMap<>();
            metaDataFields = new HashMap<>();
            for (Map.Entry<String, List<Object>> entry : fieldVisitor.fields().entrySet()) {
                if (MapperService.isMetadataField(entry.getKey())) {
                    metaDataFields.put(entry.getKey(), new DocumentField(entry.getKey(), entry.getValue()));
                } else {
                    documentFields.put(entry.getKey(), new DocumentField(entry.getKey(), entry.getValue()));
                }
            }
        }
    }

    DocumentMapper docMapper = mapperService.documentMapper();

    if (gFields != null && gFields.length > 0) {
        for (String field : gFields) {
            Mapper fieldMapper = docMapper.mappers().getMapper(field);
            if (fieldMapper == null) {
                if (docMapper.objectMappers().get(field) != null) {
                    // Only fail if we know it is a object field, missing paths / fields shouldn't fail.
                    throw new IllegalArgumentException("field [" + field + "] isn't a leaf field");
                }
            }
        }
    }
    // 过滤内容
    if (!fetchSourceContext.fetchSource()) {
        source = null;
    } else if (fetchSourceContext.includes().length > 0 || fetchSourceContext.excludes().length > 0) {
        Map<String, Object> sourceAsMap;
        XContentType sourceContentType = null;
        // TODO: The source might parsed and available in the sourceLookup but that one uses unordered maps so different. Do we care?
        Tuple<XContentType, Map<String, Object>> typeMapTuple = XContentHelper.convertToMap(source, true);
        sourceContentType = typeMapTuple.v1();
        sourceAsMap = typeMapTuple.v2();
        sourceAsMap = XContentMapValues.filter(sourceAsMap, fetchSourceContext.includes(), fetchSourceContext.excludes());
        try {
            source = BytesReference.bytes(XContentFactory.contentBuilder(sourceContentType).map(sourceAsMap));
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to get type [" + type + "] and id [" + id + "] with includes/excludes set", e);
        }
    }
    // 构建 GetResult
    return new GetResult(shardId.getIndexName(), type, id, get.docIdAndVersion().seqNo, get.docIdAndVersion().primaryTerm,
        get.version(), get.exists(), source, documentFields, metaDataFields);
}
```

这里会读取文档并根据用户需要过滤结果，最终构建 GetResult 返回。其中 `docIdAndVersion.reader.document` 用于读取文档并将内容初始化在 fieldVisitor 集合中，读取文档是通过调用 lucene 实现的。