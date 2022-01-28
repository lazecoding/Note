# 消息的生产和消费

- 目录
    - [消息](#消息)
    - [生产者](#生产者)
      - [发送模式](#发送模式)
      - [访问模式](#访问模式)
      - [路由模式](#路由模式)
      - [压缩](#压缩)
      - [批量处理](#批量处理)
      - [消息分块](#消息分块)
    - [消费者](#消费者)
      - [接收模式](#接收模式)
      - [订阅模式](#订阅模式)
        - [Exclusive](#Exclusive)
        - [Failover](#Failover)
        - [Shared](#Shared)
        - [Key_Shared](#Key_Shared)
      - [消息确认](#消息确认)

Pulsar 采用 `发布-订阅` 的设计模式(pub-sub)，在这种模式中，生产者向主题发布消息；消费者 订阅这些主题，处理传入的消息，并在处理完成后向 broker 发送确认。消息一旦创建订阅，即使 consumer 断开连接，Pulsar 仍然可以保存所有消息。
只有当消费者确认所有这些消息都已成功处理时，才会丢弃保留的消息。

### 消息

消息是 Pulsar 的基本 "单位"，下表列出消息的组件。

| 组件             | 说明 |
| ---------------- | --- |
| Value/data payload | 消息所承载的数据。尽管消息数据也可以符合数据 schemas，但所有 Pulsar 消息都包含原始字节。 |
| Key | 消息可以选择用键进行标记，这在 topic 压缩 等操作很有用。 |
| Properties | 用户自定义属性的键值对（可选）。 |
| Producer 名称 | 	生成消息的 producer 的名称。 如果不指定，则使用默认名称。 |
| Sequence ID | 每个 Pulsar 消息都存储在其主题上的有序序列中。消息的序列 ID 是其在该序列中的顺序。 |
| Publish time | 消息发布的时间戳，由 producer 自动添加。 |
| Event time | 应用程序可以附加到消息的时间戳（可选）， 例如处理消息的时间。如果没有明确设置，则消息的事件时间为 0。 |
| TypedMessageBuilder | 用于构造消息。 您可以使用 TypedMessageBuilder 设置消息的键值对属性。在设置 TypedMessageBuilder 时，最佳的选择是将 key 设置为字符串。 如果将 key 设置为其他类型（例如，AVRO 对象），则 key 会以字节形式发送，这时 consumer 就很难使用了。 |

Message 默认最大可携带 5 MB 数据。您可以使用以下配置项更改这个默认值。

在 broker.conf 文件中

```C
# 消息的最大大小(字节数)。
maxMessageSize=5242880
```

在 bookkeeper.conf 配置文件中

```C
# netty frame 的最大尺寸(以字节为单位)。任何收到的大于此值的信息都会被拒绝。默认值是 5MB。
nettyMaxFrameSizeBytes=5253120
```

### 生产者

Producer 是连接 Topic 的程序，它将消息发布到一个 Pulsar broker 上。

#### 发送模式

Producer 可以以同步(sync) 或异步(async)的方式发布消息到 broker。

- `同步发送`：Producer 将在发送每条消息后等待 broker 的确认。如果未收到确认，则 producer 将认为发送失败。
- `异步发送`：Producer 将把消息放于阻塞队列中，并立即返回 然后，客户端将在后台将消息发送给 broker。如果队列已满(最大大小可配置)，则调用 API 时，producer 可能会立即被阻止或失败，具体取决于传递给 producer 的参数。

#### 访问模式

Producer 有多种模式访问 Topic，可以使用以下几种方式将消息发送到 Topic。

- `Shared`：默认情况下，多个生成者可以将消息发送到同一个 Topic。
- `Exclusive`：在这种模式下，只有一个生产者可以将消息发送到 Topic ，当其他生产者尝试发送消息到这个 Topic 时，会发生错误。只有独占 Topic 的生产者发生宕机时（Network Partition）该生产者会被驱逐，新的生产者才能产生并向 Topic 发送消息。
- `WaitForExclusive`：在这种模式下，只有一个生产者可以将消息发送到 Topic。当已有生成者和 Topic 建立连接时，其他生产者的创建会被挂起而不会产生错误。如果想要采用领导者选举机制来选择消费者的话，可以采用这种模式。

一旦应用程序创建了一个 `Exclusive` 或 `WaitForExclusive` 的访问模式成功，此应用程序将为该主题的 **唯一写者**。任何其他生产者试图产生关于这个 Topic 的消息，要么立即得到错误，要么一直等待，直到他们得到 `Exclusive` 访问权。

#### 路由模式

当发布消息到分区 Topic，你必须要指定路由模式，路由模式决定了每条消息被发布到的分区 -- 其实是内部主题。

有三种路由模式:

|  路由模式             | 说明  |
|  -----------------  | ---------------------------------------------------------------------------------------------------------------  |
| RoundRobinPartition | 如果消息没有指定 key，为了达到最大吞吐量，生产者会以 round-robin 方式将消息发布到所有分区。注意： round-robin 并不是作用于每条单独的消息，而是作用于延迟处理的批次边界，以确保批处理有效。如果消息指定了 key，分区生产者会根据 key 的 hash 值将该消息分配到对应的分区。这是默认的模式。  |
| SinglePartition	  | 如果消息没有指定 key，生产者将会随机选择一个分区，并发布所有消息到这个分区。如果消息指定了 key，分区生产者会根据 key 的 hash 值将该消息分配到对应的分区。  |
| CustomPartition     | 使用自定义消息路由器实现来决定特定消息的分区。用户可以创建自定义路由模式：使用 Java Client 并实现 MessageRouter 接口。  |

#### 压缩

由 Producer 发布的消息在传输过程中可以被压缩。Pulsar 目前支持以下类型的压缩：

- LZ4
- ZLIB
- ZSTD
- SNAPPY

#### 批量处理

Pulsar 支持对消息进行批量处理。批量处理启用后，Producer 会在一次请求中累积并发送一批消息。批量处理时的消息数量取决于最大消息数（单次批量处理请求可以发送的最大消息数）和最大发布延迟（单个请求的最大发布延迟时间）决定。因此，积压的数量是批量处理的请求总数，而不是消息总数。

通常情况下，只有 Consumer 确认了批量请求中的所有消息，这个批量请求才会被认定为已处理。当这批消息没有全部被确认的情况下，发生故障时，会导致一些已确认的消息被重复确认。

为了避免 Consumer 重复消费已确认的消息，Pulsar 从 Pulsar 2.6.0 开始采用批量索引确认机制。如果启用批量索引确认机制，Consumer 将筛选出已被确认的批量索引，并将批量索引确认请求发送给 broker。broker 维护批量索引的确认状态并跟踪每批索引的确认状态，以避免向 Consumer 发送已确认的消息。当该批信息的所有索引都被确认后，该批信息将被删除。

默认情况下，索引确认机制处于关闭状态。开启索引确认机制将产生导致更多内存开销。

#### 消息分块

启用分块后，如果消息大小超过允许发送的最大消息大小时，Producer 会将原始消息分割成多个

在 broker 中，分块消息会和普通消息以相同的方式存储在 ledger 中。唯一的区别是，Consumer 需要缓存分块消息，并在接收到所有的分块消息后将其合并成真正的消息。如果 Producer 不能及时发布消息的所有分块，Consumer 不能在消息的过期时间内接收到所有的分块，那么 Consumer 已接收到的分块消息就会过期。

Consumer 会将分块的消息拼接在一起，并将它们放入接收器队列中。客户端从接收器队列中消费消息。当 Consumer 消费到原始的大消息并确认后，Consumer 就会发送与该大消息关联的所有分块消息的确认。

### 消费者

消费者是通过订阅 Topic 以接收消息的应用程序。

Consumer 向 broker 发送消息流获取申请（flow permit request）以获取消息。 在 Consumer 端有一个队列，用于接收从 broker 推送来的消息。 你能够通过 `receiverQueueSize` 参数配置队列的长度 (队列的默认长度是1000) 每当 `consumer.receive()` 被调用一次，就从缓冲区（buffer）获取一条消息。

#### 接收模式

可以通过同步(sync) 或者异步(async)的方式从 brokers 接受消息。

- `同步接收`：同步模式，在收到消息之前都是被阻塞的。
- `异步接收`：异步接收模式会立即返回一个 future 值（如 Java 中的 CompletableFuture），一旦收到新的消息就立刻完成。

#### 订阅模式

订阅是命名好的配置规则，指导消息如何投递给消费者。Pulsar 中有四种订阅模式: 独占、灾备、共享和 key 共享。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/四种订阅模式总览.png" width="600px">
</div>

##### Exclusive

Exclusive（独占）模式中，只允许单个消费者订阅某一个主题，如果多个使用者使用相同的订阅订阅一个主题，则会发生错误。

> Exclusive模式为默认订阅模式。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/Exclusive模式.png" width="600px">
</div>

##### Failover

Failover（灾备）多模式中，允许多个使用者可以附加到相同的订阅。主消费者会消费非分区主题或者分区主题中的每个分区的消息。当 master 消费者断开链接，
所有（非确认的和后续的）消息都被传递给线上的下一个使用者。

对于分区主题来说，broker 将按照消费者的优先级和消费者名称的词汇表顺序对消费者进行排序，然后试图将主题均匀的分配给优先级最高的消费者。

对于非分区主题来说，broker 会根据消费者订阅非分区主题的顺序选择消费者。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/Failover模式.png" width="600px">
</div>

##### Shared

Shared（共享）模式中，多个使用者可以附加到相同的订阅。消息通过轮询机制分发给不同的消费者，并且每个消息仅会被分发给一个消费者。
当消费者断开连接，所有被发送给他，但没有被确认的消息将被重新安排，分发给其它存活的消费者。

> 当使用 Shared 模型时，不保证消息排序，不能用 Shared 类型来使用累积确认。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/Shared模式.png" width="600px">
</div>

##### Key_Shared

在 Key_Shared（key 共享）模式中，多个使用者可以附加到相同的订阅。消息在分发中跨消费者传递，具有相同键或相同排序键的消息只传递给一个消费者。
无论消息被重新传递多少次，它都将被传递到相同的使用者。当消费者连接或断开时，将导致服务消费者更改消息的某些关键字。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/Key_Shared模式.png" width="600px">
</div>

#### 消息确认

消费者在成功消费一个消息后，向 broker 发送一个确认请求。 然后，这条被消费的消息将被永久保存，只有在所有订阅者都确认后才会被删除。 如果希望消息被消费者确认后仍然保留下来，可配置消息保留策略实现。

消息可以通过以下两种方式之一进行确认。

- `单独确认`：在单独确认的情况下，消费者确认每个消息，并向 Broker 发送确认请求。
- `累积确认模式`：在累积确认中，消费者只确认它收到的最后一条消息。 所有之前（包含此条）的消息，都不会被再次发送给那个消费者。

> 累积确认不能用于 Shared subscription 类型，因为 Shared subscription 类型涉及多个消费者，他们可以访问同一个订阅。 在共享订阅模式，消息都是单条确认模式。

消息取消确认也有单条取消模式和累积取消模式 ，这依赖于消费者使用的订阅模式。

> 如果启用了批处理，一个批处理中的所有消息都会重新交付给消费者。

通常情况下可以使用取消确认来达到处理消息失败后重新处理消息的目的，但通过 `redelivery backoff` 可以更好的实现这种目的。可以通过指定消息重试的次数、消息重发的延迟来重新消费处理失败的消息。
