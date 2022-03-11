# 框架设计

- 目录
    - [架构体系](#架构体系)
    - [整体设计](#整体设计)
    - [模块分包](#模块分包)

Dubbo 是一个高性能的、基于 Java 的开源 RPC 框架。

### 架构体系

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/dubbo/Dubbo架构体系.png" width="600px">
</div>

角色说明：

- Provider: 暴露服务的服务提供方。
- Consumer: 调用远程服务的服务消费方。
- Registry: 服务注册与发现的注册中心。
- Monitor: 统计服务的调用次调和调用时间的监控中心。
- Container: 服务运行容器。

调用关系说明：

- 服务容器负责启动，加载，运行服务提供者。
- 服务提供者在启动时，向注册中心注册自己提供的服务。
- 服务消费者在启动时，向注册中心订阅自己所需的服务。
- 注册中心返回服务提供者地址列表给消费者，如果有变更，注册中心将基于长连接推送变更数据给消费者。
- 服务消费者，从提供者地址列表中，基于软负载均衡算法，选一台提供者进行调用，如果调用失败，再选另一台调用。
- 服务消费者和提供者，在内存中累计调用次数和调用时间，定时每分钟发送一次统计数据到监控中心。

特性：

- 基于 RPC 的透明接口
- 智能负载均衡
- 自动服务注册和发现
- 高可扩展性
- 运行时流量路由
- 可视化服务治理

### 整体设计

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/dubbo/Dubbo整体设计示意图.png" width="600px">
</div>

图例说明：

- 图中左边淡蓝背景的为服务消费方使用的接口，右边淡绿色背景的为服务提供方使用的接口，位于中轴线上的为双方都用到的接口。
- 图中从下至上分为十层，各层均为单向依赖，右边的黑色箭头代表层之间的依赖关系，每一层都可以剥离上层被复用，其中，Service 和 Config 层为 API，其它各层均为 SPI。
- 图中绿色小块的为扩展接口，蓝色小块为实现类，图中只显示用于关联各层的实现类。
- 图中蓝色虚线为初始化过程，即启动时组装链，红色实线为方法调用过程，即运行时调时链，紫色三角箭头为继承，可以把子类看作父类的同一个节点，线上的文字为调用的方法。

各层说明：

- `Config 配置层`：对外配置接口，以 ServiceConfig, ReferenceConfig 为中心，可以直接初始化配置类，也可以通过 spring 解析配置生成配置类。
- `Proxy 服务代理层`：服务接口透明代理，生成服务的客户端 Stub 和服务器端 Skeleton, 以 ServiceProxy 为中心，扩展接口为 ProxyFactory。
- `Registry 注册中心层`：封装服务地址的注册与发现，以服务 URL 为中心，扩展接口为 RegistryFactory, Registry, RegistryService。
- `Cluster 路由层`：封装多个提供者的路由及负载均衡，并桥接注册中心，以 Invoker 为中心，扩展接口为 Cluster, Directory, Router, LoadBalance。
- `Monitor 监控层`：RPC 调用次数和调用时间监控，以 Statistics 为中心，扩展接口为 MonitorFactory, Monitor, MonitorService。
- `Protocol 远程调用层`：封装 RPC 调用，以 Invocation, Result 为中心，扩展接口为 Protocol, Invoker, Exporter。
- `Exchange 信息交换层`：封装请求响应模式，同步转异步，以 Request, Response 为中心，扩展接口为 Exchanger, ExchangeChannel, ExchangeClient, ExchangeServer。
- `Transport 网络传输层`：抽象 mina 和 netty 为统一接口，以 Message 为中心，扩展接口为 Channel, Transporter, Client, Server, Codec
- `Serialize 数据序列化层`：可复用的一些工具，扩展接口为 Serialization, ObjectInput, ObjectOutput, ThreadPool。

关系说明：

- 在 RPC 中，Protocol 是核心层，也就是只要有 Protocol + Invoker + Exporter 就可以完成非透明的 RPC 调用，然后在 Invoker 的主过程上 Filter 拦截点。
- 图中的 Consumer 和 Provider 是抽象概念，只是想让看图者更直观的了解哪些类分属于客户端与服务器端，不用 Client 和 Server 的原因是 Dubbo 在很多场景下都使用 Provider, Consumer, Registry, Monitor 划分逻辑拓普节点，保持统一概念。
- 而 Cluster 是外围概念，所以 Cluster 的目的是将多个 Invoker 伪装成一个 Invoker，这样其它人只要关注 Protocol 层 Invoker 即可，加上 Cluster 或者去掉 Cluster 对其它层都不会造成影响，因为只有一个提供者时，是不需要 Cluster 的。
- Proxy 层封装了所有接口的透明化代理，而在其它层都以 Invoker 为中心，只有到了暴露给用户使用时，才用 Proxy 将 Invoker 转成接口，或将接口实现转成 Invoker，也就是去掉 Proxy 层 RPC 是可以 Run 的，只是不那么透明，不那么看起来像调本地服务一样调远程服务。
- 而 Remoting 实现是 Dubbo 协议的实现，如果你选择 RMI 协议，整个 Remoting 都不会用上，Remoting 内部再划为 Transport 传输层和 Exchange 信息交换层，Transport 层只负责单向消息传输，是对 Mina, Netty, Grizzly 的抽象，它也可以扩展 UDP 传输，而 Exchange 层是在传输层之上封装了 Request-Response 语义。
- Registry 和 Monitor 实际上不算一层，而是一个独立的节点，只是为了全局概览，用层的方式画在一起。

### 模块分包

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/dubbo/Dubbo模块分包示意图.png" width="600px">
</div>

模块说明：

- `dubbo-common 公共逻辑模块`：包括 Util 类和通用模型。
- `dubbo-remoting 远程通讯模块`：相当于 Dubbo 协议的实现，如果 RPC 用 RMI协议则不需要使用此包。
- `dubbo-rpc 远程调用模块`：抽象各种协议，以及动态代理，只包含一对一的调用，不关心集群的管理。
- `dubbo-cluster 集群模块`：将多个服务提供方伪装为一个提供方，包括：负载均衡, 容错，路由等，集群的地址列表可以是静态配置的，也可以是由注册中心下发。
- `dubbo-registry 注册中心模块`：基于注册中心下发地址的集群方式，以及对各种注册中心的抽象。
- `dubbo-monitor 监控模块`：统计服务调用次数，调用时间的，调用链跟踪的服务。
- `dubbo-config 配置模块`：是 Dubbo 对外的 API，用户通过 Config 使用Dubbo，隐藏 Dubbo 所有细节。
- `dubbo-container 容器模块`：是一个 Standlone 的容器，以简单的 Main 加载 Spring 启动，因为服务通常不需要 Tomcat/JBoss 等 Web 容器的特性，没必要用 Web 容器去加载服务。

整体上按照分层结构进行分包，与分层的不同点在于：

- dubbo-container 为服务容器，用于部署运行服务，没有在层中画出。
- Protocol 层和 Proxy 层都放在 dubbo-rpc 模块中，这两层是 rpc 的核心，在不需要集群也就是只有一个提供者时，可以只使用这两层完成 rpc 调用。
- Transport 层和 Exchange 层都放在 dubbo-remoting 模块中，为 rpc 调用的通讯基础。
- Serialize 层放在 dubbo-common 模块中，以便更大程度复用。