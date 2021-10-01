# Search 流程

- 目录
    - [分布式检索](#分布式检索)
        - [query](#query)
        - [fetch](#fetch)
        - [dfs](#dfs)
    - [执行流程](#执行流程)
        - [query 阶段](#query-阶段)
        - [fetch 阶段](#fetch-阶段)

Elasticsearch 文档的读取分为 Get 和 Search，本章讲解 Search 请求执行流程。

### 分布式检索

搜索是一种更加复杂的执行模型，因为我们不知道查询会命中哪些文档: 这些文档有可能在集群的任何分片上，它是一个`分布式检索`。

Elasticsearch 目前支持 `query_then_fetch` 和 `dfs_query_then_fetch` 两种搜索方法，不提供对 `query_and_fetch` 和 `dfs_query_and_fetch` 的支持。

```java
public static final SearchType [] CURRENTLY_SUPPORTED = {QUERY_THEN_FETCH, DFS_QUERY_THEN_FETCH};
```

我们以缺省的搜索方式 `query_then_fetch` 来看：一个搜索请求必须询问索引的所有分片的某个副本来确定它们是否含有匹配的文档。找到所有的匹配文档仅仅完成事情的一半。
在 Search 接口返回一个 page 结果之前，多分片中的结果必须组合成单个排序列表。 为此，搜索是一个两阶段过程，我们称之为 `query then fetch`。

#### query

在 query 时， 搜索请求会广播到索引中每一个分片拷贝（主分片或者副本分片），每个分片在本地执行搜索并构建一个匹配文档的 优先队列。

> 优先队列是一个 top-N 的有序列表，它的大小取决于分页参数 from 和 size。如 {"from": 90, "size": 10} 对应的优先队列大小是 100。

query 阶段包含 3 个步骤:

- 客户端发送一个 search 请求到 Node 3 ， Node 3 会创建一个大小为 from + size 的空优先队列。
- Node 3 将查询请求转发到索引的每个主分片或副本分片中。每个分片在本地执行查询并添加结果到大小为 from + size 的本地有序优先队列中。
- 每个分片返回各自优先队列中所有文档的 ID 和排序值给协调节点，也就是 Node 3 ，它合并这些值到自己的优先队列中来产生一个全局排序后的结果列表。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/query阶段流程.png" width="600px">
</div>

当一个搜索请求被发送到某个节点时，这个节点就变成了协调节点。 这个节点的任务是广播查询请求到所有相关分片并将它们的响应整合成全局排序后的结果集合，这个结果集合会返回给客户端。

搜索请求可以被某个主分片或某个副本分片处理，更多的副本能够增加搜索吞吐率，协调节点会轮询所有的分片来分摊负载。

每个分片在本地执行查询请求并且创建一个长度为 from + size 的优先队列，分片返回一个轻量级的结果列表到协调节点，它仅包含文档 ID 集合以及任何排序需要用到的值，例如 _score。

协调节点将这些分片级的结果合并到自己的有序优先队列里，它标识哪些文档满足搜索请求。

#### fetch

query 阶段标识哪些文档满足搜索请求，我们需要取回这些文档,这是 fetch 阶段的任务。

fetch 阶段包含 3 个步骤:

- 协调节点辨别出哪些文档需要被取回并向相关的分片提交多个 GET 请求。
- 每个分片加载并丰富文档，返回文档给协调节点。
- 一旦所有的文档都被取回了，协调节点返回结果给客户端。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/fetch阶段流程.png" width="600px">
</div>

协调节点首先决定哪些文档需要被取回，一旦协调节点接收到所有的结果文档，它就组装这些结果为单个响应返回给客户端。

### dfs

缺省的搜索类型是 `query_then_fetch`，在某些情况下，你可能想明确设置 search_type 为 `dfs_query_then_fetch`  来改善相关性精确度：

```C
GET /_search?search_type=dfs_query_then_fetch
```

搜索类型 `dfs_query_then_fetch` 有预查询阶段，这个阶段可以从所有相关分片获取词频来计算全局词频。

每个文档都有相关性评分，用一个正浮点数字段 _score 来表示: _score 的评分越高，相关性越高。Elasticsearch 的相似度算法被定义为 `TF/IDF`（检索词频率/反向文档频率）。

- TF：检索词在该字段出现的频率，出现频率越高，相关性也越高。
- IDF：每个检索词在索引的所有文档出现的频率，出现频率越高，相关性越低。检索词出现在多数文档中会比出现在少数文档中的权重更低。

但是由于性能原因，Elasticsearch 不会每次都计算索引内所有文档的 IDF，而是每个分片会根据 该分片内的所有文档计算一个本地 IDF。
在数据量很小的时候，可能会因为不同分片的 IDF 不同导致不同的结果，但当数据较大，局部的 IDF 会被均化，基本不会对结果产生影响。

和 `query_then_fetch`  相比，`dfs_query_then_fetch` 有 dfs 阶段。dfs 是指分布式频率搜索（Distributed Frequency Search），
它告诉 Elasticsearch ，先分别获得每个分片本地的 IDF，然后根据结果再计算整个索引的全局 IDF。

但是，不要在生产环境上使用 `dfs_query_then_fetch` 。这个是完全没有必要，只要有足够的数据就能保证词频是均匀分布的，没有理由给每个查询额外加上 dfs。

### 执行流程

以 `query_then_fetch` 为例，search 流程需要经历 query 阶段和 fetch 阶段，整个流程由协调节点和数据阶段交互完成。

https://blog.csdn.net/wudingmei1023/article/details/103978498

经过分析了几种 Rest 请求流程，我们已经可以知道：RestXXXAction 用于预处理 Rest 请求，具体的请求处理由 TransportXXXAction 来完成。通常，协调节点在 TransportXXXAction 中要对请求进行协调转发和设置对应 action 的处理器，数据节点使用设置的处理器完成数据处理。

对于 search 请求来说，这里使用的是 RestSearchAction 和 TransportSearchAction。

#### query 阶段

RestSearchAction 是 search 请求的网络请求处理器。

RestSearchAction 构造函数：

```java
// org/elasticsearch/rest/action/search/RestSearchAction.java#RestSearchAction
public RestSearchAction(RestController controller) {
    controller.registerHandler(GET, "/_search", this);
    controller.registerHandler(POST, "/_search", this);
    controller.registerHandler(GET, "/{index}/_search", this);
    controller.registerHandler(POST, "/{index}/_search", this);

    // Deprecated typed endpoints.
    // 启用 type
    controller.registerHandler(GET, "/{index}/{type}/_search", this);
    controller.registerHandler(POST, "/{index}/{type}/_search", this);
}
```

- RestSearchAction#prepareRequest

```java
// org/elasticsearch/rest/action/search/RestSearchAction.java#prepareRequest
@Override
public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
    SearchRequest searchRequest = new SearchRequest();
    /*
     * We have to pull out the call to `source().size(size)` because
     * _update_by_query and _delete_by_query uses this same parsing
     * path but sets a different variable when it sees the `size`
     * url parameter.
     *
     * Note that we can't use `searchRequest.source()::size` because
     * `searchRequest.source()` is null right now. We don't have to
     * guard against it being null in the IntConsumer because it can't
     * be null later. If that is confusing to you then you are in good
     * company.
     */
    IntConsumer setSize = size -> searchRequest.source().size(size);
    request.withContentOrSourceParamParserOrNull(parser ->
        parseSearchRequest(searchRequest, request, parser, setSize));
    // 对应的 TransportAction 实现类是 TransportSearchAction
    // 调用 TransportSearchAction#doExecute
    return channel -> {
        RestStatusToXContentListener<SearchResponse> listener = new RestStatusToXContentListener<>(channel);
        HttpChannelTaskHandler.INSTANCE.execute(client, request.getHttpChannel(), searchRequest, SearchAction.INSTANCE, listener);
    };
}
```

`RestSearchAction#prepareRequest` 封装出 searchRequest，调用 search 对应的 TransportAction 实现类来处理请求，即 TransportSearchAction，
最终调用 `TransportSearchAction#doExecute`。

- TransportSearchAction#doExecute

```java
// org/elasticsearch/action/search/TransportSearchAction.java#doExecute
@Override
protected void doExecute(Task task, SearchRequest searchRequest, ActionListener<SearchResponse> listener) {
    final long relativeStartNanos = System.nanoTime();
    final SearchTimeProvider timeProvider =
        new SearchTimeProvider(searchRequest.getOrCreateAbsoluteStartMillis(), relativeStartNanos, System::nanoTime);
    ActionListener<SearchSourceBuilder> rewriteListener = ActionListener.wrap(source -> {
        if (source != searchRequest.source()) {
            // only set it if it changed - we don't allow null values to be set but it might be already null. this way we catch
            // situations when source is rewritten to null due to a bug
            searchRequest.source(source);
        }
        final ClusterState clusterState = clusterService.state();
        // 获取远程集群 indices 列表
        final Map<String, OriginalIndices> remoteClusterIndices = remoteClusterService.groupIndices(searchRequest.indicesOptions(),
            searchRequest.indices(), idx -> indexNameExpressionResolver.hasIndexOrAlias(idx, clusterState));
        // 获取本地集群 indices 列表
        OriginalIndices localIndices = remoteClusterIndices.remove(RemoteClusterAware.LOCAL_CLUSTER_GROUP_KEY);
        if (remoteClusterIndices.isEmpty()) {
            // 如果没有远程集群，本地执行 search
            executeLocalSearch(task, timeProvider, searchRequest, localIndices, clusterState, listener);
        } else {
            if (shouldMinimizeRoundtrips(searchRequest)) {
                ccsRemoteReduce(searchRequest, localIndices, remoteClusterIndices, timeProvider, searchService::createReduceContext,
                    remoteClusterService, threadPool, listener,
                    (r, l) -> executeLocalSearch(task, timeProvider, r, localIndices, clusterState, l));
            } else {
                AtomicInteger skippedClusters = new AtomicInteger(0);
                collectSearchShards(searchRequest.indicesOptions(), searchRequest.preference(), searchRequest.routing(),
                    skippedClusters, remoteClusterIndices, remoteClusterService, threadPool,
                    ActionListener.wrap(
                        searchShardsResponses -> {
                            List<SearchShardIterator> remoteShardIterators = new ArrayList<>();
                            Map<String, AliasFilter> remoteAliasFilters = new HashMap<>();
                            BiFunction<String, String, DiscoveryNode> clusterNodeLookup = processRemoteShards(
                                searchShardsResponses, remoteClusterIndices, remoteShardIterators, remoteAliasFilters);
                            int localClusters = localIndices == null ? 0 : 1;
                            int totalClusters = remoteClusterIndices.size() + localClusters;
                            int successfulClusters = searchShardsResponses.size() + localClusters;
                            // 执行 search
                            executeSearch((SearchTask) task, timeProvider, searchRequest, localIndices,
                                remoteShardIterators, clusterNodeLookup, clusterState, remoteAliasFilters, listener,
                                new SearchResponse.Clusters(totalClusters, successfulClusters, skippedClusters.get()));
                        },
                        listener::onFailure));
            }
        }
    }, listener::onFailure);
    if (searchRequest.source() == null) {
        rewriteListener.onResponse(searchRequest.source());
    } else {
        Rewriteable.rewriteAndFetch(searchRequest.source(), searchService.getRewriteContext(timeProvider::getAbsoluteStartMillis),
            rewriteListener);
    }
}
```

这里获取了首先 indices 列表，然后根据有无没有远程 indices 决定是否只进行本地处理。

通常，生产环境我们将协调节点和数据节点分开，协调节点不做数据存储，数据节点也不直接与客户端相连。这意味着，远程 indices 不会为空，我们会执行 executeSearch 方法。 

- TransportSearchAction#executeSearch

```java
// org/elasticsearch/action/search/TransportSearchAction.java#executeSearch
private void executeSearch(SearchTask task, SearchTimeProvider timeProvider, SearchRequest searchRequest,
                           OriginalIndices localIndices, String[] concreteIndices, Map<String, Set<String>> routingMap,
                           Map<String, AliasFilter> aliasFilter, Map<String, Float> concreteIndexBoosts,
                           List<SearchShardIterator> remoteShardIterators, BiFunction<String, String, DiscoveryNode> remoteConnections,
                           ClusterState clusterState, ActionListener<SearchResponse> listener, SearchResponse.Clusters clusters) {

    Map<String, Long> nodeSearchCounts = searchTransportService.getPendingSearchRequests();
    GroupShardsIterator<ShardIterator> localShardsIterator = clusterService.operationRouting().searchShards(clusterState,
            concreteIndices, routingMap, searchRequest.preference(), searchService.getResponseCollectorService(), nodeSearchCounts);
    // 构造出目的分片
    GroupShardsIterator<SearchShardIterator> shardIterators = mergeShardsIterators(localShardsIterator, localIndices,
        searchRequest.getLocalClusterAlias(), remoteShardIterators);

    failIfOverShardCountLimit(clusterService, shardIterators.size());

    // optimize search type for cases where there is only one shard group to search on
    // 优化搜索类型的情况只有一个分片组搜索
    if (shardIterators.size() == 1) {
        // if we only have one group, then we always want Q_T_F, no need for DFS, and no need to do THEN since we hit one shard
        // 只有一个分片的时候，默认就是 QUERY_THEN_FETCH，不存在评分不一致的问题
        searchRequest.searchType(QUERY_THEN_FETCH);
    }
    if (searchRequest.allowPartialSearchResults() == null) {
        // No user preference defined in search request - apply cluster service default
        // 用户未定义首选项，采用默认方式
        searchRequest.allowPartialSearchResults(searchService.defaultAllowPartialSearchResults());
    }
    // 如果只用做 suggest，不需要全局排序
    if (searchRequest.isSuggestOnly()) {
        // disable request cache if we have only suggest
        // 默认是没有开启请求缓存的
        searchRequest.requestCache(false);
        if (searchRequest.searchType() == DFS_QUERY_THEN_FETCH) {
            // convert to Q_T_F if we have only suggest
            // 这种情况下 DFS_QUERY_THEN_FETCH 会转化成 QUERY_THEN_FETCH
            searchRequest.searchType(QUERY_THEN_FETCH);
        }
    }
    // 获取 nodes
    final DiscoveryNodes nodes = clusterState.nodes();
    BiFunction<String, String, Transport.Connection> connectionLookup = buildConnectionLookup(searchRequest.getLocalClusterAlias(),
        nodes::get, remoteConnections, searchTransportService::getConnection);
    boolean preFilterSearchShards = shouldPreFilterSearchShards(searchRequest, shardIterators);
    // 生成查询请求的调度类 searchAsyncAction 并启动调度执行
    // 继承自 AbstractSearchAsyncAction，调用 AbstractSearchAsyncAction#start
    searchAsyncAction(task, searchRequest, shardIterators, timeProvider, connectionLookup, clusterState.version(),
        Collections.unmodifiableMap(aliasFilter), concreteIndexBoosts, routingMap, listener, preFilterSearchShards, clusters).start();
}
```

这里首先会构造目的分片 shardIterators，如果 shardIterators 中只有一个分片，则不管 search type 如何，都采用 `query_then_fetch`，因为单个分片不存在评分不一致的问题。

最后会通过 searchAsyncAction 方法获取 AbstractSearchAsyncAction 对象并执行 start 方法。AbstractSearchAsyncAction 由 search type 影响，它决定了下面的业务流程。

- AbstractSearchAsyncAction#start

```java
// org/elasticsearch/action/search/AbstractSearchAsyncAction.java#start
public final void start() {
    if (getNumShards() == 0) {
        //no search shards to search on, bail with empty response
        //(it happens with search across _all with no indices around and consistent with broadcast operations)
        int trackTotalHitsUpTo = request.source() == null ? SearchContext.DEFAULT_TRACK_TOTAL_HITS_UP_TO :
            request.source().trackTotalHitsUpTo() == null ? SearchContext.DEFAULT_TRACK_TOTAL_HITS_UP_TO :
                request.source().trackTotalHitsUpTo();
        // total hits is null in the response if the tracking of total hits is disabled
        boolean withTotalHits = trackTotalHitsUpTo != SearchContext.TRACK_TOTAL_HITS_DISABLED;
        listener.onResponse(new SearchResponse(InternalSearchResponse.empty(withTotalHits), null, 0, 0, 0, buildTookInMillis(),
            ShardSearchFailure.EMPTY_ARRAY, clusters));
        return;
    }
    // 这里传入的是 this，里面调用的 run 就是 this.run
    executePhase(this);
}
```

executePhase 方法的参数是 SearchPhase，里面调用 `SearchPhase#run`。这里的 this 代表传入的是 AbstractSearchAsyncAction，后面会执行 `AbstractSearchAsyncAction#run`

- AbstractSearchAsyncAction#run

```java
// org/elasticsearch/action/search/AbstractSearchAsyncAction.java#run
@Override
public final void run() {
    for (final SearchShardIterator iterator : toSkipShardsIts) {
        assert iterator.skip();
        skipShard(iterator);
    }
    if (shardsIts.size() > 0) {
        assert request.allowPartialSearchResults() != null : "SearchRequest missing setting for allowPartialSearchResults";
        if (request.allowPartialSearchResults() == false) {
            final StringBuilder missingShards = new StringBuilder();
            // Fail-fast verification of all shards being available
            for (int index = 0; index < shardsIts.size(); index++) {
                final SearchShardIterator shardRoutings = shardsIts.get(index);
                if (shardRoutings.size() == 0) {
                    if(missingShards.length() > 0){
                        missingShards.append(", ");
                    }
                    missingShards.append(shardRoutings.shardId());
                }
            }
            if (missingShards.length() > 0) {
                //Status red - shard is missing all copies and would produce partial results for an index search
                final String msg = "Search rejected due to missing shards ["+ missingShards +
                    "]. Consider using `allow_partial_search_results` setting to bypass this error.";
                throw new SearchPhaseExecutionException(getName(), msg, null, ShardSearchFailure.EMPTY_ARRAY);
            }
        }
        // 遍历分片迭代器，以分片为单位执行 performPhaseOnShard，这意味着即使是相同节点的两个分片的请求也不会合并
        for (int index = 0; index < shardsIts.size(); index++) {
            final SearchShardIterator shardRoutings = shardsIts.get(index);
            assert shardRoutings.skip() == false;
            // 执行以分片为单位的搜索阶段
            performPhaseOnShard(index, shardRoutings, shardRoutings.nextOrNull());
        }
    }
}
```

这里会遍历目标分片迭代器，以分片为单位执行 performPhaseOnShard 方法，这意味着即使是相同节点的两个分片的请求也不会合并。

- AbstractSearchAsyncAction#performPhaseOnShard

```java
// org/elasticsearch/action/search/AbstractSearchAsyncAction.java#performPhaseOnShard
private void performPhaseOnShard(final int shardIndex, final SearchShardIterator shardIt, final ShardRouting shard) {
    /*
     * We capture the thread that this phase is starting on. When we are called back after executing the phase, we are either on the
     * same thread (because we never went async, or the same thread was selected from the thread pool) or a different thread. If we
     * continue on the same thread in the case that we never went async and this happens repeatedly we will end up recursing deeply and
     * could stack overflow. To prevent this, we fork if we are called back on the same thread that execution started on and otherwise
     * we can continue (cf. InitialSearchPhase#maybeFork).
     */
    if (shard == null) {
        fork(() -> onShardFailure(shardIndex, null, null, shardIt, new NoShardAvailableActionException(shardIt.shardId())));
    } else {
        final PendingExecutions pendingExecutions = throttleConcurrentRequests ?
            pendingExecutionsPerNode.computeIfAbsent(shard.currentNodeId(), n -> new PendingExecutions(maxConcurrentRequestsPerNode))
            : null;
        Runnable r = () -> {
            final Thread thread = Thread.currentThread();
            try {
                // 重点！！！
                // 执行请求，并设置 listener 处理响应
                //  1. 协调节点发送 query 请求
                //  2. 数据节点通过对应的处理器处理接收到的 query 请求；对应的处理器方法为 SearchService#executeQueryPhase
                //  3. listener 进行响应处理
                //      4. onShardResult 合并请求，并结束当前阶段，进入下一个阶段，即 fetch
                executePhaseOnShard(shardIt, shard,
                    new SearchActionListener<Result>(shardIt.newSearchShardTarget(shard.currentNodeId()), shardIndex) {
                        @Override
                        public void innerOnResponse(Result result) {
                            try {
                                // 合并请求，并结束当前阶段
                                // successfulShardExecution > onPhaseDone > executeNextPhase ： 调用 FetchSearchPhase#run
                                onShardResult(result, shardIt);
                            } finally {
                                executeNext(pendingExecutions, thread);
                            }
                        }

                        @Override
                        public void onFailure(Exception t) {
                            try {
                                onShardFailure(shardIndex, shard, shard.currentNodeId(), shardIt, t);
                            } finally {
                                executeNext(pendingExecutions, thread);
                            }
                        }
                    });
            } catch (final Exception e) {
                try {
                    /*
                     * It is possible to run into connection exceptions here because we are getting the connection early and might
                     * run into nodes that are not connected. In this case, on shard failure will move us to the next shard copy.
                     */
                    fork(() -> onShardFailure(shardIndex, shard, shard.currentNodeId(), shardIt, e));
                } finally {
                    executeNext(pendingExecutions, thread);
                }
            }
        };
        if (throttleConcurrentRequests) {
            pendingExecutions.tryRun(r);
        } else {
            r.run();
        }
    }
}
```

这里是 query 阶段的重点。executePhaseOnShard 方法负责执行 query 请求，并设置响应处理器，即 onShardResult 负责处理响应结果。

executePhaseOnShard 会调用 sendExecuteQuery 进行请求转发。

```java
// org/elasticsearch/action/search/SearchTransportService.java#sendExecuteQuery
public void sendExecuteQuery(Transport.Connection connection, final ShardSearchRequest request, SearchTask task,
                             final SearchActionListener<SearchPhaseResult> listener) {
    // we optimize this and expect a QueryFetchSearchResult if we only have a single shard in the search request
    // this used to be the QUERY_AND_FETCH which doesn't exist anymore.
    final boolean fetchDocuments = request.numberOfShards() == 1;
    Writeable.Reader<SearchPhaseResult> reader = fetchDocuments ? QueryFetchSearchResult::new : QuerySearchResult::new;

    final ActionListener handler = responseWrapper.apply(connection, listener);
    // 发送 QUERY_ACTION_NAME ，对应的处理器 是  SearchService#executeQueryPhase
    transportService.sendChildRequest(connection, QUERY_ACTION_NAME, request, task,
            new ConnectionCountingHandler<>(handler, reader, clientConnections, connection.getNode().getId()));
}
```

这里转发的 action 是 QUERY_ACTION_NAME，在 `SearchTransportService#registerRequestHandler` 方法中注册了 QUERY_ACTION_NAME 的处理器，
它对应的处理方法是 `SearchService#executeQueryPhase`。

- SearchService#executeQueryPhase

```java
// org/elasticsearch/search/SearchService.java#executeQueryPhase
private SearchPhaseResult executeQueryPhase(ShardSearchRequest request, SearchTask task) throws Exception {
    final SearchContext context = createAndPutContext(request);
    context.incRef();
    try {
        context.setTask(task);
        final long afterQueryTime;
        try (SearchOperationListenerExecutor executor = new SearchOperationListenerExecutor(context)) {
            contextProcessing(context);
            // 尝试从缓存加载查询结果.如果无法使用缓存则直接执行查询阶段 ———— 得到 DocId 信息，放入 context 中
            loadOrExecuteQueryPhase(request, context);
            if (context.queryResult().hasSearchContext() == false && context.scrollContext() == null) {
                freeContext(context.id());
            } else {
                contextProcessedSuccessfully(context);
            }
            afterQueryTime = executor.success();
        }
        if (request.numberOfShards() == 1) {
            return executeFetchPhase(context, afterQueryTime);
        }
        // 返回 query 结果
        return context.queryResult();
    } catch (Exception e) {
        // execution exception can happen while loading the cache, strip it
        if (e instanceof ExecutionException) {
            e = (e.getCause() == null || e.getCause() instanceof Exception) ?
                (Exception) e.getCause() : new ElasticsearchException(e.getCause());
        }
        logger.trace("Query phase failed", e);
        processFailure(context, e);
        throw e;
    } finally {
        cleanContext(context);
    }
}
```

这里进入了数据节点，loadOrExecuteQueryPhase 方法尝试从缓存加载查询结果，如果无法使用缓存则直接执行查询阶段。

以查询为例，调用 `QueryPhase#execute`。

- QueryPhase#execute

```java
// org/elasticsearch/search/query/QueryPhase.java#execute
@Override
public void execute(SearchContext searchContext) throws QueryPhaseExecutionException {
    if (searchContext.hasOnlySuggest()) {
        // 如果至少用作 suggest，直接执行 query，返回一个 TOP N 列表
        suggestPhase.execute(searchContext);
        searchContext.queryResult().topDocs(new TopDocsAndMaxScore(
                new TopDocs(new TotalHits(0, TotalHits.Relation.EQUAL_TO), Lucene.EMPTY_SCORE_DOCS), Float.NaN),
                new DocValueFormat[0]);
        return;
    }

    if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("{}", new SearchContextSourcePrinter(searchContext));
    }

    // Pre-process aggregations as late as possible. In the case of a DFS_Q_T_F
    // request, preProcess is called on the DFS phase phase, this is why we pre-process them
    // here to make sure it happens during the QUERY phase
    aggregationPhase.preProcess(searchContext);
    final ContextIndexSearcher searcher = searchContext.searcher();
    // 执行真正的 query
    boolean rescore = execute(searchContext, searchContext.searcher(), searcher::setCheckCancelled);

    if (rescore) { // only if we do a regular search
        rescorePhase.execute(searchContext);
    }
    suggestPhase.execute(searchContext);
    aggregationPhase.execute(searchContext);

    if (searchContext.getProfilers() != null) {
        ProfileShardResult shardResults = SearchProfileShardResults
                .buildShardResults(searchContext.getProfilers());
        searchContext.queryResult().profileResults(shardResults);
    }
}
```

这里会继续深入 `execute(searchContext, searchContext.searcher(), searcher::setCheckCancelled);`，
由 `searcher.search(query, queryCollector);` 调用 lucene 接口，执行真正的查询，最后得到 Doc Id 列表返回给协调节点。

当协调节点收到响应，由 `AbstractSearchAsyncAction#onShardResult` 进行响应处理。

- AbstractSearchAsyncAction#onShardResult

```java
// org/elasticsearch/action/search/AbstractSearchAsyncAction.java#onShardResult
private void onShardResult(Result result, SearchShardIterator shardIt) {
    assert result.getShardIndex() != -1 : "shard index is not set";
    assert result.getSearchShardTarget() != null : "search shard target must not be null";
    successfulOps.incrementAndGet();
    // 合并请求结果
    results.consumeResult(result);
    if (logger.isTraceEnabled()) {
        logger.trace("got first-phase result from {}", result != null ? result.getSearchShardTarget() : null);
    }
    // clean a previous error on this shard group (note, this code will be serialized on the same shardIndex value level
    // so its ok concurrency wise to miss potentially the shard failures being created because of another failure
    // in the #addShardFailure, because by definition, it will happen on *another* shardIndex
    AtomicArray<ShardSearchFailure> shardFailures = this.shardFailures.get();
    if (shardFailures != null) {
        shardFailures.set(result.getShardIndex(), null);
    }
    // we need to increment successful ops first before we compare the exit condition otherwise if we
    // are fast we could concurrently update totalOps but then preempt one of the threads which can
    // cause the successor to read a wrong value from successfulOps if second phase is very fast ie. count etc.
    // increment all the "future" shards to update the total ops since we some may work and some may not...
    // and when that happens, we break on total ops, so we must maintain them
    // 检查是否所有请求都已收到回复，是否进入下一阶段
    // 调用 onPhaseDone(); 完成该阶段
    successfulShardExecution(shardIt);
}
```

每个分片处理完 query 请求，都会进入 `AbstractSearchAsyncAction#onShardResult`。它会将请求结果合并，并检查是否收到了所有请求的响应回复，决定是否进入下一个阶段，

successfulShardExecution 确认接收到所有请求的响应后，会调用 `onPhaseDone();`，这个方法很简单，就是进入下一个阶段，即 fetch 阶段。

```java
final void onPhaseDone() {  // as a tribute to @kimchy aka. finishHim()
    executeNextPhase(this, getNextPhase(results, this));
}
```

> 至此，query 阶段完毕。

#### fetch 阶段
