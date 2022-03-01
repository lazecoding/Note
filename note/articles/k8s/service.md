# Service

- 目录
  - [定义 Service](#定义-Service)
  - [没有选择算符的 Service](#没有选择算符的-Service)
  - [虚拟 IP 和 Service 代理](#虚拟-IP-和-Service-代理)
  - [多端口 Service](#多端口-Service)
  - [选择自己的 IP 地址](#选择自己的-IP-地址)
  - [服务发现](#服务发现)
    - [环境变量](#环境变量)
    - [DNS](#DNS)
  - [发布服务](#发布服务)
    - [NodePort 类型](#NodePort-类型)
    - [LoadBalancer 类型](#LoadBalancer-类型)
    - [ExternalName](#ExternalName)
    - [外部 IP](#外部-IP)

Pod 是非永久性资源，如果你使用 Deployment 来运行你的应用程序，则它可以动态创建和销毁 Pod。每个 Pod 都有自己的 IP 地址，但是在 Deployment 中，在同一时刻运行的 Pod 集合可能与稍后运行该应用程序的 Pod 集合不同。这导致了一个问题：如果一组 Pod（称为 "后端"）为集群内的其他 Pod（称为 "前端"）提供功能，那么前端如何找出并跟踪要连接的 IP 地址，以便前端可以使用提供工作负载的后端部分？

Kubernetes Service 解决了这个问题，Kubernetes Service 定义了这样一种抽象：一组逻辑上的 Pod 集合和访问这组 Pod 的策略。

### 定义 Service

Service 在 Kubernetes 中是一个 REST 对象，和 Pod 类似。像所有的 REST 对象一样，Service 定义可以基于 POST 方式，请求 API server 创建新的实例。

例如，假定有一组 Pod，它们对外暴露了 9376 端口，同时还被打上 app=MyApp 标签：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  selector:
    app: MyApp
  ports:
    - protocol: TCP
      port: 80
      targetPort: 9376
```

上述配置创建一个名称为 "my-service" 的 Service 对象，它会将请求代理到使用 TCP 端口 9376，并且具有标签 "app=MyApp" 的 Pod 上。

Kubernetes 为该服务分配一个 IP 地址（有时称为 "集群IP"），该 IP 地址由服务代理使用。Service selector 不断扫描与其选择器匹配的 Pod，然后将所有更新发布到也称为  "my-service" 的 Endpoint 对象。Endpoint 是 Kubernetes 集群中的一个资源对象，存储在 etcd 中，用来记录一个 Service 对应的所有 Pod 的访问地址。

> 说明： 需要注意的是，Service 能够将一个接收 port 映射到任意的 targetPort。默认情况下，targetPort 将被设置为与 port 字段相同的值。

Pod 中的端口定义是有名字的，你可以在服务的 targetPort 属性中引用这些名称。即使服务中使用单个配置的名称混合使用 Pod，并且通过不同的端口号提供相同的网络协议，此功能也可以使用，这为部署和发展服务提供了很大的灵活性。例如：你可以更改 Pods 在新版本的后端软件中公开的端口号，而不会破坏客户端。

服务的默认协议是 TCP，你还可以使用任何其他受支持的协议。

### 没有选择算符的 Service

在任何这些场景中，都能够定义没有选择算符的 Service。实例:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  ports:
    - protocol: TCP
      port: 80
      targetPort: 9376
```

由于此服务没有选择算符，因此不会自动创建相应的 Endpoint 对象。你可以通过手动添加 Endpoint 对象，将服务手动映射到运行该服务的网络地址和端口：

```yaml
apiVersion: v1
kind: Endpoints
metadata:
  name: my-service
subsets:
  - addresses:
      - ip: 192.0.2.42
    ports:
      - port: 9376
```

Endpoints 对象的名称必须是合法的 DNS 子域名。

> 端点 IPs 必须不可以 是：本地回路（IPv4 的 127.0.0.0/8, IPv6 的 ::1/128）或 本地链接（IPv4 的 169.254.0.0/16 和 224.0.0.0/24，IPv6 的 fe80::/64)。
>
> 端点 IP 地址不能是其他 Kubernetes 服务的集群 IP，因为 kube-proxy 不支持将虚拟 IP 作为目标。

访问没有选择算符的 Service，与有选择算符的 Service 的原理相同。请求将被路由到用户定义的 Endpoint，yaml 中为：192.0.2.42:9376（TCP）。

类型为 ExternalName 的服务将服务映射到 DNS 名称，而不是典型的选择器，例如 my-service 或者 cassandra。 你可以使用 `spec.externalName` 参数指定这些服务。

例如，以下 Service 定义将 prod 名称空间中的 my-service 服务映射到 my.database.example.com：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
  namespace: prod
spec:
  type: ExternalName
  externalName: my.database.example.com
```

当查找主机 `my-service.prod.svc.cluster.local` 时，集群 DNS 服务返回 CNAME 记录，其值为 `my.database.example.com`。访问 `my-service` 的方式与其他服务的方式相同，但主要区别在于重定向发生在 DNS 级别，而不是通过代理或转发。如果以后你决定将数据库移到集群中，则可以启动其 Pod，添加适当的选择器或端点以及更改服务的 type。

> ExternalName 服务接受 IPv4 地址字符串，但作为包含数字的 DNS 名称，而不是 IP 地址。类似于 IPv4 地址的外部名称不能由 CoreDNS 或 ingress-nginx 解析，因为外部名称旨在指定规范的 DNS 名称。要对 IP 地址进行硬编码，请考虑使用 headless Services。

### 虚拟 IP 和 Service 代理

在 Kubernetes 集群中，每个 Node 运行一个 kube-proxy 进程。 kube-proxy 负责为 Service 实现了一种 VIP（虚拟 IP）的形式，而不是 ExternalName 的形式。

> 为什么不使用 DNS 轮询？时不时会有人问到为什么 Kubernetes 依赖代理将入站流量转发到后端。那其他方法呢？例如，是否可以配置具有多个 A 值（或 IPv6 为 AAAA）的 DNS 记录，并依靠轮询名称解析？
>
> 使用服务代理有以下几个原因：
>
> - DNS 实现的历史由来已久，它不遵守记录 TTL，并且在名称查找结果到期后对其进行缓存。
> - 有些应用程序仅执行一次 DNS 查找，并无限期地缓存结果。
> - 即使应用和库进行了适当的重新解析，DNS 记录上的 TTL 值低或为零也可能会给 DNS 带来高负载，从而使管理变得困难。

### 多端口 Service

对于某些服务，你需要公开多个端口。Kubernetes 允许你在 Service 对象上配置多个端口定义。为服务使用多个端口时，必须提供所有端口名称，以使它们无歧义。例如：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  selector:
    app: MyApp
  ports:
    - name: http
      protocol: TCP
      port: 80
      targetPort: 9376
    - name: https
      protocol: TCP
      port: 443
      targetPort: 9377
```

### 选择自己的 IP 地址

在 Service 创建的请求中，可以通过设置 `spec.clusterIP` 字段来指定自己的集群 IP 地址。比如，希望替换一个已经已存在的 DNS 条目，或者遗留系统已经配置了一个固定的 IP 且很难重新配置。

用户选择的 IP 地址必须合法，并且这个 IP 地址在 `service-cluster-ip-range` CIDR 范围内，这对 API 服务器来说是通过一个标识来指定的。如果 IP 地址不合法，API 服务器会返回 HTTP 状态码 422，表示值不合法。

### 服务发现

Kubernetes 支持两种基本的服务发现模式 —— 环境变量和 DNS。

#### 环境变量

当 Pod 运行在 Node 上，kubelet 会为每个活跃的 Service 添加一组环境变量。 它同时支持 Docker links 兼容变量、 简单的 {SVCNAME}_SERVICE_HOST 和 {SVCNAME}_SERVICE_PORT 变量。这里 Service 的名称需大写，横线被转换成下划线。

举个例子，一个名称为 redis-master 的 Service 暴露了 TCP 端口 6379，同时给它分配了 Cluster IP 地址 10.0.0.11，这个 Service 生成了如下环境变量：

```yaml
REDIS_MASTER_SERVICE_HOST=10.0.0.11
REDIS_MASTER_SERVICE_PORT=6379
REDIS_MASTER_PORT=tcp://10.0.0.11:6379
REDIS_MASTER_PORT_6379_TCP=tcp://10.0.0.11:6379
REDIS_MASTER_PORT_6379_TCP_PROTO=tcp
REDIS_MASTER_PORT_6379_TCP_PORT=6379
REDIS_MASTER_PORT_6379_TCP_ADDR=10.0.0.11
```

> 说明：
>
> 当你具有需要访问服务的 Pod 时，并且你正在使用环境变量方法将端口和集群 IP 发布到客户端 Pod 时，必须在客户端 Pod 出现 之前 创建服务。 否则，这些客户端 Pod 将不会设定其环境变量。
>
> 如果仅使用 DNS 查找服务的集群 IP，则无需担心此设定问题。

#### DNS

你可以（几乎总是应该）使用附加组件为 Kubernetes 集群设置 DNS 服务。

支持集群的 DNS 服务器（例如 CoreDNS）监视 Kubernetes API 中的新服务，并为每个服务创建一组 DNS 记录。如果在整个集群中都启用了 DNS，则所有 Pod 都应该能够通过其 DNS 名称自动解析服务。

例如，如果你在 Kubernetes 命名空间 my-ns 中有一个名为 my-service 的服务，则控制平面和 DNS 服务共同为 `my-service.my-ns` 创建 DNS 记录。my-ns 命名空间中的 Pod 应该能够通过按名检索 my-service 来找到服务 （my-service.my-ns 也可以工作）。

其他命名空间中的 Pod 必须将名称限定为 `my-service.my-ns`，这些名称将解析为为服务分配的集群 IP。

Kubernetes 还支持命名端口的 DNS SRV（服务）记录。 如果 `my-service.my-ns` 服务具有名为 http　的端口，且协议设置为 TCP， 则可以对 `_http._tcp.my-service.my-ns` 执行 DNS SRV 查询查询以发现该端口号, "http" 以及 IP 地址。

### 发布服务

对一些应用的某些部分（如前端），可能希望将其暴露给 Kubernetes 集群外部的 IP 地址。

Kubernetes ServiceTypes 允许指定你所需要的 Service 类型，默认是 ClusterIP。

Type 的取值以及行为如下：

- ClusterIP：通过集群的内部 IP 暴露服务，选择该值时服务只能够在集群内部访问，这也是默认的 ServiceType。
- NodePort：通过每个节点上的 IP 和静态端口（NodePort）暴露服务，NodePort 服务会路由到自动创建的 ClusterIP 服务。通过请求 `<节点 IP>:<节点端口>`，你可以从集群的外部访问一个 NodePort 服务。
- LoadBalancer：使用云提供商的负载均衡器向外部暴露服务。外部负载均衡器可以将流量路由到自动创建的 NodePort 服务和 ClusterIP 服务上。
- ExternalName：通过返回 CNAME 和对应值，可以将服务映射到 externalName 字段的内容（例如，foo.bar.example.com）。无需创建任何类型代理。

> 你也可以使用 Ingress 来暴露自己的服务。Ingress 不是一种服务类型，但它充当集群的入口点，它可以将路由规则整合到一个资源中，因为它可以在同一IP地址下公开多个服务。

#### NodePort 类型

如果你将 type 字段设置为 NodePort，则 Kubernetes 控制平面将在 `--service-node-port-range` 标志指定的范围内分配端口（默认值：30000-32767）。每个节点将那个端口（每个节点上的相同端口号）代理到你的服务中，你的服务在其 `.spec.ports[*].nodePort` 字段中要求分配的端口。

如果你想指定特定的 IP 代理端口，则可以设置 kube-proxy 中的 `--nodeport-addresses` 参数或者将kube-proxy 配置文件中的等效 nodePortAddresses 字段设置为特定的 IP 块。该标志采用逗号分隔的 IP 块列表（例如，10.0.0.0/8、192.0.2.0/25）来指定 kube-proxy 应该认为是此节点本地的 IP 地址范围。

例如，如果你使用 `--nodeport-addresses=127.0.0.0/8` 标志启动 kube-proxy，则 kube-proxy 仅选择 NodePort Services 的本地回路接口。 `--nodeport-addresses` 的默认值是一个空列表，这意味着 kube-proxy 应该考虑 NodePort 的所有可用网络接口。

如果需要特定的端口号，你可以在 nodePort 字段中指定一个值。使用 NodePort 可以让你自由设置自己的负载均衡解决方案，配置 Kubernetes 不完全支持的环境，甚至直接暴露一个或多个节点的 IP。需要注意的是，Service 能够通过 `<NodeIP>:spec.ports[*].nodePort` 和 `spec.clusterIp:spec.ports[*].port` 而对外可见。如果设置了 kube-proxy 的 --nodeport-addresses 参数或 kube-proxy 配置文件中的等效字段，`<NodeIP>` 将被过滤 NodeIP。

例如：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  type: NodePort
  selector:
    app: MyApp
  ports:
    # 默认情况下，为了方便起见，`targetPort` 被设置为与 `port` 字段相同的值。
    - port: 80
      targetPort: 80
      # 可选字段
      # 默认情况下，为了方便起见，Kubernetes 控制平面会从某个范围内分配一个端口号（默认：30000-32767）
      nodePort: 30007
```

#### LoadBalancer 类型

在使用支持外部负载均衡器的云提供商的服务时，设置 type 的值为 "LoadBalancer"，将为 Service 提供负载均衡器。负载均衡器是异步创建的，关于被提供的负载均衡器的信息将会通过 Service 的 `status.loadBalancer` 字段发布出去。

实例：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  selector:
    app: MyApp
  ports:
    - protocol: TCP
      port: 80
      targetPort: 9376
  clusterIP: 10.0.171.239
  type: LoadBalancer
status:
  loadBalancer:
    ingress:
      - ip: 192.0.2.127
```

来自外部负载均衡器的流量将直接重定向到后端 Pod 上，不过实际它们是如何工作的，这要依赖于云提供商。

某些云提供商允许设置 loadBalancerIP，在这些情况下，将根据用户设置的 loadBalancerIP 来创建负载均衡器。如果没有设置 loadBalancerIP 字段，将会给负载均衡器指派一个临时 IP。如果设置了 loadBalancerIP，但云提供商并不支持这种特性，那么设置的 loadBalancerIP 值将会被忽略掉。

#### ExternalName

ExternalName 类型的服务将服务映射到一个 DNS 名称，而不是典型的选择器，如 my-service 或 cassandra。使用 spec.externalName 参数指定这些服务。

例如，这个服务定义将 prod 命名空间中的 my-service 映射到 my.database.example.com。

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
  namespace: prod
spec:
  type: ExternalName
  externalName: my.database.example.com
```

当查找主机 `my-service.prod.svc.cluster.local` 时，集群 DNS 服务返回 CNAME 记录，其值为 my.database.example.com。访问 my-service 的方式与其他服务的方式相同，但主要区别在于重定向发生在 DNS 级别，而不是通过代理或转发。如果以后你决定将数据库移到集群中，则可以启动其 Pod，添加适当的选择器或端点以及更改服务的 type。

> 说明： ExternalName 服务接受 IPv4 地址字符串，但作为包含数字的 DNS 名称，而不是 IP 地址。类似于 IPv4 地址的外部名称不能由 CoreDNS 或 ingress-nginx 解析，因为外部名称旨在指定规范的 DNS 名称。要对 IP 地址进行硬编码，请考虑使用 headless Services。

#### 外部 IP

如果外部的 IP 路由到集群中一个或多个 Node 上，Kubernetes Service 会被暴露给这些 externalIPs。通过外部 IP（作为目的 IP 地址）进入到集群，打到 Service 的端口上的流量，将会被路由到 Service 的 Endpoint 上。externalIPs 不会被 Kubernetes 管理，它属于集群管理员的职责范畴。

根据 Service 的规定，externalIPs 可以同任意的 ServiceType 来一起指定。在上面的例子中，my-service 可以在 `"80.11.12.10:80"(externalIP:port)` 上被客户端访问。

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  selector:
    app: MyApp
  ports:
    - name: http
      protocol: TCP
      port: 80
      targetPort: 9376
  externalIPs:
    - 80.11.12.10
```