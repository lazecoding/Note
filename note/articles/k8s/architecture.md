# Kubernetes 架构

- 目录
  - [Borg 架构](#Borg-架构)
  - [Kubernetes 架构](#Kubernetes-架构)
    - [分层架构](#分层架构)
    - [API 设计原则](#API-设计原则)
    - [控制机制设计原则](#控制机制设计原则)
  - [核心组件](#核心组件)
  - [总结](#总结)

Kubernetes 最初源于谷歌内部的 Borg，提供了面向应用的容器集群部署和管理系统。Kubernetes 的目标旨在消除编排物理/虚拟计算，网络和存储基础设施的负担，并使应用程序运营商和开发人员完全将重点放在以容器为中心的原语上进行自助运营。Kubernetes 也提供稳定、兼容的基础（平台），用于构建定制化的 workflows 和更高级的自动化任务。 Kubernetes 具备完善的集群管理能力，包括多层次的安全防护和准入机制、多租户应用支撑能力、透明的服务注册和服务发现机制、内建负载均衡器、故障发现和自我修复能力、服务滚动升级和在线扩容、可扩展的资源自动调度机制、多粒度的资源配额管理能力。Kubernetes 还提供完善的管理工具，涵盖开发、部署测试、运维监控等各个环节。

### Borg 架构

Borg 是谷歌内部的大规模集群管理系统，负责对谷歌内部很多核心服务的调度和管理。Borg 的目的是让用户能够不必操心资源管理的问题，让他们专注于自己的核心业务，并且做到跨多个数据中心的资源利用率最大化。

Borg 主要由 BorgMaster、Borglet、borgcfg 和 Scheduler 组成，如下图所示

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/k8s/Borg架构图.png" width="600px">
</div>

- BorgMaster 是整个集群的大脑，负责维护整个集群的状态，并将数据持久化到 Paxos 存储中；
- Scheduer 负责任务的调度，根据应用的特点将其调度到具体的机器上去；
- Borglet 负责真正运行任务（在容器中）；
- borgcfg 是 Borg 的命令行工具，用于跟 Borg 系统交互，一般通过一个配置文件来提交任务。

### Kubernetes 架构

Kubernetes 借鉴了 Borg 的设计理念，比如 Pod、Service、Label 和单 Pod 单 IP 等。Kubernetes 的整体架构跟 Borg 非常像，如下图所示。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/k8s/Kubernetes架构.png" width="600px">
</div>

Kubernetes 主要由以下几个核心组件组成：

- etcd 保存了整个集群的状态。`etcd 是一个高度一致的分布式键值存储，它提供了一种可靠的方式来存储需要由分布式系统或机器集群访问的数据`。
- apiserver 提供了资源操作的 `唯一入口`，并提供认证、授权、访问控制、API 注册和发现等机制。
- controller manager 负责维护集群的状态，比如故障检测、自动扩展、滚动更新等。
- scheduler 负责资源的调度，按照预定的调度策略将 Pod 调度到相应的机器上。
- kubelet 负责维护容器的生命周期，同时也负责 Volume（CSI）和网络（CNI）的管理。
- Container runtime 负责镜像管理以及 Pod 和容器的真正运行（CRI）。
- kube-proxy 负责为 Service 提供 cluster 内部的服务发现和负载均衡。

除了核心组件，还有一些推荐的插件，其中有的已经成为 CNCF 中的托管项目：

- CoreDNS 负责为整个集群提供 DNS 服务。
- Ingress Controller 为服务提供外网入口。
- Prometheus 提供资源监控。
- Dashboard 提供 GUI。
- Federation 提供跨可用区的集群。

下图清晰表明了 Kubernetes 的架构设计以及组件之间的通信协议。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/k8s/Kubernetes组件通信架构图.png" width="600px">
</div>

可以看到，Kubernetes 是 Master 和 Node 架构。

整体架构：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/k8s/kubernetes-whole-arch.png" width="600px">
</div>

Master 架构：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/k8s/kubernetes-master-arch.png" width="600px">
</div>

Node 架构：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/k8s/kubernetes-node-arch.png" width="600px">
</div>

#### 分层架构

Kubernetes 设计理念和功能其实就是一个类似 Linux 的分层架构，如下图所示。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/k8s/kubernetes-layers-arch.png" width="600px">
</div>

汉译：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/k8s/kubernetes-layers-arch-CN.png" width="600px">
</div>

- 云原生生态系统：在接口层之上的庞大容器集群管理调度的生态系统，可以划分为两个范畴。
  - Kubernetes 外部：日志、监控、配置管理、CI/CD、Workflow、FaaS、OTS 应用、ChatOps、GitOps、SecOps 等。
  - Kubernetes 内部：CRI、CNI、CSI、镜像仓库、Cloud Provider、集群自身的配置和管理等。
- 接口层：kubectl 命令行工具、客户端 SDK 以及集群联邦。
- 管理层：系统度量（如基础设施、容器和网络的度量），自动化（如自动扩展、动态 Provision 等）以及策略管理（RBAC、Quota、PSP、NetworkPolicy 等）、Service Mesh（部分位于管理层）。
- 应用层：部署（无状态应用、有状态应用、批处理任务、集群应用等）和路由（服务发现、DNS 解析等）、Service Mesh（部分位于应用层）。
- 核心层：Kubernetes 最核心的功能，对外提供 API 构建高层的应用，对内提供插件式应用执行环境。

#### API 设计原则

对于云计算系统，系统 API 实际上处于系统设计的统领地位，正如本文前面所说，Kubernetes 集群系统每支持一项新功能，引入一项新技术，一定会新引入对应的 API 对象，支持对该功能的管理操作，理解掌握的 API，就好比抓住了 Kubernetes 系统的牛鼻子。Kubernetes 系统 API 的设计有以下几条原则：

- `所有 API 应该是声明式的。` 正如前文所说，声明式的操作，相对于命令式操作，对于重复操作的效果是稳定的，这对于容易出现数据丢失或重复的分布式环境来说是很重要的。另外，声明式操作更容易被用户使用，可以使系统向用户隐藏实现的细节，隐藏实现的细节的同时，也就保留了系统未来持续优化的可能性。此外，声明式的 API，同时隐含了所有的 API 对象都是名词性质的，例如 Service、Volume 这些 API 都是名词，这些名词描述了用户所期望得到的一个目标分布式对象。
- `API 对象是彼此互补而且可组合的。` 这里面实际是鼓励 API 对象尽量实现面向对象设计时的要求，即 “高内聚，松耦合”，对业务相关的概念有一个合适的分解，提高分解出来的对象的可重用性。事实上，Kubernetes 这种分布式系统管理平台，也是一种业务系统，只不过它的业务就是调度和管理容器服务。
- `高层 API 以操作意图为基础设计。` 如何能够设计好 API，跟如何能用面向对象的方法设计好应用系统有相通的地方，高层设计一定是从业务出发，而不是过早的从技术实现出发。因此，针对 Kubernetes 的高层 API 设计，一定是以 Kubernetes 的业务为基础出发，也就是以系统调度管理容器的操作意图为基础设计。
- `低层 API 根据高层 API 的控制需要设计。` 设计实现低层 API 的目的，是为了被高层 API 使用，考虑减少冗余、提高重用性的目的，低层 API 的设计也要以需求为基础，要尽量抵抗受技术实现影响的诱惑。
- `尽量避免简单封装，不要有在外部 API 无法显式知道的内部隐藏的机制。` 简单的封装，实际没有提供新的功能，反而增加了对所封装 API 的依赖性。内部隐藏的机制也是非常不利于系统维护的设计方式，例如 StatefulSet 和 ReplicaSet，本来就是两种 Pod 集合，那么 Kubernetes 就用不同 API 对象来定义它们，而不会说只用同一个 ReplicaSet，内部通过特殊的算法再来区分这个 ReplicaSet 是有状态的还是无状态。
- `API 操作复杂度与对象数量成正比。` 这一条主要是从系统性能角度考虑，要保证整个系统随着系统规模的扩大，性能不会迅速变慢到无法使用，那么最低的限定就是 API 的操作复杂度不能超过 O(N)，N 是对象的数量，否则系统就不具备水平伸缩性了。
- `API 对象状态不能依赖于网络连接状态。` 由于众所周知，在分布式环境下，网络连接断开是经常发生的事情，因此要保证 API 对象状态能应对网络的不稳定，API 对象的状态就不能依赖于网络连接状态。
- `尽量避免让操作机制依赖于全局状态，因为在分布式系统中要保证全局状态的同步是非常困难的。`

#### 控制机制设计原则

- `控制逻辑应该只依赖于当前状态。` 这是为了保证分布式系统的稳定可靠，对于经常出现局部错误的分布式系统，如果控制逻辑只依赖当前状态，那么就非常容易将一个暂时出现故障的系统恢复到正常状态，因为你只要将该系统重置到某个稳定状态，就可以自信的知道系统的所有控制逻辑会开始按照正常方式运行。
- `假设任何错误的可能，并做容错处理。` 在一个分布式系统中出现局部和临时错误是大概率事件。错误可能来自于物理系统故障，外部系统故障也可能来自于系统自身的代码错误，依靠自己实现的代码不会出错来保证系统稳定其实也是难以实现的，因此要设计对任何可能错误的容错处理。
- `尽量避免复杂状态机，控制逻辑不要依赖无法监控的内部状态。` 因为分布式系统各个子系统都是不能严格通过程序内部保持同步的，所以如果两个子系统的控制逻辑如果互相有影响，那么子系统就一定要能互相访问到影响控制逻辑的状态，否则，就等同于系统里存在不确定的控制逻辑。
- `假设任何操作都可能被任何操作对象拒绝，甚至被错误解析。` 由于分布式系统的复杂性以及各子系统的相对独立性，不同子系统经常来自不同的开发团队，所以不能奢望任何操作被另一个子系统以正确的方式处理，要保证出现错误的时候，操作级别的错误不会影响到系统稳定性。
- `每个模块都可以在出错后自动恢复。` 由于分布式系统中无法保证系统各个模块是始终连接的，因此每个模块要有自我修复的能力，保证不会因为连接不到其他模块而自我崩溃。
- `每个模块都可以在必要时优雅地降级服务。` 所谓优雅地降级服务，是对系统鲁棒性的要求，即要求在设计实现模块时划分清楚基本功能和高级功能，保证基本功能不会依赖高级功能，这样同时就保证了不会因为高级功能出现故障而导致整个模块崩溃。根据这种理念实现的系统，也更容易快速地增加新的高级功能，因为不必担心引入高级功能影响原有的基本功能。

### 核心组件

API 对象是 Kubernetes 集群中的管理操作单元。Kubernetes 集群系统每支持一项新功能，引入一项新技术，一定会新引入对应的 API 对象，支持对该功能的管理操作。例如副本集 Replica Set 对应的 API 对象是 RS。

每个 API 对象都有 3 大类属性：元数据 metadata、规范 spec 和状态 status。

- 元数据是用来标识 API 对象的，每个对象都至少有 3 个元数据：namespace，name 和 uid；除此以外还有各种各样的标签 labels 用来标识和匹配不同的对象，例如用户可以用标签 env 来标识区分不同的服务部署环境，分别用 env=dev、env=testing、env=production 来标识开发、测试、生产的不同服务。
- 规范描述了用户期望 Kubernetes 集群中的分布式系统达到的理想状态（Desired State），例如用户可以通过复制控制器 Replication Controller 设置期望的 Pod 副本数为 3。
- status 描述了系统实际当前达到的状态（Status），例如系统当前实际的 Pod 副本数为 2；那么复制控制器当前的程序逻辑就是自动启动新的 Pod，争取达到副本数为 3。

Kubernetes 中所有的配置都是通过 API 对象的 spec 去设置的，也就是用户通过配置系统的理想状态来改变系统，这是 Kubernetes 重要设计理念之一，即所有的操作都是声明式（Declarative）的而不是命令式（Imperative）的。声明式操作在分布式系统中的好处是稳定，不怕丢操作或运行多次，例如设置副本数为 3 的操作运行多次也还是一个结果，而给副本数加 1 的操作就不是声明式的，运行多次结果就错了。

### 总结

我们可以看到 Kubernetes 系统最核心的两个设计理念：一个是 容错性，一个是 易扩展性。容错性实际是保证 Kubernetes 系统稳定性和安全性的基础，易扩展性是保证 Kubernetes 对变更友好，可以快速迭代增加新功能的基础。

按照分布式系统一致性算法 Paxos 发明人计算机科学家 Leslie Lamport 的理念，一个分布式系统有两类特性：安全性 Safety 和活性 Liveness。安全性保证系统的稳定，保证系统不会崩溃，不会出现业务错误，不会做坏事，是严格约束的；活性使得系统可以提供功能，提高性能，增加易用性，让系统可以在用户 “看到的时间内” 做些好事，是尽力而为的。Kubernetes 系统的设计理念正好与 Lamport 安全性与活性的理念不谋而合，也正是因为 Kubernetes 在引入功能和技术的时候，非常好地划分了安全性和活性，才可以让 Kubernetes 能有这么快版本迭代，快速引入像 RBAC、Federation 和 PetSet 这种新功能。