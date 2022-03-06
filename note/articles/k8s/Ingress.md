# Ingress

- 目录
  - [Ingress 是什么](#Ingress-是什么)
  - [Ingress 资源](#Ingress-资源)
    - [Ingress 规则](#Ingress-规则)
    - [路径类型](#路径类型)
    - [主机名通配符](#主机名通配符)
  - [Ingress Controller](#Ingress-Controller)
  - [IngressClass](#IngressClass)

Ingress 是对集群中服务的外部访问进行管理的 API 对象，典型的访问方式是 HTTP。Ingress 可以提供负载均衡、SSL 终结和基于名称的虚拟托管。

术语：

- `节点（Node）`: Kubernetes 集群中其中一台工作机器，是集群的一部分。
- `集群（Cluster）`: 一组运行由 Kubernetes 管理的容器化应用程序的节点。在此示例和在大多数常见的 Kubernetes 部署环境中，集群中的节点都不在公共网络中。
- `边缘路由器（Edge router）`: 在集群中强制执行防火墙策略的路由器（router）。可以是由云提供商管理的网关，也可以是物理硬件。
- `集群网络（Cluster network）`: 一组逻辑的或物理的连接，根据 Kubernetes 网络模型在集群内实现通信。
- `服务（Service）`：Kubernetes Service 使用标签选择算符（selectors）标识的一组 Pod。除非另有说明，否则假定服务只具有在集群网络中可路由的虚拟 IP。

### Ingress 是什么

通常情况下，Service 和 Pod 仅可在集群内部网络中通过 IP 地址访问，所有到达边界路由器的流量或被丢弃或被转发到其他地方。

Ingress 是授权入站连接到达集群服务的规则集合。你可以给 Ingress 配置提供外部可访问的 URL、负载均衡、SSL、基于名称的虚拟主机等。用户通过 POST Ingress 资源到 API server 的方式来请求 Ingress。Ingress Controller 负责实现 Ingress，通常使用负载均衡器，它还可以配置边界路由和其他前端，这有助于以高可用的方式处理流量。

下面是一个将所有流量都发送到同一 Service 的简单 Ingress 示例：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/k8s/Ingress流量管理示意图.png" width="600px">
</div>

Ingress 不会公开任意端口或协议。将 HTTP 和 HTTPS 以外的服务公开到 Internet 时，通常使用 `Service.Type=NodePort` 或 `Service.Type=LoadBalancer` 类型的服务。

### Ingress 资源

一个最小的 Ingress 资源示例：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingress-wildcard-host
spec:
  rules:
  - host: "foo.bar.com"
    http:
      paths:
      - pathType: Prefix
        path: "/bar"
        backend:
          service:
            name: service1
            port:
              number: 80
  - host: "*.foo.com"
    http:
      paths:
      - pathType: Prefix
        path: "/foo"
        backend:
          service:
            name: service2
            port:
              number: 80
```

与所有其他 Kubernetes 资源一样，Ingress 需要使用 apiVersion、kind 和 metadata 字段。Ingress 经常使用注解（annotations）来配置一些选项，具体取决于 Ingress Controller，不同的 Ingress Controller支持不同的注解。

Ingress 规约提供了配置负载均衡器或者代理服务器所需的所有信息，最重要的是，其中包含与所有传入请求匹配的规则列表。Ingress 资源仅支持用于转发 HTTP 流量的规则。

#### Ingress 规则

每个 HTTP 规则都包含以下信息：

- 可选的 host。在此示例中，未指定 host，因此该规则适用于通过指定 IP 地址的所有入站 HTTP 通信。 如果提供了 host（例如 foo.bar.com），则 rules 适用于该 host。
- 路径列表 paths（例如，/testpath）,每个路径都有一个由 serviceName 和 servicePort 定义的关联后端。在负载均衡器将流量定向到引用的服务之前，主机和路径都必须匹配传入请求的内容。
- backend（后端）是 Service 文档中所述的服务和端口名称的组合。 与规则的 host 和 path 匹配的对 Ingress 的 HTTP（和 HTTPS ）请求将发送到列出的 backend。

通常在 Ingress 控制器中会配置 defaultBackend（默认后端），以服务于不符合规约中 path 的请求。没有 rules 的 Ingress 和 hosts 或 paths 都没有与 Ingress 对象中的 HTTP 请求匹配，则流量将路由到 DefaultBackend。

#### 路径类型

Ingress 中的每个路径都需要有对应的路径类型（Path Type），未明确设置 pathType 的路径无法通过合法性检查。当前支持的路径类型有三种：

- `ImplementationSpecific`：对于这种路径类型，匹配方法取决于 IngressClass。具体实现可以将其作为单独的 pathType 处理或者与 Prefix 或 Exact 类型作相同处理。
- `Exact`：精确匹配 URL 路径，且区分大小写。
- `Prefix`：基于以 / 分隔的 URL 路径前缀匹配。匹配区分大小写，并且对路径中的元素逐个完成。路径元素指的是由 / 分隔符分隔的路径中的标签列表。如果每个 p 都是请求路径 p 的元素前缀，则请求与路径 p 匹配。

> 如果路径的最后一个元素是请求路径中最后一个元素的子字符串，则不会匹配（例如：/foo/bar 匹配 /foo/bar/baz, 但不匹配 /foo/barbaz）。
>
> 在某些情况下，Ingress 中的多条路径会匹配同一个请求，这种情况下最长的匹配路径优先。如果仍然有两条同等的匹配路径，则精确路径类型优先于前缀路径类型。

#### 主机名通配符

主机名可以是精确匹配（例如 "foo.bar.com"）或者使用通配符来匹配 （例如 "*.foo.com"）。精确匹配要求 HTTP host 头部字段与 host 字段值完全匹配，通配符匹配则要求 HTTP host 头部字段与通配符规则中的后缀部分相同。

| 主机       | host 头部         | 匹配与否？                         |
| ---------- | ---------------- | ---------------------------------  |
| *.foo.com  | bar.foo.com      | 基于相同的后缀匹配                  |
| *.foo.com  | baz.bar.foo.com  | 不匹配，通配符仅覆盖了一个 DNS 标签  |
| *.foo.com  | foo.com          | 不匹配，通配符仅覆盖了一个 DNS 标签  |

### Ingress Controller

为了使 Ingress 正常工作，集群中必须运行 Ingress Controller。这与其他类型的控制器不同，其他类型的控制器通常作为 `kube-controller-manager` 二进制文件的一部分运行，在集群启动时自动启动，你需要选择最适合自己集群的 Ingress Controller 或者自己实现一个。

Kubernetes 目前支持和维护 AWS， GCE 和 nginx Ingress 控制器。

### IngressClass

Ingress 可以由不同的控制器实现，通常使用不同的配置。每个 Ingress 应当指定一个类，也就是一个对 IngressClass 资源的引用，IngressClass 资源包含额外的配置，其中包括应当实现该类的控制器名称。
