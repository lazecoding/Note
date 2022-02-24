# 集群资源管理

- 目录
  - [Node](#Node)
    - [管理节点](#管理节点)
    - [节点状态](#节点状态)
    - [心跳](#心跳)
  - [Namespace](#Namespace)
  - [Label](#Label)
    - [语法和字符集](#语法和字符集)
    - [Label Selector](#Label-Selector)

为了管理异构和不同配置的主机，为了便于 Pod 的运维管理，Kubernetes 中提供了很多集群管理的配置和管理功能，通过 namespace 划分的空间，通过为 Node 节点创建 Label 和 Taint 用于 Node 的调度等。

### Node

Kubernetes 通过将容器放入在节点（Node）上运行的 Pod 中来执行你的工作负载。节点可以是一个虚拟机或者物理机器，取决于所在的集群配置。每个节点包含运行 Pods 所需的服务，这些节点由控制面负责管理。通常集群中会有若干个节点，而在一个资源受限的环境中，你的集群中也可能只有一个节点。

> 控制平面（Control Plane）是指容器编排层，它暴露 API 和接口来定义、部署容器和管理容器的生命周期。

节点上的组件包括 `kubelet`、`容器运行时` 以及 `kube-proxy`。 

- `kubelet`：一个在集群中每个节点（node）上运行的代理，它保证容器（containers）都运行在 Pod 中。
- `容器运行时`：Container Runtime，容器运行环境是负责运行容器的软件。
- `kube-proxy`:它是集群中每个节点上运行的网络代理， 实现 Kubernetes 服务（Service） 概念的一部分。

#### 管理节点

向 API 服务器添加节点的方式主要有两种：

- 节点上的 kubelet 向控制面执行自注册。
- 你，或者别的什么人，手动添加一个 Node 对象。

在你创建了 Node 对象或者节点上的 kubelet 执行了自注册操作之后，控制面会检查新的 Node 对象是否合法。例如，如果你使用下面的 JSON 对象来创建 Node 对象：

```json
{
  "kind": "Node",
  "apiVersion": "v1",
  "metadata": {
    "name": "10.240.79.157",
    "labels": {
      "name": "my-first-k8s-node"
    }
  }
}
```

Kubernetes 会在内部创建一个 Node 对象作为节点的表示。Kubernetes 检查 kubelet 向 API 服务器注册节点时使用的 metadata.name 字段是否匹配。如果节点是健康的（即所有必要的服务都在运行中），则该节点可以用来运行 Pod。否则，直到该节点变为健康之前，所有的集群活动都会忽略该节点。

>  Kubernetes 会一直保存着非法节点对应的对象，并持续检查该节点是否已经 变得健康。你，或者某个控制器必需显式地删除该 Node 对象以停止健康检查操作。

#### 节点状态

一个节点的状态包含以下信息:地址、状况、容量与可分配、信息。

你可以使用 kubectl 来查看节点状态和其他细节信息：

```C
kubectl describe node <节点名称>
```

- `Address（地址）`:
  - HostName：可以被 kubelet 中的 --hostname-override 参数替代。
  - ExternalIP：可以被集群外部路由到的 IP 地址。
  - InternalIP：集群内部使用的 IP，集群外部无法访问。
- `Condition（状况）`:
  - Ready：如节点是健康的并已经准备好接收 Pod 则为 True；False 表示节点不健康而且不能接收 Pod；Unknown 表示节点控制器在最近 node-monitor-grace-period 期间（默认 40 秒）没有收到节点的消息。
  - OutOfDisk：磁盘空间不足时为 True。
  - DiskPressure：True 表示节点存在磁盘空间压力，即磁盘可用量低, 否则为 False。
  - MemoryPressure：True 表示节点存在内存压力，即节点内存可用量低，否则为 False。
  - PIDPressure：True 表示节点存在进程压力，即节点上进程过多；否则为 False。
  - NetworkUnavailable：True 表示节点网络配置不正确；否则为 False。
- `Capacity（容量与可分配）`:
  - CPU。
  - 内存。
  - 可运行的最大 Pod 个数。
- `Info（信息）`：节点的一些版本信息，如 OS、kubernetes、docker 等。

#### 心跳

Kubernetes 节点发送的心跳帮助你的集群确定每个节点的可用性，并在检测到故障时采取行动。

对于节点，有两种形式的心跳:

- 更新节点的 `.status`。
- Lease 对象在 `kube-node-lease` 命名空间中，每个节点都有一个关联的 Lease 对象。

与 Node 的 .status 更新相比，Lease 是一种轻量级资源，使用 Leases 心跳在大型集群中可以减少这些更新对性能的影响。

kubelet 负责创建和更新节点的 .status，以及更新它们对应的 Lease。

- 当状态发生变化时，或者在配置的时间间隔内没有更新事件时，kubelet 会更新 `.status`。`.status` 更新的默认间隔为 5 分钟（比不可达节点的 40 秒默认超时时间长很多）。
- kubelet 会每 10 秒（默认更新间隔时间）创建并更新其 Lease 对象，如果 Lease 的更新操作失败，kubelet 会采用指数回退机制，从 200 毫秒开始 重试，最长重试间隔为 7 秒钟。Lease 更新独立于 NodeStatus 更新而发生。

### Namespace

namespace 可以认为是 kubernetes 集群中 "虚拟集群"，在一个 Kubernetes 集群中可以拥有多个 namespace，它们在逻辑上彼此隔离。但也可以通过某种方式，让一个 namespace 中的 service 可以访问到其他的 namespace 中的服务。

Kubernetes 中的集群通常默认会有一个叫 default 的 namespace。这个默认（default）的 namespace 并没什么特别，但你不能删除它。这很适合刚刚开始使用 kubernetes 和一些小的产品系统，但不建议应用于大型生产系统。因为，在复杂系统中或人员很多的项目中，团队会非常容易意外地或者无意识地重写或者中断其他服务 service。相反，创建多个命名空间来把你的服务 service 分割成更容易管理的块，而且并不会降低服务性能。

另外，并不是所有的资源对象都会对应 namespace，Node 和 PersistentVolume 就不属于任何 namespace。

### Label

Label 是附着到 object 上（例如 Pod）的键值对。可以在创建 object 的时候指定，也可以在 object 创建后随时指定。Labels 的值对系统本身并没有什么含义，只是对用户才有意义。

我们通常使用 `metadata.labels` 字段，来为对象添加 Label。Label 可以为多个。一个简单的例子如下：

```C
apiVersion: v1
kind: Pod
metadata:
  name: nginx
  labels:
    app: nginx
    release: stable
spec:
  containers:
  - name: nginx
    image: nginx
    ports:
    - containerPort: 80
```

上面的描述文件为名为 nginx 的 Pod 添加了两个Label，分别为 `app: nginx` 和 `release: stable`。

> Kubernetes 最终将对 Labels 最终索引和反向索引用来优化查询和 watch，在 UI 和命令行中会对它们排序。不要在 Label 中使用大型、非标识的结构化数据，记录这样的数据应该用 annotation。

#### 语法和字符集

Label key 的组成：

- 不得超过 63 个字符。
- 可以使用前缀，使用 / 分隔，前缀必须是 DNS 子域，不得超过 253 个字符，系统中的自动化组件创建的 Label 必须指定前缀，kubernetes.io/ 由 kubernetes 保留。
- 起始必须是字母（大小写都可以）或数字，中间可以有连字符、下划线和点。

Label value 的组成：

- 不得超过 63 个字符。
- 起始必须是字母（大小写都可以）或数字，中间可以有连字符、下划线和点。

#### Label Selector

Label 不是唯一的，很多 object 可能有相同的 Label。

通过 label Selector，客户端／用户可以指定一个 object 集合，通过 Label Selector 对 object 的集合进行操作。

Label Selector 有两种类型：

- `基于等式的 Selector（Equality-based）`：可以使用 =、==、!= 操作符，可以使用逗号分隔多个表达式。
- `基于集合的 Selector（Set-based）`：可以使用 in、notin、! 操作符，另外还可以没有操作符，直接写出某个 Label 的 key，表示过滤有某个 key 的 object 而不管该 key 的 value 是何值，! 表示没有该 label 的 object。

示例：

```C
$ kubectl get pods -l environment=production,tier=frontend
$ kubectl get pods -l 'environment in (production),tier in (frontend)'
$ kubectl get pods -l 'environment in (production, qa)'
$ kubectl get pods -l 'environment,environment notin (frontend)'
```