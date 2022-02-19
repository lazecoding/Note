# Pod

- 目录
  - [管理 Pod](#管理-Pod)
    - [Pod 和 Controller](#Pod-和-Controller)
    - [Pod 的终止](#Pod-的终止)
  - [Init 容器](#Init-容器)
    - [具体行为](#具体行为)

Pod 是 kubernetes 中你可以创建和部署的最小也是最简的单位。Pod 代表着集群中运行的进程。

Pod 中封装着应用的容器（有的情况下是好几个容器），存储、独立的网络 IP，管理容器如何运行的策略选项。Pod 代表着部署的一个单位：kubernetes 中应用的一个实例，可能由一个或者多个容器组合在一起共享资源。Pod 中的容器总是被同时调度，有共同的运行环境。你可以把单个 Pod 想象成是运行独立应用的 “逻辑主机”—— 其中运行着一个或者多个紧密耦合的应用容器 —— 在有容器之前，这些应用都是运行在几个相同的物理机或者虚拟机上。

> Docker 是 kubernetes 中最常用的容器运行时，但是 Pod 也支持其他容器运行时。


Pod 是一个服务的多个进程的聚合单位，Pod 提供这种模型能够简化应用部署管理，通过提供一个更高级别的抽象的方式。Pod 作为一个独立的部署单位，支持横向扩展和复制。共生（协同调度），命运共同体（例如被终结），协同复制，资源共享，依赖管理，Pod 都会自动的为容器处理这些问题。

在 Kubernetes 集群中 Pod 有如下两种使用方式：

- `一个 Pod 中运行一个容器`。"每个 Pod 中一个容器" 的模式是最常见的用法；在这种使用方式中，你可以把 Pod 想象成是单个容器的封装，kuberentes 管理的是 Pod 而不是直接管理容器。
- `在一个 Pod 中同时运行多个容器`。一个 Pod 中也可以同时封装几个需要紧密耦合互相协作的容器，它们之间共享资源。这些在同一个 Pod 中的容器可以互相协作成为一个 service 单位 —— 一个容器共享文件，另一个 "sidecar" 容器来更新这些文件。Pod 将这些容器的存储资源作为一个实体来管理。

每个 Pod 都是应用的一个实例。如果你想横向扩展应用的话（运行多个实例），你应该运行多个 Pod，每个 Pod 都是一个应用实例。在 Kubernetes 中，这通常被称为 replication。

Pod 中可以同时运行多个进程（作为容器运行）协同工作，同一个 Pod 中的容器会被分配到同一个 Node 上，同一个 Pod 中的容器共享资源、网络环境和依赖，它们总是被同时调度。

Pod 中可以共享两种资源：网络和存储。

- `网络`：每个 Pod 都会被分配一个唯一的 IP 地址。Pod 中的所有容器共享网络空间，包括 IP 地址和端口。Pod 内部的容器可以使用 `localhost` 互相通信。Pod 中的容器与外界通信时，必须分配共享网络资源（例如使用宿主机的端口映射）。
- `存储`：可以为一个 Pod 指定多个共享的 Volume。Pod 中的所有容器都可以访问共享的 volume。Volume 也可以用来持久化 Pod 中的存储资源，以防容器重启后文件丢失。

### 管理 Pod

Pod 的生命周期是短暂的，用后即焚的实体。当 Pod 被创建后（不论是由你直接创建还是被其他 Controller），都会被 Kubernetes 调度到集群的 Node 上。直到 Pod 的进程终止、被删掉、因为缺少资源而被驱逐、或者 Node 故障之前这个 Pod 都会一直保持在那个 Node 上。

> 注意：重启 Pod 中的容器跟重启 Pod 不是一回事。Pod 只提供容器的运行环境并保持容器的运行状态，重启容器不会造成 Pod 重启。

`Pod 不会自愈`。如果 Pod 运行的 Node 故障，或者是调度器本身故障，这个 Pod 就会被删除。同样的，如果 Pod 所在 Node 缺少资源或者 Pod 处于维护状态，Pod 也会被驱逐。Kubernetes 使用更高级的称为 Controller 的抽象层，来管理 Pod 实例。虽然可以直接使用 Pod，但是在 Kubernetes 中通常是使用 Controller 来管理 Pod 的。

#### Pod 和 Controller

Controller 可以创建和管理多个 Pod，提供副本管理、滚动升级和集群级别的自愈能力。例如，如果一个 Node 故障，Controller 就能自动将该节点上的 Pod 调度到其他健康的 Node 上。

包含一个或者多个 Pod 的 Controller 示例：

- Deployment
- StatefulSet
- DaemonSet

Pod Template 是包含了其他 object 的 Pod 定义，例如 Replication Controllers，Jobs 和 DaemonSets，Controller 会用你提供的 Pod Template 来创建相应的 Pod。

#### Pod 的终止

因为 Pod 作为在集群的节点上运行的进程，所以在不再需要的时候能够优雅的终止掉是十分必要的（比起使用发送 KILL 信号这种暴力的方式）。用户需要能够发起一个删除 Pod 的请求，并且知道它们何时会被终止，是否被正确的删除。用户想终止程序时发送删除 pod 的请求，在 pod 可以被强制删除前会有一个宽限期，会发送一个 TERM 请求到每个容器的主进程。一旦超时，将向主进程发送 KILL 信号并从 API server 中删除。如果 kubelet 或者 container manager 在等待进程终止的过程中重启，在重启后仍然会重试完整的宽限期。

示例流程如下：

- 用户发送删除 pod 的命令，默认宽限期是 30 秒；
- 在 Pod 超过该宽限期后 API server 就会更新 Pod 的状态为 “dead”；
- 在客户端命令行上显示的 Pod 状态为 “terminating”；
- 跟第三步同时，当 kubelet 发现 pod 被标记为 “terminating” 状态时，开始停止 pod 进程：
    - 如果在 pod 中定义了 preStop hook，在停止 pod 前会被调用。如果在宽限期过后，preStop hook 依然在运行，第二步会再增加 2 秒的宽限期；
    - 向 Pod 中的进程发送 TERM 信号；
- 跟第三步同时，该 Pod 将从该 service 的端点列表中删除，不再是 replication controller 的一部分。关闭的慢的 pod 将继续处理 load balancer 转发的流量；
- 过了宽限期后，将向 Pod 中依然运行的进程发送 SIGKILL 信号而杀掉进程。
- Kubelet 会在 API server 中完成 Pod 的的删除，通过将优雅周期设置为 0（立即删除）。Pod 在 API 中消失，并且在客户端也不可见。

删除宽限期默认是 30 秒。 `kubectl delete` 命令支持 `—grace-period=<seconds>` 选项，允许用户设置自己的宽限期。如果设置为 0 将强制删除 Pod。在 kubectl >= 1.5 版本的命令中，你必须同时使用 `--force` 和 `--grace-period=0` 来强制删除 Pod。在 yaml 文件中可以通过 `{{ .spec.spec.terminationGracePeriodSeconds }}` 来修改此值。

> Pod 的强制删除是通过在集群和 etcd 中将其定义为删除状态。当执行强制删除命令时，API server 不会等待该 Pod 所运行在节点上的 kubelet 确认，就会立即将该 Pod 从 API server 中移除，这时就可以创建跟原 Pod 同名的 Pod 了。这时，在节点上的 Pod 会被立即设置为 terminating 状态，不过在被强制删除之前依然有一小段优雅删除周期。

### Init 容器

Pod 能够具有多个容器，应用运行在容器里面，但是它也可能有一个或多个先于应用容器启动的 Init 容器。

Init 容器与普通的容器非常像，除了如下两点：

- Init 容器总是运行到成功完成为止。
- 每个 Init 容器都必须在下一个 Init 容器启动之前成功完成。

如果 Pod 的 Init 容器失败，Kubernetes 会不断地重启该 Pod，直到 Init 容器成功为止。然而，如果 Pod 对应的 restartPolicy 为 Never，它不会重新启动。

指定容器为 Init 容器，在 PodSpec 中添加 initContainers 字段，以 v1.Container 类型对象的 JSON 数组的形式，还有 app 的 containers 数组。 Init 容器的状态在 status.initContainerStatuses 字段中以容器状态数组的格式返回（类似 status.containerStatuses 字段）。

> 与普通容器的不同，Init 容器对资源请求和限制的处理稍有不同，而且 Init 容器不支持 Readiness Probe，因为它们必须在 Pod 就绪之前运行完成。如果为一个 Pod 指定了多个 Init 容器，那些容器会按顺序一次运行一个。只有当前面的 Init 容器必须运行成功后，才可以运行下一个 Init 容器。当所有的 Init 容器运行完成后，Kubernetes 才初始化 Pod 和运行应用容器。

因为 Init 容器具有与应用程序容器分离的单独镜像，所以它们的启动相关代码具有如下优势：

- 它们可以包含并运行实用工具，但是出于安全考虑，是不建议在应用程序容器镜像中包含这些实用工具的。
- 它们可以包含使用工具和定制化代码来安装，但是不能出现在应用程序镜像中。例如，创建镜像没必要 FROM 另一个镜像，只需要在安装过程中使用类似 sed、 awk、 python 或 dig 这样的工具。
- 应用程序镜像可以分离出创建和部署的角色，而没有必要联合它们构建一个单独的镜像。
- Init 容器使用 Linux Namespace，所以相对应用程序容器来说具有不同的文件系统视图。因此，它们能够具有访问 Secret 的权限，而应用程序容器则不能。
- 它们必须在应用程序容器启动之前运行完成，而应用程序容器是并行运行的，所以 Init 容器能够提供了一种简单的阻塞或延迟应用容器的启动的方法，直到满足了一组先决条件。

#### 具体行为

在 Pod 启动过程中，Init 容器会按顺序在网络和数据卷初始化之后启动。每个容器必须在下一个容器启动之前成功退出。如果由于运行时或失败退出，将导致容器启动失败，它会根据 Pod 的 restartPolicy 指定的策略进行重试。然而，如果 Pod 的 restartPolicy 设置为 Always，Init 容器失败时会使用 RestartPolicy 策略。

在所有的 Init 容器没有成功之前，Pod 将不会变成 Ready 状态。Init 容器的端口将不会在 Service 中进行聚集。 正在初始化中的 Pod 处于 Pending 状态，但应该会将 Initializing 状态设置为 true。

如果 Pod 重启，所有 Init 容器必须重新执行。

对 Init 容器 spec 的修改被限制在容器 image 字段，修改其他字段都不会生效。更改 Init 容器的 image 字段，等价于重启该 Pod。

因为 Init 容器可能会被重启、重试或者重新执行，所以 Init 容器的代码应该是幂等的。特别地当写到 EmptyDirs 文件中的代码，应该对输出文件可能已经存在做好准备。

Init 容器具有应用容器的所有字段。除了 readinessProbe，因为 Init 容器无法定义不同于完成（completion）的就绪（readiness）之外的其他状态。这会在验证过程中强制执行。

在 Pod 上使用 activeDeadlineSeconds，在容器上使用 livenessProbe，这样能够避免 Init 容器一直失败。 这就为 Init 容器活跃设置了一个期限。

在 Pod 中的每个 app 和 Init 容器的名称必须唯一；与任何其它容器共享同一个名称，会在验证时抛出错误。