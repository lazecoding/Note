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