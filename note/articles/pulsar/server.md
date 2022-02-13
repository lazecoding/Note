# 服务端

从 [基本架构](https://github.com/lazecoding/Note/blob/main/note/articles/pulsar/whatispulsar.md#基本架构) 
我们了解到，一个 Pulsar 集群由以下三部分组成：

- 一个或多个 broker 负责处理和负载均衡 Producer 发出的消息，并将这些消息分派给 Consumer；Broker 与 Pulsar 配置存储交互来处理相应的任务，并将消息存储在 BookKeeper 实例中（又称 bookies）；Broker 依赖 ZooKeeper 集群处理特定的任务，等等。
- 包含一个或多个 bookie 的 BookKeeper 集群负责消息的持久化存储。
- 一个 Zookeeper 集群，用来处理多个 Pulsar 集群之间的协调任务。

Pulsar 在架构设计上采用了计算与存储分离的模式，发布/订阅相关的计算逻辑在 Broker 上完成，而数据的持久化存储交由 BookKeeper 去实现。

### Broker

Pulsar 的 broker 是一个无状态组件, 主要负责运行另外的两个组件:

- 一个 HTTP 服务器，它为生产者和消费者的管理任务和主题查找公开一个 REST API。生产者连接到代理来发布消息，消费者连接到代理来消费消息。
- 一个调度分发器, 它是异步的 TCP 服务器，通过自定义 二进制协议应用于所有相关的数据传输。

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

#### 消息去重

消息去重保证了一条消息只能在 Pulsar 服务端被持久化一次。消息去重是一个 Pulsar 可选的特性，它能够阻止不必要的消息重复，它保证了即使消息被消费了多次，也只会被保存一次。

下图展示了开启和关闭消息去重的场景：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/消息去重开启和关闭.png" width="600px">
</div>

Pulsar 中启用消息去重，你必须同时配置 Pulsar broker 和客户端。[更多内容](https://pulsar.apache.org/docs/zh-CN/next/cookbooks-deduplication) 查看

Producer 对每一个发送的消息，都会采用递增的方式生成一个唯一的 sequence ID，这个消息会放在 message 的元数据中传递给 broker。同时，Broker 也会维护一个 PendingMessage 队列，当 broker 返回发送成功 ack 后，Producer 会将 PendingMessage 队列中的对应的 sequence ID 删除，表示 Producer 认为这个消息生产成功。broker 会记录针对每个从 Producer 接收到的最大 Sequence ID 和已经处理完的最大 Sequence ID。

当 broker 开启消息去重后，broker 会对每个消息请求进行是否去重的判断：根据收到的最新的 Sequence ID 是否大于 broker 端记录的两个维度的最大 Sequence ID。 如果大于则不重复，如果小于或等于则消息重复。消息重复时，broker 端会直接返回 ack，不会继续走后续的存储处理流程。

消息去重的另外一种方法是确保每条消息仅生产一次，这种方法通常被叫做 `生产者幂等`，这种方式的缺点是，把消息去重的工作推给了应用去做。

在 Pulsar 中，消息去重是在 broker 上处理的，用户不需要去修改客户端的代码，你只需要通过修改配置就可以实现。

消息去重，使 Pulsar 成为了流处理引擎（SPE）或者其他寻求 "仅仅一次" 语义的连接系统所需的理想消息系统。如果消息系统没有提供自动去重能力，那么 SPE (流处理引擎) 或者其他连接系统就必须自己实现去重语义，这意味着需要应用去承担这部分的去重工作。使用 Pulsar，严格的顺序保证不会带来任何应用层面的代价。

#### 消息延迟传递

延时消息功能允许 Consumer 能够在消息发送到 Topic 后过一段时间才能消费到这条消息。在这种机制中，消息存储在 BookKeeper 中，在消息发布到 broker 后，DelayedDeliveryTracker 在内存中维护时间索引(time -> messageId)，当到消息特定的延迟时间时，此消息将被传递给 Consumer。

延迟消息传递只适用于共享订阅类型，在独占订阅和故障转移订阅类型中，将立即分派延迟的消息。

延时消息的实现机制：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/延时消息的实现机制.png" width="600px">
</div>

Broker 保存消息是不经过任何检查的。 当消费者消费一条消息时，如果这条消息是延时消息，那么这条消息会被加入到 `DelayedDeliveryTracker` 当中。订阅检查机制会从 `DelayedDeliveryTracker` 获取到超时的消息，并交付给消费者。

在 Pulsar 中，可以通过两种方式实现延迟投递，分别为 deliverAfter 和 deliverAt。deliverAfter 可以指定具体的延迟时间戳，deliverAt 可以指定消息在多长时间后消费。两种方式本质时一样的，deliverAt 方式下，客户端会计算出具体的延迟时间戳发送给 broker。

`DelayedDeliveryTracker` 会记录所有需要延迟投递的消息的 index。index 由 Timestamp、Ledger ID、Entry ID 三部分组成，其中 Ledger ID 和 Entry ID 用于定位该消息，Timestamp 除了记录需要投递的时间，还用于延迟优先级队列排序。`DelayedDeliveryTracker` 会根据延迟时间对消息进行排序，延迟时间最短的放在前面。当 Consumer 在消费时，如果有到期的消息需要消费，则根据 `DelayedDeliveryTracker-index` 的 Ledger ID、Entry ID 找到对应的消息进行消费。

#### Bookkeeper

BookKeeper 是 Pulsar 的存储组件。对于 Pulsar 的每个 Topic（分区），其数据并不会固定的分配在某个 Bookie 上，而是通过 BookKeeper 组件来实现的。

#### 分片存储

概念：

- Bookie：BookKeeper 的一部分，处理需要持久化的数据。
- Ledger：BookKeeper 的存储逻辑单元，可用于追加写数据。
- Entry：写入 BookKeeper 的数据实体。当批量生产时，Entry 为多条消息，当非批量生产时，Entry 为单条数据。

Pulsar 在物理上采用分片存储的模式，存储粒度比分区更细化、存储负载更均衡。如图，一个分区 topic-partition2 的数据由多个分片组成。每个分片作为 BookKeeper 中的一个 Ledger，均匀的分布并存储在 BookKeeper 的多个 Bookie 节点中。在 broker中，消息以 Entry 的形式追加的形式写入 Ledger 中，每个 Topic 分区都有多个非连续 ID 的 Ledger，Topic 分区的 Ledger 同一时刻只有一个处于可写状态。

Topic 分区在存储消息时，会先找到当前使用的 Ledger，生成 Entry ID（每个 Entry ID 在同一个 Ledger 内是递增的）。当 Ledger 的长度或 Entry 个数超过阈值时，新消息会存储到新 Ledger 中。每个 messageID 由 [Ledger ID，Entry ID，Partition 编号，batch-index] 组成。（Partition：消息所属的 Topic 分区，batch-index：是否为批量消息）

一个 Ledger 会根据 Topic 指定的副本数量存储到多个 Bookie 中。一个 Bookie 可以存放多个不连续的 Ledger。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/Bookkeeper分片存储示意图.png" width="600px">
</div>

基于分片存储的机制，使得 Bookie 的扩容可以即时完成，无需任何数据复制或者迁移。当 Bookie 扩容时，Broker可以立刻发现并感知新的 Bookie，并尝试将新的分片 Segment 写入新增加的 Bookie 中。

> Pulsar 的分区 Topic 实际上是多个子 Topic，Bookkeeper 的存储维度即 Topic。Bookkeeper 对 Topic 进行分片、副本存储。