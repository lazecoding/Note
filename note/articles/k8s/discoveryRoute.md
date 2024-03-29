# 服务发现和路由

每一个 Pod 都有它自己的IP地址，这就意味着你不需要显式地在 Pod 之间创建链接，你几乎不需要处理容器端口到主机端口之间的映射。这将形成一个干净的、向后兼容的模型；在这个模型里，从端口分配、命名、服务发现、负载均衡、应用配置和迁移的角度来看，Pod 可以被视作虚拟机或者物理主机。

Kubernetes 强制要求所有网络设施都满足以下基本要求（从而排除了有意隔离网络的策略）：

- 节点上的 Pod 可以不通过 NAT 和其他任何节点上的 Pod 通信。
- 节点上的代理（比如：系统守护进程、kubelet）可以和节点上的所有 Pod 通信。

备注：对于支持在主机网络中运行 Pod 的平台（比如：Linux）：

- 运行在节点主机网络里的 Pod 可以不通过 NAT 和所有节点上的 Pod 通信。

这个模型不仅不复杂，而且还和 Kubernetes 的实现从虚拟机向容器平滑迁移的初衷相符，如果你的任务开始是在虚拟机中运行的，你的虚拟机有一个 IP，可以和项目中其他虚拟机通信。这里的模型是基本相同的。

Kubernetes 的 IP 地址存在于 Pod 范围内 - 容器共享它们的网络命名空间 - 包括它们的 IP 地址和 MAC 地址。这就意味着 Pod 内的容器都可以通过 localhost 到达对方端口，这也意味着 Pod 内的容器需要相互协调端口的使用，但是这和虚拟机中的进程似乎没有什么不同，这也被称为 "一个 Pod 一个 IP" 模型。

Kubernetes 网络解决四方面的问题：

- 一个 Pod 中的容器之间通过本地回路（loopback）通信。
- 集群网络在不同 pod 之间提供通信。
- Service 资源允许你对外暴露 Pods 中运行的应用程序，以支持来自于集群外部的访问。
- 可以使用 Services 来发布仅供集群内部使用的服务。

> Kubernetes 中为了实现服务实例间的负载均衡和不同服务间的服务发现，创造了 Serivce 对象，同时又为从集群外部访问集群创建了 Ingress 对象。

-------------------------------------------------

更多：

- [Service](https://github.com/lazecoding/Note/blob/main/note/articles/k8s/service.md)
- [Pod 与 Service 的 DNS](https://github.com/lazecoding/Note/blob/main/note/articles/k8s/dnsInK8S.md)
- [Ingress](https://github.com/lazecoding/Note/blob/main/note/articles/k8s/Ingress.md)