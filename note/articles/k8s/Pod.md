# Pod

- 目录
  - [管理 Pod](#管理-Pod)
    - [Pod 和 Controller](#Pod-和-Controller)
    - [Pod 的终止](#Pod-的终止)
  - [Init 容器](#Init-容器)
    - [具体行为](#具体行为)
  - [Pause 容器](#Pause-容器)
  - [Ephemeral 容器](#Ephemeral-容器)
  - [安全策略](#安全策略)
  - [生命周期](#生命周期)
    - [Pod 阶段](#Pod-阶段)
    - [Pod 状态](#Pod-状态)
  - [Pod 容器](#Pod-容器)
    - [容器状态](#容器状态)
    - [容器重启策略](#容器重启策略)
    - [容器探针](#容器探针)

Pod 是 kubernetes 中你可以创建和部署的最小也是最简的单位。Pod 代表着集群中运行的进程。

Pod 中封装着应用的容器（有的情况下是好几个容器），存储、独立的网络 IP，管理容器如何运行的策略选项。Pod 代表着部署的一个单位：kubernetes 中应用的一个实例，可能由一个或者多个容器组合在一起共享资源。Pod 中的容器总是被同时调度，有共同的运行环境。你可以把单个 Pod 想象成是运行独立应用的 "逻辑主机" —— 其中运行着一个或者多个紧密耦合的应用容器 —— 在有容器之前，这些应用都是运行在几个相同的物理机或者虚拟机上。

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
- 在 Pod 超过该宽限期后 API server 就会更新 Pod 的状态为 "dead"；
- 在客户端命令行上显示的 Pod 状态为 terminating"；
- 跟第三步同时，当 kubelet 发现 pod 被标记为 "terminating" 状态时，开始停止 pod 进程：
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

### Pause 容器

容器之间原本是被 Linux 的 Namespace 和 cgroups 隔开的，所以 Kubernetes 实际要解决的是怎么去打破这个隔离，然后共享某些事情和某些信息，这就是 Pod 的设计要解决的核心问题所在。

具体的解法分为两个部分：网络和存储，而 `Pause 容器` 就是为解决 Pod 中的网络问题而产生的。

Pause 容器，又叫 Infra 容器，它与用户容器 "捆绑" 运行在同一个 Pod 中。在 Kubernetes 里的解法是这样的：它会在每个 Pod 里，额外起一个 Pause container 小容器来共享整个 Pod 的 Network Namespace。所以说一个 Pod 里面的所有容器，它们看到的网络视图是完全一样的。即：它们看到的网络设备、IP 地址、Mac 地址等等，跟网络相关的信息，其实全是一份，这一份都来自于 Pod 第一次创建的这个 Pause container。也因此整个 Pod 中必然是 Pause container 第一个启动，并且整个 Pod 的生命周期是等同于 Pause container 的生命周期的。这也是为什么在 Kubernetes 里面，它是允许去单独更新 Pod 里的某一个镜像的，即：做这个操作，整个 Pod 不会重建，也不会重启，这是非常重要的一个设计。


从网络的角度看，同一个 Pod 中的不同容器犹如在运行在同一个专有主机上，可以通过 localhost 进行通信。原则上，任何人都可以配置 Docker 来控制容器组之间的共享级别——你只需创建一个父容器，并创建与父容器共享资源的新容器，然后管理这些容器的生命周期。在 Kubernetes 中，Pause 容器被当作 Pod 中所有容器的 "父容器" 并为每个业务容器提供以下功能：

kubernetes 中的 Pause 容器主要为每个业务容器提供以下功能：

- 在 Pod 中担任 Linux 命名空间共享的基础。
- 启用 pid 命名空间，开启 init 进程。

Pause 的功能很轻量，它是一个非常小的镜像，大概 700KB 左右，是一个 C 语言写的、永远处于 "暂停" 状态的容器。

Pause container 源码：

```C
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#define STRINGIFY(x) #x
#define VERSION_STRING(x) STRINGIFY(x)

#ifndef VERSION
#define VERSION HEAD
#endif

static void sigdown(int signo) {
  psignal(signo, "Shutting down, got signal");
  exit(0);
}

static void sigreap(int signo) {
  while (waitpid(-1, NULL, WNOHANG) > 0)
    ;
}

int main(int argc, char **argv) {
  int i;
  for (i = 1; i < argc; ++i) {
    if (!strcasecmp(argv[i], "-v")) {
      printf("pause.c %s\n", VERSION_STRING(VERSION));
      return 0;
    }
  }

  if (getpid() != 1)
    /* Not an error because pause sees use outside of infra containers. */
    fprintf(stderr, "Warning: pause should be the first process\n");

  if (sigaction(SIGINT, &(struct sigaction){.sa_handler = sigdown}, NULL) < 0)
    return 1;
  if (sigaction(SIGTERM, &(struct sigaction){.sa_handler = sigdown}, NULL) < 0)
    return 2;
  if (sigaction(SIGCHLD, &(struct sigaction){.sa_handler = sigreap,
                                             .sa_flags = SA_NOCLDSTOP},
                                             NULL) < 0)
    return 3;

  for (;;)
    pause();
  fprintf(stderr, "Error: infinite loop terminated\n");
  return 42;
}
```

### Ephemeral 容器

Ephemeral（临时）容器是一种特殊的容器，该容器在现有 Pod 中临时运行，以便完成用户发起的操作，例如故障排查。 你会使用临时容器来检查服务，而不是用它来构建应用程序。

临时容器与其他容器的不同之处在于，它们缺少对资源或执行的保证，并且永远不会自动重启， 因此不适用于构建应用程序。临时容器使用与常规容器相同的 ContainerSpec 节来描述，但许多字段是不兼容和不允许的。

- 临时容器没有端口配置，因此像 ports，livenessProbe，readinessProbe 这样的字段是不允许的。
- Pod 资源分配是不可变的，因此 resources 配置是不允许的。
- 有关允许字段的完整列表，请参见 [EphemeralContainer 参考文档](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.23/#ephemeralcontainer-v1-core) 。

临时容器是使用 API 中的一种特殊的 ephemeralcontainers 处理器进行创建的， 而不是直接添加到 pod.spec 段，因此无法使用 kubectl edit 来添加一个临时容器。与常规容器一样，将临时容器添加到 Pod 后，将不能更改或删除临时容器。

当由于容器崩溃或容器镜像不包含调试工具而导致 kubectl exec 无用时，临时容器对于交互式故障排查很有用。尤其是，Distroless 镜像允许用户部署最小的容器镜像，从而减少攻击面并减少故障和漏洞的暴露。由于 Distroless 镜像不包含 Shell 或任何的调试工具，因此很难单独使用 kubectl exec 命令进行故障排查。使用临时容器时，启用 `进程名字空间共享` 很有帮助，可以查看其他容器中的进程。

### 安全策略

PodSecurityPolicy 在 Kubernetes v1.21 版本中被弃用，将在 v1.25 中删除。关于弃用的更多信息，请查阅 [PodSecurityPolicy Deprecation: Past, Present, and Future](https://kubernetes.io/blog/2021/04/06/podsecuritypolicy-deprecation-past-present-and-future/) 。

### 生命周期

Pod 遵循一个预定义的生命周期，起始于 Pending 阶段，如果至少 其中有一个主要容器正常启动，则进入 Running，之后取决于 Pod 中是否有容器以 失败状态结束而进入 Succeeded 或者 Failed 阶段。在 Pod 运行期间，kubelet 能够重启容器以处理一些失效场景。在 Pod 内部，Kubernetes 跟踪不同容器的状态 并确定使 Pod 重新变得健康所需要采取的动作。

Pod 在其生命周期中只会被调度一次，一旦 Pod 被调度（分派）到某个节点，Pod 会一直在该节点运行，直到 Pod 停止或者被终止。

和一个个独立的应用容器一样，Pod 也被认为是相对临时性（而不是长期存在）的实体。Pod 会被创建、赋予一个唯一的 ID（UID），并被调度到节点，并在终止（根据重启策略）或删除之前一直运行在该节点。如果一个节点死掉了，调度到该节点的 Pod 也被计划在给定超时期限结束后删除。

Pod 自身不具有自愈能力。如果 Pod 被调度到某节点 而该节点之后失效，Pod 会被删除；类似地，Pod 无法在因节点资源耗尽或者节点维护而被驱逐期间继续存活。Kubernetes 使用一种高级抽象来管理这些相对而言可随时丢弃的 Pod 实例，称作控制器。

如果某物声称其生命期与某 Pod 相同，例如存储卷， 这就意味着该对象在此 Pod（UID 亦相同）存在期间也一直存在。如果 Pod 因为任何原因被删除，甚至某完全相同的替代 Pod 被创建时，这个相关的对象（例如这里的卷）也会被删除并重建。

#### Pod 阶段 

Pod 的 status 字段是一个 PodStatus 对象，其中包含一个 phase 字段。

Pod 的阶段（Phase）是 Pod 在其生命周期中所处位置的简单宏观概述。该阶段并不是对容器或 Pod 状态的综合汇总，也不是为了成为完整的状态机。

Pod 阶段的数量和含义是严格定义的。除了本文档中列举的内容外，不应该再假定 Pod 有其他的 phase 值。

| 取值	         | 描述                 |
| -------------  | -----------------   |
| Pending（悬决）  | Pod 已被 Kubernetes 系统接受，但有一个或者多个容器尚未创建亦未运行。此阶段包括等待 Pod 被调度的时间和通过网络下载镜像的时间。 |
| Running（运行中）| Pod 已经绑定到了某个节点，Pod 中所有的容器都已被创建。至少有一个容器仍在运行，或者正处于启动或重启状态。 |
| Succeeded（成功）| Pod 中的所有容器都已成功终止，并且不会再重启。  |
| Failed（失败）	 | Pod 中的所有容器都已终止，并且至少有一个容器是因为失败终止。也就是说，容器以非 0 状态退出或者被系统终止。 |
| Unknown（未知）	 | 因为某些原因无法取得 Pod 的状态。这种情况通常是因为与 Pod 所在主机通信失败。  |

如果某节点死掉或者与集群中其他节点失联，Kubernetes 会实施一种策略，将失去的节点上运行的所有 Pod 的 phase 设置为 Failed。

Pod 生命周期示意图如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/k8s/Pod生命周期示意图.png" width="600px">
</div>

#### Pod 状态

Pod 有一个 PodStatus 对象，其中包含一个 PodConditions 数组。PodCondition 数组的每个元素都有一个 type 字段和一个 status 字段。type 用于描述 Pod 状态；status 表明该状态是否适用，可能的取值有 "True", "False" 或 "Unknown"。

type 可能的值：

- PodScheduled：Pod 已经被调度到某节点；
- ContainersReady：Pod 中所有容器都已就绪；
- Initialized：所有的 Init 容器 都已成功启动；
- Ready：Pod 可以为请求提供服务，并且应该被添加到对应服务的负载均衡池中。

### Pod 容器

Kubernetes 会跟踪 Pod 中每个容器的状态，就像它跟踪 Pod 总体上的阶段一样。你可以使用容器 [生命周期回调](https://kubernetes.io/zh/docs/concepts/containers/container-lifecycle-hooks/) 来在容器生命周期中的特定时间点触发事件。

#### 容器状态

一旦调度器将 Pod 分派给某个节点，kubelet 就会为 Pod 创建容器。容器的状态有三种：Waiting（等待）、Running（运行中）和 Terminated（已终止）。

要检查 Pod 中容器的状态，你可以使用 `kubectl describe pod <pod 名称>`，其输出中包含 Pod 中每个容器的状态：

- `Waiting（等待）`：如果容器并不处在 Running 或 Terminated 状态之一，它就处在 Waiting 状态。处于 Waiting 状态的容器仍在运行它完成启动所需要的操作：例如，从某个容器镜像仓库拉取容器镜像，或者向容器应用 Secret 数据等等。当你使用 kubectl 来查询包含 Waiting 状态的容器的 Pod 时，你也会看到一个 Reason 字段，其中给出了容器处于等待状态的原因。
- `Running（运行中）`：Running 状态表明容器正在执行状态并且没有问题发生。如果配置了 postStart 回调，那么该回调已经执行且已完成。如果你使用 kubectl 来查询包含 Running 状态的容器的 Pod 时，你也会看到 关于容器进入 Running 状态的信息。
- `Terminated（已终止）`：处于 Terminated 状态的容器已经开始执行并且或者正常结束或者因为某些原因失败。如果你使用 kubectl 来查询包含 Terminated 状态的容器的 Pod 时，你会看到 容器进入此状态的原因、退出代码以及容器执行期间的起止时间。如果容器配置了 preStop 回调，则该回调会在容器进入 Terminated 状态之前执行。

#### 容器重启策略

Pod 的 spec 中包含一个 restartPolicy 字段，其可能取值包括 Always、OnFailure 和 Never。默认值是 Always。

restartPolicy 适用于 Pod 中的所有容器。restartPolicy 仅针对同一节点上 kubelet 的容器重启动作。当 Pod 中的容器退出时，kubelet 会按指数回退 方式计算重启的延迟（10s、20s、40s、...），其最长延迟为 5 分钟。一旦某容器执行了 10 分钟并且没有出现问题，kubelet 对该容器的重启回退计时器执行重置操作。

#### 容器探针

Probe（探针）是对容器执行的定期诊断，是由 kubelet 调用由容器实现的 Handler（处理程序）。有三种类型的处理程序：

- `ExecAction`：在容器内执行指定命令。如果命令退出时返回码为 0 则认为诊断成功。
- `TCPSocketAction`：对容器的 IP 地址上的指定端口执行 TCP 检查。如果端口打开，则诊断被认为是成功的。
- `HTTPGetAction`：对容器的 IP 地址上指定端口和路径执行 HTTP Get 请求。如果响应的状态码大于等于 200 且小于 400，则诊断被认为是成功的。

每次探测都将获得以下三种结果之一：

- `成功`：容器通过了诊断。
- `失败`：容器未通过诊断。
- `未知`：诊断失败，因此不会采取任何行动。

针对运行中的容器，kubelet 可以选择是否执行以下三种探针，以及如何针对探测结果作出反应：

- `livenessProbe`：指示容器是否正在运行。如果存活态探测失败，则 kubelet 会杀死容器，并且容器将根据其重启策略决定未来。如果容器不提供存活探针，则默认状态为 Success。
- `readinessProbe`：指示容器是否准备好为请求提供服务。如果就绪态探测失败，端点控制器将从与 Pod 匹配的所有服务的端点列表中删除该 Pod 的 IP 地址。初始延迟之前的就绪态的状态值默认为 Failure。如果容器不提供就绪态探针，则默认状态为 Success。
- `startupProbe`: 指示容器中的应用是否已经启动。如果提供了启动探针，则所有其他探针都会被 禁用，直到此探针成功为止。如果启动探测失败，kubelet 将杀死容器，而容器依其 重启策略进行重启。如果容器没有提供启动探测，则默认状态为 Success。

更多内容查看 [配置存活、就绪和启动探测器](https://kubernetes.io/zh/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/) 。