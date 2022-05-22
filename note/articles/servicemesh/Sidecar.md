# Sidecar 模式

- 目录
  - [Sidecar 的注入](#Sidecar-的注入)
    - [istio-init](#istio-init)

大部分应用和服务都需要实现额外的服务治理功能，这些功能可以作为单独的组件或服务而存在。如果将这些功能集成到业务应用中，就会与业务应用运行在同一个进程中，从而可以有效地共享资源。从另一方面来看，这也意味着服务治理组件与业务应用之间的隔离性很弱，一旦其中一个组件出现故障，可能会影响其他组件甚至整个应用程序。除此之外，服务治理组件需要使用与父应用程序相同的技术栈来实现，因此它们之间有密切的相互依赖性。

上述问题的解决方案是，`将服务治理功能从应用本身剥离出来作为单独进程`，与主应用程序共同放在一台主机（Host）中，但会将它们部署在各自的进程或容器中。这种方式也被称为 Sidecar（边车）模式。该模式允许我们向应用无侵入添加多种功能，避免了为满足第三方组件需求而向应用添加额外的配置代码。

使用 Sidecar 模式的好处有很多：

- 通过将服务治理相关功能抽象到不同的层来降低微服务的代码复杂性
- 在运行时环境和编程语言方面，Sidecar 独立于其主要应用程序，不需要为每个微服务编写服务治理功能的代码，减少了微服务架构中的代码重复。
- Sidecar 可以访问与主应用程序相同的资源。例如，Sidecar 可以监视 Sidecar 本身和主应用程序使用的系统资源。
- 由于它靠近主应用程序，因此在它们之间进行通信时没有明显的延迟。
- 降低了应用与底层平台的耦合度。

> 使用 Sidecar 模式部署服务网格时，无需在节点上运行代理（因此您不需要基础结构的协作），但是集群中将运行多个相同的 Sidecar 副本。从另一个角度看：我可以为一组微服务部署到一个服务网格中，你也可以部署一个有特定实现的服务网格。在 Sidecar 部署方式中，你会为每个应用的容器部署一个伴生容器。Sidecar 接管进出应用容器的所有流量。在 Kubernetes 的 Pod 中，在原有的应用容器旁边运行一个 Sidecar 容器，可以理解为两个容器共享存储、网络等资源，可以广义的将这个注入了 Sidecar 容器的 Pod 理解为一台主机，两个容器共享主机资源。

### Sidecar 的注入

如 Istio 官方文档中对 Istio Sidecar 注入的描述，你可以使用 istioctl 命令手动注入 Sidecar，也可以为 Kubernetes 集群自动开启 Sidecar 注入，这主要用到了 Kubernetes 的准入控制器中的 webhook。

不论是手动注入还是自动注入，sidecar 的注入过程都需要遵循如下步骤：

- Kubernetes 需要了解待注入的 Sidecar 所连接的 Istio 集群及其配置。
- Kubernetes 需要了解待注入的 Sidecar 容器本身的配置，如镜像地址、启动参数等。
- Kubernetes 根据 Sidecar 注入模板和以上配置填充 Sidecar 的配置参数，将以上配置注入到应用容器的一侧。

使用下面的命令可以手动注入 Sidecar。

```C
istioctl kube-inject -f ${YAML_FILE} | kuebectl apply -f -
```

注入完成后您将看到 Istio 为原有 Pod template 注入了 initContainer 及 Sidecar proxy 相关的配置。

- Init 容器 istio-init：用于 Pod 中设置 iptables 端口转发
- Sidecar 容器 istio-proxy：运行 Sidecar 代理，如 Envoy 或 MOSN。

#### istio-init

Istio 在 Pod 中注入的 Init 容器名为 istio-init，我们在上面 Istio 注入完成后的 YAML 文件中看到了该容器的启动命令是：

```C
istio-iptables -p 15001 -z 15006 -u 1337 -m REDIRECT -i '*' -x "" -b '*' -d 15090,15020
```

Init 容器的启动入口是 `istio-iptables` 命令行，该命令行工具的用法如下：

```C
$ istio-iptables [flags]
  -p: 指定重定向所有 TCP 流量的 Sidecar 端口（默认为 $ENVOY_PORT = 15001）
  -m: 指定入站连接重定向到 Sidecar 的模式，“REDIRECT” 或 “TPROXY”（默认为 $ISTIO_INBOUND_INTERCEPTION_MODE)
  -b: 逗号分隔的入站端口列表，其流量将重定向到 Envoy（可选）。使用通配符 “*” 表示重定向所有端口。为空时表示禁用所有入站重定向（默认为 $ISTIO_INBOUND_PORTS）
  -d: 指定要从重定向到 Sidecar 中排除的入站端口列表（可选），以逗号格式分隔。使用通配符“*” 表示重定向所有入站流量（默认为 $ISTIO_LOCAL_EXCLUDE_PORTS）
  -o：逗号分隔的出站端口列表，不包括重定向到 Envoy 的端口。
  -i: 指定重定向到 Sidecar 的 IP 地址范围（可选），以逗号分隔的 CIDR 格式列表。使用通配符 “*” 表示重定向所有出站流量。空列表将禁用所有出站重定向（默认为 $ISTIO_SERVICE_CIDR）
  -x: 指定将从重定向中排除的 IP 地址范围，以逗号分隔的 CIDR 格式列表。使用通配符 “*” 表示重定向所有出站流量（默认为 $ISTIO_SERVICE_EXCLUDE_CIDR）。
  -k：逗号分隔的虚拟接口列表，其入站流量（来自虚拟机的）将被视为出站流量。
  -g：指定不应用重定向的用户的 GID。(默认值与 -u param 相同)
  -u：指定不应用重定向的用户的 UID。通常情况下，这是代理容器的 UID（默认值是 1337，即 istio-proxy 的 UID）。
  -z: 所有进入 Pod/VM 的 TCP 流量应被重定向到的端口（默认 $INBOUND_CAPTURE_PORT = 15006）。
```

该容器存在的意义就是让 Sidecar 代理可以拦截所有的进出 Pod 的流量，15090 端口（Mixer 使用）和 15092 端口（Ingress Gateway）除外的所有入站（inbound）流量重定向到 15006 端口（Sidecar），再拦截应用容器的出站（outbound）流量经过 Sidecar 处理（通过 15001 端口监听）后再出站。