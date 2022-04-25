# Istio

- 目录
  - [Istio 和 Kubernetes](#Istio-和-Kubernetes)
  - [xDS 协议](#xDS-协议)
  - [Envoy](#Envoy)
    - [Istio Service Mesh](#Istio-Service-Mesh)

Istio 是可配置的开源服务网格层，用于连接、监视和保护 Kubernetes 集群中的容器。Kubernetes 是一个容器编排工具，Kubernetes 的一个核心单元就是一个节点。节点包含一个或多个容器，以及文件系统或其他组件。 微服务架构可能有十几个不同的节点，每个节点表示不同的微服务。Kubernetes 可管理节点的可用性和资源消耗情况，随着需求的增加通过 Pod 自动缩放控制器增加 Pod。 Istio 可将其他容器注入到 Pod 中，以增添安全性、管理和监视功能。

### Istio 和 Kubernetes

下图展示的是 Kubernetes 与 Service Mesh 中的的服务访问关系：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/servicemesh/kubernetes-vs-service-mesh.png" width="600px">
</div>

- 流量转发

Kubernetes 集群中的每个节点都部署了一个 kube-proxy 组件，该组件与 Kubernetes API Server 进行通信，获取集群中的服务信息，然后设置 iptables 规则，将服务请求直接发送到对应的 Endpoint（属于同一组服务的 Pod）。

Istio 在集群中，最常见的方式就是使用 Sidecar 模式。Sidecar 是 Pod 中的新容器，用于路由和观测服务与容器之间的通信流量，它为每个服务都注入一个代理。

- 服务发现

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/servicemesh/istio-service-registry.png" width="600px">
</div>

Istio 可以跟踪 Kubernetes 中的服务注册，也可以在控制平面中通过平台适配器与其他服务发现系统对接；然后生成数据平面的配置（使用 CRD，这些配置存储在 etcd 中），数据平面的透明代理。数据平面的透明代理以 Sidecar 容器的形式部署在每个应用服务的 Pod 中，这些代理都需要请求控制平面同步代理配置。代理之所以 "透明"，是因为应用容器完全不知道代理的存在。过程中的 kube-proxy 组件也需要拦截流量，只不过 kube-proxy 拦截的是进出 Kubernetes 节点的流量，而 Sidecar 代理拦截的是进出 Pod 的流量。

> 由于 Kubernetes 的每个节点上都运行着很多 Pod，所以在每个 Pod 中放入原有的 kube-proxy 路由转发功能，会增加响应延迟——由于 Sidecar 拦截流量时跳数更多，消耗更多的资源。为了对流量进行精细化管理，将增加一系列新的抽象功能。这将进一步增加用户的学习成本，但随着技术的普及，这种情况会慢慢得到缓解。
>
> kube-proxy 的设置是全局的，无法对每个服务进行细粒度的控制，而 service mesh 通过 Sidecar proxy 的方式将 Kubernetes 中的流量控制从服务层中抽离出来–可以实现更大的弹性。

### xDS 协议

xDS 协议控制了 Service Mesh 中所有流量的具体行为，即将  Service Mesh 中 Service 链接起来。

xDS 协议是由 Envoy 提出的，在 Envoy v2 版本 API 中最原始的 xDS 协议指的是 CDS（Cluster Discovery Service）、EDS（Endpoint Discovery service）、LDS（Listener Discovery Service） 和 RDS（Route Discovery Service），后来在 v3 版本中又发展出了 Scoped Route Discovery Service（SRDS）、Virtual Host Discovery Service （VHDS）、Secret Discovery Service（SDS）、Runtime Discovery Service（RTDS）等，详见 [xDS REST and gRPC protocol](https://www.envoyproxy.io/docs/envoy/latest/api-docs/xds_protocol) 。

下面我们以各有两个实例的 Service，来看下 xDS 协议。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/servicemesh/service-mesh-schematic-diagram.png" width="600px">
</div>

上图中的箭头不是流量进入 Proxy 后的路径或路由，也不是实际顺序，而是想象的一种 xDS 接口处理顺序，其实 xDS 之间也是有交叉引用的。

支持 xDS 协议的代理通过查询文件或管理服务器来动态发现资源。概括地讲，对应的发现服务及其相应的 API 被称作 xDS。Envoy 通过 订阅（subscription） 的方式来获取资源，订阅方式有以下三种：

- 文件订阅：监控指定路径下的文件，发现动态资源的最简单方式就是将其保存于文件，并将路径配置在 ConfigSource 中的 path 参数中。
- gRPC 流式订阅：每个 xDS API 可以单独配置 ApiConfigSource，指向对应的上游管理服务器的集群地址。
- 轮询 REST-JSON 轮询订阅：单个 xDS API 可对 REST 端点进行的同步（长）轮询。

> Istio 使用 gRPC 流式订阅的方式配置所有的数据平面的 Sidecar Proxy。

xDS 协议要点：

- CDS、EDS、LDS、RDS 是最基础的 xDS 协议，它们可以分别独立更新。
- 所有的发现服务（Discovery Service）可以连接不同的 Management Server，也就是说管理 xDS 的服务器可以是多个。
- Envoy 在原始 xDS 协议的基础上进行了一些列扩充，增加了 SDS（秘钥发现服务）、ADS（聚合发现服务）、HDS（健康发现服务）、MS（Metric 服务）、RLS（速率限制服务）等 API。
- 为了保证数据一致性，若直接使用 xDS 原始 API 的话，需要保证这样的顺序更新：CDS –> EDS –> LDS –> RDS，这是遵循电子工程中的先合后断（Make-Before-Break）原则，即在断开原来的连接之前先建立好新的连接，应用在路由里就是为了防止设置了新的路由规则的时候却无法发现上游集群而导致流量被丢弃的情况，类似于电路里的断路。
- CDS 设置 Service Mesh 中有哪些服务。
- EDS 设置哪些实例（Endpoint）属于这些服务（Cluster）。
- LDS 设置实例上监听的端口以配置路由。
- RDS 最终服务间的路由关系，应该保证最后更新 RDS。

### Envoy

Envoy 是一款 CNCF 旗下的开源项目，由 Lyft 开源。Envoy 采用 C++ 实现，是面向 Service Mesh 的高性能网络代理服务。它与应用程序并行运行，通过以平台无关的方式提供通用功能来抽象网络。当基础架构中的所有服务流量都通过 Envoy 网格时，通过一致的可观测性，很容易地查看问题区域，调整整体性能。

Envoy 架构：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/servicemesh/envoy-architecture.png" width="600px">
</div>

基本术语：

- Downstream（下游）：下游主机连接到 Envoy，发送请求并接收响应，即发送请求的主机。
- Upstream（上游）：上游主机接收来自 Envoy 的连接和请求，并返回响应，即接受请求的主机。
- Listener（监听器）：监听器是命名网地址（例如，端口、unix domain socket 等)，下游客户端可以连接这些监听器。Envoy 暴露一个或者多个监听器给下游主机连接。
- Cluster（集群）：集群是指 Envoy 连接的一组逻辑相同的上游主机。Envoy 通过服务发现来发现集群的成员。可以选择通过主动健康检查来确定集群成员的健康状态。Envoy 通过负载均衡策略决定将请求路由到集群的哪个成员。

Envoy 中可以设置多个 Listener，每个 Listener 中又可以设置 filter chain（过滤器链表），而且过滤器是可扩展的，这样就可以更方便我们操作流量的行为，例如设置加密、私有 RPC 等。

xDS 协议是由 Envoy 提出的，现在是 Istio 中默认的 Sidecar Proxy，但只要实现 xDS 协议理论上都是可以作为 Istio 中的 Sidecar Proxy 的。

#### Istio Service Mesh

Envoy 是 Istio 中默认的 Sidecar 代理，Istio 基于 Envoy 的 xDS 协议扩展了其控制平面。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/servicemesh/istio-service-mesh-architecture.png" width="600px">
</div>

Istio 中定义了如下的 CRD（自定义资源）来帮助用户进行流量管理：

- Gateway：Gateway 描述了在网络边缘运行的负载均衡器，用于接收传入或传出的HTTP / TCP连接。
- VirtualService：VirtualService 实际上将 Kubernetes 服务连接到 Istio Gateway。它还可以执行更多操作，例如定义一组流量路由规则，以便在主机被寻址时应用。
- DestinationRule：DestinationRule 所定义的策略，决定了经过路由处理之后的流量的访问策略。简单的说就是定义流量如何路由。这些策略中可以定义负载均衡配置、连接池尺寸以及外部检测（用于在负载均衡池中对不健康主机进行识别和驱逐）配置。
- EnvoyFilter：EnvoyFilter 对象描述了针对代理服务的过滤器，这些过滤器可以定制由 Istio Pilot 生成的代理配置。这个配置初级用户一般很少用到。
- ServiceEntry：默认情况下 Istio Service Mesh 中的服务是无法发现 Mesh 外的服务的，ServiceEntry 能够在 Istio 内部的服务注册表中加入额外的条目，从而让网格中自动发现的服务能够访问和路由到这些手工加入的服务。

> 如果说 Kubernetes 管理的对象是 Pod，那么 Service Mesh 中管理的对象就是一个个 Service，所以说使用 Kubernetes 管理微服务后再应用 Service Mesh 就是水到渠成了。