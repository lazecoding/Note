# 抛弃 ZooKeeper 的 Kafka

在 Kafka 2.8 版本里，用户可在完全不需要 ZooKeeper 的情况下运行 Kafka，该版本将依赖于 ZooKeeper 的控制器改造成了基于 Kafka Raft 的 Quorm 控制器。

在之前的版本中，如果没有 ZooKeeper，Kafka 将无法运行。但管理部署两个不同的系统不仅让运维复杂度翻倍，还让 Kafka 变得沉重，进而限制了 Kafka 在轻量环境下的应用，
同时 ZooKeeper 的分区特性也限制了 Kafka 的承载能力。

这是一次架构上的重大升级，让一向“重量级”的 Kafka 从此变得简单了起来。轻量级的单进程部署可以作为 ActiveMQ 或 RabbitMQ 等的替代方案，同时也适合于边缘场景和使用轻量级硬件的场景。

### 为什么要抛弃 ZooKeeper

ZooKeeper 是 Hadoop 的一个子项目，一般用来管理较大规模、结构复杂的服务器集群，具有自己的配置文件语法、管理工具和部署模式。Kafka 最初由 LinkedIn 开发，随后于 2011 年初开源，2014 年由主创人员组建企业 Confluent。

Broker 是 Kafka 集群的骨干，负责从生产者（producer）到消费者（consumer）的接收、存储和发送消息。在当前架构下，Kafka 进程在启动的时候需要往 ZooKeeper 集群中注册一些信息，比如 BrokerId，并组建集群。ZooKeeper 为 Kafka 提供了可靠的元数据存储，比如 Topic/分区的元数据、Broker 数据、ACL 信息等等。

同时 ZooKeeper 充当 Kafka 的领导者，以更新集群中的拓扑更改；根据 ZooKeeper 提供的通知，生产者和消费者发现整个 Kafka 集群中是否存在任何新 Broker 或 Broker 失败。大多数的运维操作，比如说扩容、分区迁移等等，都需要和 ZooKeeper 交互。

也就是说，Kafka 代码库中有很大一部分是负责实现在集群中多个 Broker 之间分配分区（即日志）、分配领导权、处理故障等分布式系统的功能。而早已经过业界广泛使用和验证过的 ZooKeeper 是分布式代码工作的关键部分，假设没有 ZooKeeper 的话，Kafka 甚至无法启动进程。

但严重依赖 ZooKeeper，也给 Kafka 带来了制约。Kafka 一路发展过来，绕不开的两个话题就是集群运维的复杂度以及单集群可承载的分区规模。

首先从集群运维的角度来看，Kafka 本身就是一个分布式系统。但它又依赖另一个开源的分布式系统，而这个系统又是 Kafka 系统本身的核心。这就要求集群的研发和维护人员需要同时了解这两个开源系统，需要对其运行原理以及日常的运维（比如参数配置、扩缩容、监控告警等）都有足够的了解和运营经验。否则在集群出现问题的时候无法恢复，是不可接受的。所以，ZooKeeper 的存在增加了运维的成本。

其次从集群规模的角度来看，限制 Kafka 集群规模的一个核心指标就是集群可承载的分区数。集群的分区数对集群的影响主要有两点：ZooKeeper 上存储的元数据量和控制器变动效率。

Kafka 集群依赖于一个单一的 Controller 节点来处理绝大多数的 ZooKeeper 读写和运维操作，并在本地缓存所有 ZooKeeper 上的元数据。分区数增加，ZooKeeper 上需要存储的元数据就会增加，从而加大 ZooKeeper 的负载，给 ZooKeeper 集群带来压力，可能导致 Watch 的延时或丢失。

当 Controller 节点出现变动时，需要进行 Leader 切换、Controller 节点重新选举等行为，分区数越多需要进行越多的 ZooKeeper 操作：比如当一个 Kafka 节点关闭的时候，Controller 需要通过写 ZooKeeper 将这个节点的所有 Leader 分区迁移到其他节点；新的 Controller 节点启动时，首先需要将所有 ZooKeeper 上的元数据读进本地缓存，分区越多，数据量越多，故障恢复耗时也就越长。

### 去除 ZooKeeper 后的 Kafka

为了改善 Kafka，Confluent 开始重写 ZooKeeper 功能，将这部分代码集成到了 Kafka 内部：将元数据存储在 Kafka 本身，而不是存储 ZooKeeper 这样的外部系统中。 Quorum 控制器使用新的 KRaft 协议来确保元数据在仲裁中被精确地复制。这个协议在很多方面与 ZooKeeper 的 ZAB 协议和 Raft 相似。这意味着，仲裁控制器在成为活动状态之前不需要从 ZooKeeper 加载状态。当领导权发生变化时，新的活动控制器已经在内存中拥有所有提交的元数据记录。

在架构改进之前，一个最小的分布式 Kafka 集群也需要六个异构的节点：三个 ZooKeeper 节点，三个 Kafka 节点。而一个最简单的 Quickstart 演示也需要先启动一个 ZooKeeper 进程，然后再启动一个 Kafka 进程。在新的 KIP-500 版本中，一个分布式 Kafka 集群只需要三个节点，而 Quickstart 演示只需要一个 Kafka 进程就可以。

在此之前，元数据管理一直是集群范围限制的主要瓶颈。特别是在集群规模比较大的时候，如果出现 Controller 节点失败涉及到的选举、Leader 分区迁移，以及将所有 ZooKeeper 的元数据读进本地缓存的操作，所有这些操作都会受限于单个 Controller 的读写带宽。因此一个 Kafka 集群可以管理的分区总数也会受限于这单个 Controller 的效率。

改进后的 Kafka 同时提高了集群的延展性（scalability），大大增加了 Kafka 单集群可承载的分区数量。