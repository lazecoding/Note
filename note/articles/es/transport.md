# Transport 模块

- 目录
    - [Transport](#Transport)
        - [传输模块](#传输模块)
        - [HTTP 模块](#HTTP-模块)
    - [如何接收用户请求](#如何接收用户请求)

Transport 模块 是 ElasticSearch 的通信模型，分为传输模块和 HTTP 模块，它们都是基于 Netty 实现的，Netty 是一个 Java 实现的高性能异步网络通信库。

### Transport

ElasticSearch 将传输模块和 HTTP 模块封装到 NetworkModule 类中，该类有三个重要的成员：

- Transport： 负责内部节点的通信
- HttpServerTransport: 负责用户的 REST 请求
- TransportInterceptor ：传输层拦截器，拦截发送方和接收方的请求。

在 ElasticSearch  启动时候，构造 Node 对象阶段会初始化 NetworkModule ，并实例化和注册相关的组件。

```java
// org/elasticsearch/node/Node.java#Node
// ...
// 获取 RestController,用于处理各种 Elasticsearch 的 RESTful 命令,如 _cat,_all,_cat/health,_clusters 等(Elasticsearch 称之为 action)
// 并用于初始化 NetworkModule，注册 HttpServerTransport
final RestController restController = actionModule.getRestController();
// 初始化 NetworkModule 的传输模块和 HTTP 模块,加载 Transport、HttpServerTransport 和 TransportInterceptor
final NetworkModule networkModule = new NetworkModule(settings, false, pluginsService.filterPlugins(NetworkPlugin.class),
        threadPool, bigArrays, pageCacheRecycler, circuitBreakerService, namedWriteableRegistry, xContentRegistry,
        networkService, restController);
// ...
// 获取  transport，用于初始化 transportService
final Transport transport = networkModule.getTransportSupplier().get();
// ...
// 初始化 transportService，用于处理节点间通信
final TransportService transportService = newTransportService(settings, transport, threadPool,
        networkModule.getTransportInterceptor(), localNodeFactory, settingsModule.getClusterSettings(), taskHeaders);
// ...
// 获取  httpServerTransport
final HttpServerTransport httpServerTransport = newHttpTransport(networkModule);
// ...
// 注册 RestHandlers，处理客户端请求
        actionModule.initRestHandlers(() -> clusterService.state().nodes());
```

NetworkModule 的构造函数主要处理了三件事情：注册 HTTP 模块、注册传输模块、注册拦截器。其中注册 HTTP 模块时，注入了 RestController，因为 HTTP 模块用于处理用户的 REST 请求。

```java
// org/elasticsearch/common/network/NetworkModule.java#NetworkModule
public NetworkModule(Settings settings, boolean transportClient, List<NetworkPlugin> plugins, ThreadPool threadPool,
                     BigArrays bigArrays,
                     PageCacheRecycler pageCacheRecycler,
                     CircuitBreakerService circuitBreakerService,
                     NamedWriteableRegistry namedWriteableRegistry,
                     NamedXContentRegistry xContentRegistry,
                     NetworkService networkService, HttpServerTransport.Dispatcher dispatcher) {
    this.settings = settings;
    this.transportClient = transportClient;
    for (NetworkPlugin plugin : plugins) {
        Map<String, Supplier<HttpServerTransport>> httpTransportFactory = plugin.getHttpTransports(settings, threadPool, bigArrays,
            pageCacheRecycler, circuitBreakerService, xContentRegistry, networkService, dispatcher);
        if (transportClient == false) {
            for (Map.Entry<String, Supplier<HttpServerTransport>> entry : httpTransportFactory.entrySet()) {
                // 注册 HTTP 模块
                registerHttpTransport(entry.getKey(), entry.getValue());
            }
        }
        Map<String, Supplier<Transport>> transportFactory = plugin.getTransports(settings, threadPool, pageCacheRecycler,
            circuitBreakerService, namedWriteableRegistry, networkService);
        for (Map.Entry<String, Supplier<Transport>> entry : transportFactory.entrySet()) {
            // 注册传输模块
            registerTransport(entry.getKey(), entry.getValue());
        }
        List<TransportInterceptor> transportInterceptors = plugin.getTransportInterceptors(namedWriteableRegistry,
            threadPool.getThreadContext());
        for (TransportInterceptor interceptor : transportInterceptors) {
            // 注册拦截器
            registerTransportInterceptor(interceptor);
        }
    }
}
```

NetworkModule 内部组件的初始化是通过插件方式加载的。在其构造函数中传入 NetworkPlugin 列表，NetworkPlugin 是一个接口类， Netty4Plugin 是这个接口的实现，如下图所示：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/Netty4Plugin类图.png" width="400px">
</div>

Netty4Plugin 提供了 getTransports 和 getHttpTransports 方法分别用来获取 Netty4Transport 和 Netty4HttpServerTransport。

```java
/**
 * 构建 Netty4Transport，用于 Transport 传输模块
 */
@Override
// org/elasticsearch/transport/Netty4Plugin.java#getTransports
public Map<String, Supplier<Transport>> getTransports(Settings settings, ThreadPool threadPool, PageCacheRecycler pageCacheRecycler,
                                                      CircuitBreakerService circuitBreakerService,
                                                      NamedWriteableRegistry namedWriteableRegistry, NetworkService networkService) {
    return Collections.singletonMap(NETTY_TRANSPORT_NAME, () -> new Netty4Transport(settings, Version.CURRENT, threadPool,
        networkService, pageCacheRecycler, namedWriteableRegistry, circuitBreakerService));
}

/**
 * 构建 Netty4HttpServerTransport，用于 HttpServerTransport HTTP 模块
 */
@Override
// org/elasticsearch/transport/Netty4Plugin.java#getHttpTransports
public Map<String, Supplier<HttpServerTransport>> getHttpTransports(Settings settings, ThreadPool threadPool, BigArrays bigArrays,
                                                                    PageCacheRecycler pageCacheRecycler,
                                                                    CircuitBreakerService circuitBreakerService,
                                                                    NamedXContentRegistry xContentRegistry,
                                                                    NetworkService networkService,
                                                                    HttpServerTransport.Dispatcher dispatcher) {
    return Collections.singletonMap(NETTY_HTTP_TRANSPORT_NAME,
        () -> new Netty4HttpServerTransport(settings, networkService, bigArrays, threadPool, xContentRegistry, dispatcher));
}
```

- Netty4Transport 负责在 ElasticSearch 启动时初始化 client 和 server。

```java
// org/elasticsearch/transport/netty4/Netty4Transport.java#doStart
protected void doStart() {
    boolean success = false;
    try {
        ThreadFactory threadFactory = daemonThreadFactory(settings, TRANSPORT_WORKER_THREAD_NAME_PREFIX);
        eventLoopGroup = new NioEventLoopGroup(workerCount, threadFactory);
        // 初始化 client
        clientBootstrap = createClientBootstrap(eventLoopGroup);
        if (NetworkService.NETWORK_SERVER.get(settings)) {
            for (ProfileSettings profileSettings : profileSettings) {
                // 初始化 server
                createServerBootstrap(profileSettings, eventLoopGroup);
                bindServer(profileSettings);
            }
        }
        super.doStart();
        success = true;
    } finally {
        if (success == false) {
            doStop();
        }
    }
}
```

- Netty4HttpServerTransport 负责在 ElasticSearch 启动时创建一个 HTTP Server 监听端口，当收到用户请求时，调用 dispatchRequest 对不同的请求执行相应的处理。

#### 传输模块

TransportService 是传输模块服务类，它主要提供两组方法：connectToNode 和 sendRequest。

- connectToNode 用于节点间建立连接。
- sendRequest 用于节点间通信。在发送请求的时候，最后一个参数定义了如何处理 Response，即 TransportResponseHandler<T> handler。

#### HTTP 模块

HTTP 模块用于处理来自用户的 REST 请求，在 ElasticSearch 中，请求被称为 action。ActionModule 中注册了每种 action 对应的处理器。

```java
// org/elasticsearch/node/Node.java#Node
// 注册 RestHandlers，处理客户端请求
actionModule.initRestHandlers(() -> clusterService.state().nodes());

// org/elasticsearch/action/ActionModule.java#initRestHandlers
public void initRestHandlers(Supplier<DiscoveryNodes> nodesInCluster) {
    List<AbstractCatAction> catActions = new ArrayList<>();
    Consumer<RestHandler> registerHandler = a -> {
        if (a instanceof AbstractCatAction) {
            catActions.add((AbstractCatAction) a);
        }
    };
    // 注册 REST handler, 它们都继承了 BaseRestHandler，构造函数中都注入了 restController，用于注册 handler。
    registerHandler.accept(new RestAddVotingConfigExclusionAction(restController));
    registerHandler.accept(new RestClearVotingConfigExclusionsAction(restController));
    registerHandler.accept(new RestMainAction(restController));
    registerHandler.accept(new RestNodesInfoAction(restController, settingsFilter));
    registerHandler.accept(new RestRemoteClusterInfoAction(restController));
    registerHandler.accept(new RestNodesStatsAction(restController));
    // ... 
}
```

每种 RestAction handler 都继承自 BaseRestHandler，它们的构造函数都注入了 RestController，用于将路由和 handler 绑定，注册到 PathTrie，路由字典。

在处理请求阶段，会根据 PathTrie 中匹配的 handler 来处理用户的 REST 请求。

```java
// 示例
controller.registerHandler(RestRequest.Method.POST, "/_cluster/voting_config_exclusions/{node_name}", this);

// org/elasticsearch/rest/RestController.java#registerHandler
public void registerHandler(RestRequest.Method method, String path, RestHandler handler) {
    if (handler instanceof BaseRestHandler) {
        usageService.addRestHandler((BaseRestHandler) handler);
    }
    final RestHandler maybeWrappedHandler = handlerWrapper.apply(handler);
    // handlers 就是 PathTrie，路由字典。将路由和对应的 handler 映射。（为了之前处理 REST 请求时，根据 REST 请求结构从 PathTrie 中获取对应的 handler）
    handlers.insertOrUpdate(path, new MethodHandlers(path, maybeWrappedHandler, method),
        (mHandlers, newMHandler) -> mHandlers.addMethods(maybeWrappedHandler, method));
}
```

同时，每个 REST 请求处理类需要实现一个 prepareRequest 函数，用于在收到请求时，对请求执行验证工作等，当一个请求到来时，网络层调用 BaseRestHandler 对应的 action 子类的 handleRequest 来处理。

### 如何接收用户请求

具体点说，ElasticSearch 是如何接收请求的。

Netty4HttpRequestHandler 是 ElasticSearch 的网络请求处理器。Netty4HttpRequestHandler 继承了 SimpleChannelInboundHandler，SimpleChannelInboundHandler 是 Netty 提供的用于处理网络消息的抽象类。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/Netty4HttpRequestHandler类图.png" width="600px">
</div>

Netty4HttpRequestHandler#channelRead0 会处理接收到的网络消息，它会调用 Netty4HttpServerTransport#incomingRequest，顾名思义这是个处理传入的 HTTP 请求的方法。

```java
// org/elasticsearch/http/netty4/Netty4HttpRequestHandler.java#channelRead0
protected void channelRead0(ChannelHandlerContext ctx, HttpPipelinedRequest<FullHttpRequest> msg) {
    Netty4HttpChannel channel = ctx.channel().attr(Netty4HttpServerTransport.HTTP_CHANNEL_KEY).get();
    FullHttpRequest request = msg.getRequest();
    final FullHttpRequest copiedRequest;
    try {
        copiedRequest =
            new DefaultFullHttpRequest(
                request.protocolVersion(),
                request.method(),
                request.uri(),
                Unpooled.copiedBuffer(request.content()),
                request.headers(),
                request.trailingHeaders());
    } finally {
        // As we have copied the buffer, we can release the request
        request.release();
    }
    Netty4HttpRequest httpRequest = new Netty4HttpRequest(copiedRequest, msg.getSequence());

    if (request.decoderResult().isFailure()) {
        Throwable cause = request.decoderResult().cause();
        if (cause instanceof Error) {
            ExceptionsHelper.maybeDieOnAnotherThread(cause);
            serverTransport.incomingRequestError(httpRequest, channel, new Exception(cause));
        } else {
            serverTransport.incomingRequestError(httpRequest, channel, (Exception) cause);
        }
    } else {
        serverTransport.incomingRequest(httpRequest, channel);
    }
}
```

REST 请求会走到 RestController#dispatchRequest 方法。tryAllHandlers 取出在 PathTrie 中所有和路由匹配的 handler 并尝试处理。之后就是具体的 handler 通过 prepareRequest 做请求处理的准备。

```java
// org/elasticsearch/rest/RestController.java#dispatchRequest
@Override
public void dispatchRequest(RestRequest request, RestChannel channel, ThreadContext threadContext) {
    if (request.rawPath().equals("/favicon.ico")) {
        handleFavicon(request.method(), request.uri(), channel);
        return;
    }
    try {
        tryAllHandlers(request, channel, threadContext);
    } catch (Exception e) {
        try {
            channel.sendResponse(new BytesRestResponse(channel, e));
        } catch (Exception inner) {
            inner.addSuppressed(e);
            logger.error(() ->
                new ParameterizedMessage("failed to send failure response for uri [{}]", request.uri()), inner);
        }
    }
}

// org/elasticsearch/rest/RestController.java#dispatchRequest
handler.handleRequest(request, responseChannel, client);
```

以 RestCreateIndexAction 为例，prepareRequest 做处理 action 前准备工作，最终调用 `client.admin().indices().create` 方法执行相关业务。

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