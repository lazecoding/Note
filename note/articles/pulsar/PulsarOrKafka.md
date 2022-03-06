# Pulsar 还是 Kafka

- 目录
  - [基础架构](#基础架构)
  - [消息模型](#消息模型)
  - [存储](#存储)
  - [其他](#其他)

我们来比较一下 Pulsar 和 Kafka。

### 基础架构

#### Kafka

一个典型的 Kafka 体系架构包括若干 Producer、若干 Broker、若干 Consumer，以及一个 ZooKeeper 集群，如下图所示。其中 ZooKeeper 是 Kafka 用来负责集群元数据的管理、控制器的选举等操作的。Producer 将消息发送到 Broker，Broker 负责将收到的消息存储到磁盘中，而 Consumer 负责从 Broker 订阅并消费消息。

> 注意：Apache Kafka 2.8 版本之后可以不需要使用 ZooKeeper。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/kafka/Kafka系统结构图.png" width="600px">
</div>

- Producer：生产者，也就是发送消息的一方。生产者负责创建消息，然后将其投递到 Kafka 中。
- Consumer：消费者，也就是接收消息的一方。消费者连接到 Kafka 上并接收消息，进而进行相应的业务逻辑处理。
- Broker：服务代理节点。对于 Kafka 而言，Broker 可以简单地看作一个独立的 Kafka 服务节点或 Kafka 服务实例。大多数情况下也可以将 Broker 看作一台 Kafka 服务器，前提是这台服务器上只部署了一个 Kafka 实例。一个或多个 Broker 组成了一个 Kafka 集群。一般而言，我们更习惯使用首字母小写的 broker 来表示服务代理节点。
- Zookeeper：Zookeeper 负责 Kafka 集群元数据的管理、控制器的选举等操作。

#### Pulsar

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

#### 对比

和 Kafka 相比，Pulsar 具有以下特征：

- Pulsar 采用分层架构，将计算和存储相分离，存储使用 BookKeeper 集群，计算使用 Broker 集群，Broker 需要内置 BookKeeper 客户端。
- Pulsar 的部署和架构更加复杂，但是也更具有伸缩性。
- Pulsar 在最新版本中依然不能脱离 Zookeeper 独立运行。

### 消息模型

#### Kafka

Kafka 中的消息以主体为单位进行归类，生产者负责将消息发送到特定的主体（发送到 Kafka 集群中的每一条消息都要指定一个主体），而消费者负责订阅主体并进行消费。主题只是一个逻辑上的概念，它还可以细分为多个分区，一个分区只属于单个主题。同一主题下的不同分区包含的消息是不同的，分区在存储层面可以看作一个可追加的日志文件，消息在被追加到分区日志文件的时候都会分配一个特定的偏移量（offset）。offset 是消息在分区中的唯一标识， Kafka 通过它来保证消息在分区内的顺序性，每个分区内的 offset 是独立的，所以 Kafka 只能保证分区有序而不是主题有序。Kafka 中的分区可以分布在不同 broker 上，也就是说，一个主体可以横跨多个 broker 。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/kafka/Kafka消息写入多个分区.png" width="600px">
</div>

每一条消息被发送到 broker 之前，会按照分区规则选择存储到具体的某个分区。如果分区规则设定得合理，所有的消息都可以均匀地分配到不同的分区中。如果一个主题只对应一个文件，那么这个文件所在的机器 I/O 将会成为这个主题的性能瓶颈，而分区解决了这个问题，通过增加分区的数量可以实现水平扩展。

Kafka 为分区引入了多副本（Replica）机制，可通过增加副本数量来提升容灾能力。同一分区的副本保存的是相同的消息（不过在同一时刻，副本之间并非完全一样）。副本之间是 `一主多从` 的关系，其中 leader 副本负责处理读写请求，follower 副本只负责与 leader 副本的消息同步。副本处于不同的broker中，当 leader 副本出现故障时，从 follower 副本中重新选举新的 leader 副本对外提供服务。Kafka 通过多副本机制实现了故障的自动转移，当 Kafka 集群中某个 broker 失效时仍然能保证服务可用。

如下图所示，Kafka 集群中有 4 个 broker，某个主题中有 3 个分区，且副本因子（副本个数）也为 3，如此，每个分区都有 1 个 leader 副本和 2 个 follower 副本。生产者与消费者只与 leader 副本进行交互，而 follower 副本只负责消息的同步，所以很多时候 follower 副本中的消息相对于 leader 副本而言有一定的滞后。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/kafka/主题的分区和副本.png" width="600px">
</div>

Kafka 消费端也具备一定的容灾能力。Consumer 使用拉（Pull）模式从服务端拉取消息，并且保存消费的具体位置，当消费者宕机后恢复上线时可以根据之前保存的消费位置重新拉取需要的消息进行消费，这样就不会造成消息丢失。

##### 消费者和消费组

消费者（Consumer）负责订阅 Kafka 中的主题（Topic），并且从订阅的主题上拉取消息。与其他一些消息中间件不同的是：在 Kafka的 消费理念中还有一层消费组（Consumer Group）的概念，每个消费者都有一个对应的消费组。当消息发布到主题后，只会被订阅它的每个消费组中的一个消费者所消费。

当一个主题被某个消费组中的多个消费者订阅，该主题中的分区只会被其中一个消费者绑定消费。当新增、减少消费者，主题会进入 `再均衡` 状态，分区与消费者的绑定会 `再分配`，整个过程消息是无法被消费的。

> 消费者与消费组这种模型可以让整体的消费能力具备横向伸缩性，我们可以增加（或减少）消费者的个数来提高（或降低）整体的消费能力。对于分区数固定的情况，一味地增加消费者并不会让消费能力一直得到提升，如果消费者过多，出现了消费者的个数大于分区个数的情况，就会有消费者分配不到任何分区。

对于消息中间件而言，一般有两种模式：`点对点模式` 和 `发布订阅模式`。

- 点对点模式是基于队列的，消息生产者发送消息到队列，消息消费者从队列中接收消息。
- 发布订阅模式定义了如何向一个内容节点发布和订阅消息，这个内容节点称为主题（Topic），主题可以认为是消息传递的中介，消息发布者将消息发布到某个主题，而消息订阅者从主题中订阅消息。主题使得消息的订阅者和发布者互相保持独立，不需要进行接触即可保证消息的传递，发布/订阅模式在消息的一对多广播时采用。

Kafka 同时支持两种模式，而这正是得益于消费者与消费组模型的契合：

- 如果所有的消费者都隶属于同一个消费组，那么所有的消息都会被均衡地投递给每一个消费者，即每条消息只会被一个消费者处理，这就相当于点对点模式的应用。
- 如果所有的消费者都隶属于不同的消费组，那么所有的消息都会被广播给所有的消费者，即每条消息会被所有的消费者处理，这就相当于发布/订阅模式的应用。

消费组是一个逻辑上的概念，它将旗下的消费者归为一类，每一个消费者只隶属于一个消费组。每一个消费组都会有一个固定的名称，消费者在进行消费前需要指定其所属消费组的名称，这个可以通过消费者客户端参数 `group.id` 来配置，默认值为空字符串。消费者并非逻辑上的概念，它是实际的应用实例，它可以是一个线程，也可以是一个进程。同一个消费组内的消费者既可以部署在同一台机器上，也可以部署在不同的机器上。

#### Pulsar

消息是 Pulsar 的基本单位，消息以 Topic 为单位进行归类。普通的主题仅仅被保存在单个 broker 中，这限制了主题的最大吞吐量。分区实际是通过在底层拥有 N 个内部主题来实现的，这个 N 的数量就是等于分区的数量。当向分区的 topic 发送消息，每条消息被路由到其中一个 broker，Pulsar 自动处理跨 broker 的分区分布。

下图展示了分区的生产、消费逻辑：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/pulsar/主题分区的生产消费模型.png" width="400px">
</div>

Topic1 有 5 个分区(P0 到 P4)，划分在 3 个 broker 上。因为分区多于 broker 数量，其中有两个 broker 要处理两个分区。第三个 broker 则只处理一个。（再次强调，分区的分布是 Pulsar 自动处理的）。

这个 topic 的消息被广播给两个 Consumer。路由模式决定将每个消息发布到哪个分区，而订阅类型决定将哪些消息被哪些消费者消费。

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

#### 对比

和 Kafka 相比，Pulsar 具有以下特征：

- 由于 Pulsar 的分层架构，实现了计算、存储分离，当 Pulsar 的分区相比 Kafka 更轻量灵活。
- Pulsar 的订阅相比 Kafka 的消费者组更灵活，Pulsar 的消费者可以实现多订阅。
- Kafka 中消费者组内消费者变更或 topic 及其分区变更会影响整个 topic 的消费，Pulsar 没有类似限制。

### 存储

#### Kafka

主题和分区都是提供给上层用户的抽象，而在副本层面或更加确切地说是 Log 层面才有实际物理上的存在。

> 此处说明的是 Kafka 消息存储的信息文件内容，不是所谓的 Kafka 服务器运行产生的日志文件。

考虑多副本的情况，一个分区对应一个日志 Log)。为了防止 Log 过大，Kafka 又引入了日志分段(LogSegment)的概念，将 Log 切分为多个 LogSegment，相当于一个巨型文件被平均分配为多个相对较小的文件。事实上， Log 和 LogSegment 也不是纯粹物理意义上的概念， Log 在物理上只以文件夹的形式存储，而在分区日志文件中，你会发现很多类型的文件，比如：.index、.timestamp、.log、.snapshot 等，其中，文件名一致的文件集合就称为 LogSement。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/kafka/主题内部的日志实现结构.png" width="600px">
</div>

向 Log 中追加消息时是顺序写入的，只有最后一个 LogSegment 才能执行写入操作，在此之前所有的 LogSegment 都不能写入数据。

- 为了方便描述，我们将最后一个 LogSegment 称为 `activeSegment`，即表示当前活跃的日志分段。
- 随着消息的不断写入，当 `activeSegment` 满足一定的条件时，就需要创建新的 `activeSegment`，之后追加的消息将写入新的 `activeSegment`。

在某一时刻，Kafka 中的文件目录布局如图所示：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/kafka/主题内部的日志文件目录.png" width="600px">
</div>

- 每一个根目录都会包含最基本的 4 个检查点文件(xxx-checkpoint)和 meta.properties 文件。
- 初始情况下主题 _consumer_offisets 并不存在，当第一次有消费者消费消息时会自动创建这个主题。
- 在创建主题的时候，如果当前 broker 中不止配置了一个根目录，那么会挑选分区数最少的那个根目录来完成本次创建任务。

##### LogSement

在分区日志文件中，你会发现很多类型的文件，比如：.index、.timestamp、.log、.snapshot 等，其中，文件名一致的文件集合就称为 LogSement。分区日志文件中包含很多的 LogSegment ，Kafka 日志追加是顺序写入的，LogSegment 可以减小日志文件的大小，进行日志删除的时候和数据查找的时候可以快速定位。同时，ActiveLogSegment 也就是活跃的日志分段拥有文件拥有写入权限，其余的 LogSegment 只有只读的权限。每个 LogSegment 都有一个基准偏移量，用来表示当前 LogSegment 中第一条消息的 offset。偏移量是一个 64 位的长整形数，固定是 20 位数字，长度未达到，用 0 进行填补，索引文件和日志文件都由该作为文件名命名规则。

日志文件中存在的多种后缀文件，重点需要关注 .index（偏移量索引文件）、.timestamp（时间戳索引文件）、.log（数据文件）三种类型。

- `偏移量索引文件（.log）`：用于记录消息偏移量与物理地址之间的映射关系。
- `时间戳索引文件（.timeindex）`：则根据时间戳查找对应的偏移量。
- `数据文件（.log）`：用于存储数据。

#### Pulsar

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

BookKeeper 的主要优势在于他能在有系统故障时保证读的一致性。由于 ledger 只能被一个进程写入（之前提的写入器进程），这样这个进程在写入时不会有冲突，从而写入会非常高效。在一次故障之后，ledger 会启动一个恢复进程来确定 ledger 的最终状态并确认最后提交到日志的是哪一个条目。在这之后，能保证所有的 ledger 读进程读取到相同的内容。

由于 BookKeeper Ledgers 提供了单一的日志抽象，在 ledger 的基础上开发了一个叫 managed ledger 的库，用以表示单个 topic 的存储层。managed ledger 即消息流的抽象，有一个写入器进程不断在流结尾添加消息，并且有多个 cursors 消费这个流，每个 cursor 有自己的消费位置。

一个 managed ledger 在内部用多个 BookKeeper ledgers 保存数据，这么做有两个原因：

- 在故障之后，原有的某个 ledger 不能再写了，需要创建一个新的。
- ledger 在所有 cursors 消费完它所保存的消息之后就可以被删除，这样可以实现 ledgers 的定期翻滚从头写。

##### 日志存储

BookKeeper 的日志文件包含事务日志。在更新到 ledger 之前，bookie 需要确保描述这个更新的事务被写到持久（非易失）存储上面。在 bookie 启动和旧的日志文件大小达到上限（由 journalMaxSizeMB 参数配置）的时候，新的日志文件会被创建。

#### 对比

Kafka 日志中的数据是串行的，可以按照写入的顺序快速读取数据。相比随机读取和写入，串行读取和写入速度更快。

日志固然好，但当数据量过大时，也会给我们带来一些麻烦，单台服务器上保存所有日志已经成为一个挑战。

Pulsar 对日志进行分段，从而避免了拷贝大块的日志。通过 BookKeeper，Pulsar 将日志分段分散到多台不同的服务器上。也就是说，日志不会保存在单台服务器上，任何一台服务器都不会成为整个系统的瓶颈。这使故障处理和扩容更加简单，只需要加入新的服务器，而无需进行再均衡处理。

### 其他

- Kafka 没有与租户完全隔离的本地多租户；而 Pulsar 内置了多租户，因此不同的团队可以使用相同的集群并将其隔离。这解决了许多管理难题。它支持隔离，身份验证，授权和配额。
- 扩展 Kafka 十分棘手，这是由于代理还存储数据的耦合体系结构所致，剥离另一个代理意味着它必须复制主题分区和副本，这非常耗时；Pulsar 是多层体系结构，计算/存储分离：`broker 负责处理和负载均衡 Producer 发出的消息，并将这些消息分派给 Consumer。BookKeeper 负责消息的持久化存储。Zookeeper 用来处理多个 Pulsar 集群之间的协调任务。`
- Kafka 存储可能会变得非常昂贵，尽管可以长时间存储数据，但是由于成本问题，很少使用它；Pulsar 将数据分布再多个节点，降低存储成本。
- Kafka 集群重新平衡会影响相连的生产者和消费者的性能；Pulsar 可以快速重新平衡。
- Pulsar 可以直接连接 BookKeeper 查询数据，并且不影响实时数据。