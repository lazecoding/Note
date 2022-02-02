# Pulsar 是什么

- 目录
    - [基本架构](#基本架构)
      - [Broker](#Broker)
      - [集群](#集群)
      - [元数据存储](#元数据存储)
      - [配置存储](#配置存储)
      - [持久化存储](#持久化存储)
        - [Apache BookKeeper](#Apache-BookKeeper)
        - [Ledgers](#Ledgers)
        - [日志存储](#日志存储)
      - [服务发现](#服务发现)
    - [Topic](#Topic)
      - [分区](#分区)
        - [路由模式](#路由模式)
        - [订阅模式](#订阅模式)
        - [顺序保证](#顺序保证)
      - [持久化](#持久化)
      - [消息重试](#消息重试)
      - [死信](#死信)
      - [消息保留和过期](#消息保留和过期)

Apache Pulsar 是 Apache 软件基金会顶级项目，是下一代云原生分布式消息流平台，集消息、存储、轻量化函数式计算为一体，采用计算与存储分离架构设计，支持多租户、持久化存储、多机房跨区域数据复制，具有强一致性、高吞吐、低延时及高可扩展性等流数据存储特性。

Pulsar 的关键特性如下：

- Pulsar 的单个实例原生支持多个集群，可跨机房在集群间无缝地完成消息复制。
- 极低的发布延迟和端到端延迟。
- 可无缝扩展到超过一百万个 Topic。
- 简单的客户端 API，支持 Java、Go、Python 和 C++。
- Topic 支持多种订阅模式。
- 通过 Apache BookKeeper 提供的持久化消息存储机制保证消息传递。
    - 由轻量级的 serverless 计算框架 Pulsar Functions 实现流原生的数据处理。
    - 基于 Pulsar Functions 的 serverless connector 框架 Pulsar IO 使得数据更易移入、移出 Apache Pulsar。
    - 分层式存储可在数据陈旧时，将数据从热存储卸载到冷/长期存储（如S3、GCS）中。
    
Pulsar 采用 `发布-订阅` 的设计模式(pub-sub)，在这种模式中，生产者向主题发布消息；消费者 订阅这些主题，处理传入的消息，并在处理完成后向 broker 发送确认。消息一旦创建订阅，即使 consumer 断开连接，Pulsar 仍然可以保存所有消息。
只有当消费者确认所有这些消息都已成功处理时，才会丢弃保留的消息。

### 基本架构

一个 Pulsar 集群由以下三部分组成：

- 一个或多个 broker 负责处理和负载均衡 Producer 发出的消息，并将这些消息分派给 Consumer；Broker 与 Pulsar 配置存储交互来处理相应的任务，并将消息存储在 BookKeeper 实例中（又称 bookies）；Broker 依赖 ZooKeeper 集群处理特定的任务，等等。
- 包含一个或多个 bookie 的 BookKeeper 集群负责消息的持久化存储。
- 一个 Zookeeper 集群，用来处理多个 Pulsar 集群之间的协调任务。

下图为 Pulsar 集群架构：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/Pulsar集群基础架构图.png" width="600px">
</div>

- Producer：生产者，即发送消息的一方。生产者负责创建消息，将其投递到 Pulsar 中。
- Consumer：消费者，即接收消息的一方。消费者连接到 Pulsar 并接收消息，进行相应的业务处理。
- Broker：无状态的服务层，负责接收消息、传递消息、集群负载均衡等操作，Broker 不会持久化保存元数据。
- BookKeeper：有状态的持久层，包含多个 Bookie，负责持久化地存储消息。
- ZooKeeper：存储 Pulsar、BookKeeper 的元数据，集群配置等信息，负责集群间的协调(例如：Topic 与 Broker 的关系)、服务发现等。

从 Pulsar 的架构图上可以看出，Pulsar 在架构设计上采用了计算与存储分离的模式，发布/订阅相关的计算逻辑在 Broker 上完成，而数据的持久化存储交由 BookKeeper 去实现。

#### Broker

Pulsar 的 broker 是一个无状态组件, 主要负责运行另外的两个组件:

- 一个 HTTP 服务器，它为生产者和消费者的管理任务和主题查找公开一个 REST API。生产者连接到代理来发布消息，消费者连接到代理来消费消息。
- 一个调度分发器, 它是异步的 TCP 服务器，通过自定义 二进制协议应用于所有相关的数据传输。

出于性能的考虑, 通常从 managed ledger (ledger是Pulsar底层存储BookKeeper中的概念，相当于一种记录的集合) 缓存中调度消息, 除非积压的消息超过这个缓存的大小。
如果积压的消息对于缓存来说太大了, 则 Broker 将开始从 BookKeeper 那里读取 Entries（Entry 同样是 BookKeeper 中的概念，相当于一条记录）。

最后，为了支持全局 Topic 异地复制，Broker 会控制 Replicators 追踪本地发布的条目，并把这些条目用 Java 客户端重新发布到其他区域。

#### 集群

Pulsar 的单个实例原生支持多个集群，单个 Pulsar 实例由一个或多个 Pulsar 集群组成，实例中的集群之间可以相互复制数据。

#### 元数据存储

Pulsar 元数据存储维护 Pulsar 集群的全部元数据，比如主题元数据、Schema、Broker 负载数据等等。Pulsar 使用 Apache ZooKeeper 进行元数据存储、集群配置和协调。
Pulsar 元数据存储可以部署在单独的 ZooKeeper 集群或者是部署在已有的 ZooKeeper 集群。你可以将 ZooKeeper 用作 Pulsar 元数据存储和 BookKeeper 元数据存储。如果想将部署的 Pulsar broker 连接到一个已有的 BookKeeper 集群，
你需要部署单独的 ZooKeeper 集群分别用作 Pulsar 元数据存储和 BookKeeper 元数据存储。

在 Pulsar 实例中：

- 配置存储 Quorum 存储了租户、命名空间和其他需要全局一致的配置项。
- 每个集群有自己独立的本地 ZooKeeper 保存集群内部配置和协调信息，例如 broker 负责哪几个主题及所有权归属元数据、broker 负载报告，BookKeeper ledger 元数据（这个是 BookKeeper 本身所依赖的）等等。

#### 配置存储

配置存储（Configuration Store）维护所有 Pulsar 集群的配置信息，比如集群、租户、命名空间、分区主题相关的配置等等。Pulsar 实例可能有一个本地集群、多个本地集群，或者多个跨区域集群。因此，配置存储可以在 Pulsar 实例下跨多个集群共享配置。
配置存储可以部署在单独的 ZooKeeper 集群或者是部署在已有的 ZooKeeper 集群。

#### 持久化存储

Pulsar 采用存储和计算分离的软件架构。发布/订阅相关的计算逻辑在 Broker 上完成，而数据的持久化存储交由 BookKeeper 去实现。这种架构为用户带来了更高的可用性、更灵活的扩容和管理、避免数据的 reblance 和 catch-up。

在 Apache Pulsar 的分层架构中，服务层 Broker 和存储层 BookKeeper 的每个节点都是对等的。Broker 仅仅负责消息的服务支持，不存储数据。这为服务层和存储层提供了瞬时的节点扩展和无缝的失效恢复。

##### Apache BookKeeper

Pulsar用 Apache BookKeeper 作为持久化存储。BookKeeper 是一个分布式的预写日志（WAL）系统，有如下几个特性特别适合 Pulsar 的应用场景：

- 能让 Pulsar 创建多个独立的日志，这种独立的日志就是 ledgers。随着时间的推移，Pulsar 会为 Topic 创建多个 ledgers。
- 为按条目复制的顺序数据提供了非常高效的存储。
- 保证了多系统挂掉时 ledgers 的读取一致性。
- 提供不同的 Bookies 之间均匀的 IO 分布的特性。
- 容量和吞吐量都能水平扩展。并且容量可以通过在集群内添加更多的 Bookies 立刻提升。
- Bookies 被设计成可以承载数千的并发读写的 ledgers。使用多个磁盘设备，一个用于日志，另一个用于一般存储，这样 Bookies 可以将读操作的影响和对于写操作的延迟分隔开。

除了消息数据，cursors 也会被持久化入 BookKeeper。Cursors 是消费端订阅消费的位置。BookKeeper 让 Pulsar 可以用一种可扩展的方式存储消费位置。

下图展示了 brokers 和 bookies 是如何交互的：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/brokers和bookies交互图.png" width="600px">
</div>
##### Ledgers

Ledger 是一个只追加的数据结构，并且只有一个写入器，这个写入器负责多个 BookKeeper 存储节点（就是 Bookies）的写入。Ledger 的条目会被复制到多个 Bookies。Ledgers 本身有着非常简单的语义：

- Pulsar Broker 可以创建 ledeger，添加内容到 ledger 和关闭 ledger。
- 当一个 ledger 被关闭后，除非明确的要写数据或者是因为写入器挂掉导致 ledger 关闭，这个 ledger 只会以只读模式打开。
- 最后，当 ledger 中的条目不再有用的时候，整个 legder 可以被删除（ledger 分布是跨 Bookies 的）。

BookKeeper 的主要优势在于他能在有系统故障时保证读的一致性。由于 ledger 只能被一个进程写入（之前提的写入器进程），这样这个进程在写入时不会有冲突，从而写入会非常高效。
在一次故障之后，ledger 会启动一个恢复进程来确定 ledger 的最终状态并确认最后提交到日志的是哪一个条目。在这之后，能保证所有的 ledger 读进程读取到相同的内容。

由于 BookKeeper Ledgers 提供了单一的日志抽象，在 ledger 的基础上开发了一个叫 managed ledger 的库，用以表示单个 topic 的存储层。managed ledger 即消息流的抽象，有一个写入器进程不断在流结尾添加消息，并且有多个 cursors 消费这个流，
每个 cursor 有自己的消费位置。

一个 managed ledger 在内部用多个 BookKeeper ledgers 保存数据，这么做有两个原因：

- 在故障之后，原有的某个 ledger 不能再写了，需要创建一个新的。
- ledger 在所有 cursors 消费完它所保存的消息之后就可以被删除，这样可以实现 ledgers 的定期翻滚从头写。

##### 日志存储

BookKeeper 的日志文件包含事务日志。在更新到 ledger 之前，bookie 需要确保描述这个更新的事务被写到持久（非易失）存储上面。在 bookie 启动和旧的日志文件大小达到上限（由 journalMaxSizeMB 参数配置）的时候，新的日志文件会被创建。

#### 服务发现

客户端可以使用单个 URL 与整个 Pulsar 实例进行通信。

### Topic

消息是 Pulsar 的基本单位，消息以 Topic 为单位进行归类。

#### 分区

普通的主题仅仅被保存在单个 broker 中，这限制了主题的最大吞吐量。分区实际是通过在底层拥有 N 个内部主题来实现的，这个 N 的数量就是等于分区的数量。当向分区的 topic 发送消息，每条消息被路由到其中一个 broker。Pulsar 自动处理跨 broker 的分区分布。

下图展示了分区的生产、消费逻辑：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/主题分区的生产消费模型.png" width="400px">
</div>

Topic1 有 5 个分区(P0 到 P4)，划分在 3 个 broker 上。因为分区多于 broker 数量，其中有两个 broker 要处理两个分区。第三个 broker 则只处理一个。（再次强调，分区的分布是 Pulsar 自动处理的）。

这个 topic 的消息被广播给两个 Consumer。路由模式决定将每个消息发布到哪个分区，而订阅类型决定将哪些消息发送到哪个使用者。

在大多数情况下，可以分别决定路由和订阅模式。通常来讲，吞吐能力的要求，决定了分区/路由的方式。订阅模式则应该由应用的语义来做决定。

##### 路由模式

当发布消息到分区 topic，你必须要指定路由模式，路由模式决定了每条消息被发布到的分区 -- 其实是内部主题。

有三种路由模式:

|  路由模式             | 说明  |
|  -----------------  | ---------------------------------------------------------------------------------------------------------------  |
| RoundRobinPartition | 如果消息没有指定 key，为了达到最大吞吐量，生产者会以 round-robin 方式将消息发布到所有分区。注意： round-robin 并不是作用于每条单独的消息，而是作用于延迟处理的批次边界，以确保批处理有效。如果消息指定了 key，分区生产者会根据 key 的 hash 值将该消息分配到对应的分区。这是默认的模式。  |
| SinglePartition	  | 如果消息没有指定 key，生产者将会随机选择一个分区，并发布所有消息到这个分区。如果消息指定了 key，分区生产者会根据 key 的 hash 值将该消息分配到对应的分区。  |
| CustomPartition     | 使用自定义消息路由器实现来决定特定消息的分区。用户可以创建自定义路由模式：使用 Java Client 并实现 MessageRouter 接口。  |

##### 订阅模式

订阅是命名好的配置规则，指导消息如何投递给消费者。Pulsar 中有四种订阅模式: 独占、灾备、共享和 key 共享。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/四种订阅模式总览.png" width="600px">
</div>

##### 顺序保证

消息的顺序与路由模式和 Message Key 相关。通常，用户想要 Per-key-partition 的排序保证。

当使用 SinglePartition 或者 RoundRobinPartition 模式时，如果消息有 key，消息将会被路由到匹配的分区，这是基于 ProducerBuilder 中 HashingScheme 指定的散列 shema。

| 顺序保证      | 说明	                                                       | 路由策略与消息 key                                                   |
| ----------  | ------------------------------------------------------------   | ----------------------------------------------------------------  |
| 按键分区      | 所有具有相同 key 的消息将按顺序排列并放置在相同的分区（Partition）中。	   | 使用 SinglePartition 或 RoundRobinPartition 模式，每条消息都需要有 key。|
| 生产者排序	  | 来自同一生产者的所有消息都是有序的	                                   | 路由策略为 SinglePartition, 且每条消息都没有 key。                      |

#### 持久化

默认的，Pulsar 保存所有没有确认的消息到多个 BookKeeper 的 bookies中（存储节点）。持久 Topic 的消息数据可以在 broker 重启或者订阅者出问题的情况下存活下来。因此，持久性主题上的消息数据可以在 broker 重启和订阅者故障转移之后继续存在。

Pulsar 还支持非持久性主题，这些主题的消息从不持久存储到磁盘，只存在于内存中。当使用非持久 Topic 分发时，杀掉 Pulsar 的 broker 或者关闭订阅者，此 Topic（ non-persistent)）上所有的瞬时消息都会丢失，意味着客户端可能会遇到消息缺失。

非持久 Topic 中，broker 会立即发布消息给所有连接的订阅者，而不会在 BookKeeper 中存储。如果有一个订阅者断开连接，broker 将无法重发这些瞬时消息，订阅者将永远也不能收到这些消息了。
去掉持久化存储的步骤，在某些情况下，使得非持久 Topic 的消息比持久 Topic 稍微变快。但是同时，Pulsar的一些核心优势也丧失掉了。

非持久消息传递通常比持久消息传递更快，因为 broker 不需要持久消息，并且在消息传递给连接的代理时立即将 ACK 发送回生产者。非持久 Topic 让 producer 有更低的发布延迟。

#### 消息重试

由于业务逻辑处理出现异常，消息一般需要被重新消费。Pulsar 支持生产者同时将消息发送到普通的 Topic 和重试 Topic，并指定允许延时和最大重试次数。当配置了允许消费者自动重试时，如果消息没有被消费成功，会被保存到重试 Topic 中，并在指定延时时间后，
重新被消费。

#### 死信

当 Consumer 消费消息出错时，可以通过配置重试 Topic 对消息进行重试，但是，如果当消息超过了最大的重试次数仍处理失败时，该怎么办呢？Pulsar 提供了死信 Topic，通过配置 deadLetterTopic，当消息达到最大重试次数的时候，
Pulsar 会将消息推送到死信 Topic 中进行保存。

#### 消息保留和过期

Pulsar broker 默认如下：

- 立即删除所有已经被 Consumer 确认过的的消息。
- 以消息 backlog 的形式，持久保存所有的未被确认消息。

Pulsar 有两个特性，可以覆盖上面的默认行为：

- 消息存留让你可以保存 Consumer 确认过的消息。
- 消息过期让你可以给未被确认的消息设置存活时长（TTL）。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/消息保留和过期示意图.png" width="600px">
</div>