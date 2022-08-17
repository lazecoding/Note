# Stream

- 目录
  - [数据结构](#数据结构)
  - [使用](#使用)
    - [增删改查](#增删改查)
    - [独立消费](#独立消费)
    - [消费组消费](#消费组消费)
      - [创建消费组](#创建消费组)
      - [消费组消费](#消费组消费)
      - [信息监控](#信息监控)
  - [深入](#深入)
    - [消息队列设计要点](#消息队列设计要点)
    - [消息 ID 的设计是否考虑了时间回拨的问题](#消息-ID-的设计是否考虑了时间回拨的问题)
    - [消费者崩溃带来的会不会消息丢失问题](#消费者崩溃带来的会不会消息丢失问题)
    - [消费者彻底宕机后如何转移给其它消费者处理](#消费者彻底宕机后如何转移给其它消费者处理)
    - [坏消息问题、Dead Letter、死信问题](#坏消息问题、Dead Letter、死信问题)

Redis Stream 是 Redis 5.0 版本新增加的数据结构。

Redis Stream 主要用于消息队列（MQ，Message Queue），Redis 本身是有一个 Redis 发布订阅 (pub/sub) 来实现消息队列的功能，但它有个缺点就是消息无法持久化，如果出现网络断开、Redis 宕机等，消息就会被丢弃。

简单来说发布订阅 (pub/sub) 可以分发消息，但无法记录历史消息。而 Redis Stream 提供了消息的持久化和主从复制功能，可以让任何客户端访问任何时刻的数据，并且能记住每一个客户端的访问位置，还能保证消息不丢失。

### 数据结构

一个 Stream 可以拥有多个消费组，每个消费组中又包含了多个消费者，组内消费者之间存在竞争关系。当某个消费者消费了一条消息时，同组消费者，都不会再次消费这条消息。被消费的消息 ID 会被放入等待处理的 pending_ids 中。每消费完一条信息，消费组的游标就会向前移动一位，组内消费者就继续去争抢下消息。

Stream 数据结构：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/Stream数据结构图.png" width="600px">
</div>

> 每个 Stream 都有唯一的名称，它就是 Redis 的 key，在我们首次使用 xadd 指令追加消息时自动创建。

上图解析：

- `Stream direction`：表示数据流，它是一个消息链，将所有的消息都串起来，每个消息都有一个唯一标识 ID 和对应的消息内容（Message content）。如果接收到的消息比较多，为了避免 Stream 过长，可以选择指定 Stream 的最大长度，一旦到达了最大长度，就会从最早的消息开始清除，保证 Stream 中最新的消息。
- `Consumer Group`：消费组，使用 XGROUP CREATE 命令创建，一个消费组有多个消费者(Consumer), 这些消费者之间是竞争关系。
- `last_delivered_id`：游标，每个消费组会有个游标 last_delivered_id，任意一个消费者读取了消息都会使游标 last_delivered_id 往前移动。
- `pending_ids`：消费者(Consumer)的状态变量，作用是维护消费者的未确认的 id。pending_ids 记录了当前已经被客户端读取的消息，但是还没有 ack (Acknowledge character：确认字符）。如果客户端没有 ack，这个变量里面的消息ID会越来越多，一旦某个消息被ack，它就开始减少。这个 pending_ids 变量在 Redis 官方被称之为 PEL，也就是 Pending Entries List，这是一个很核心的数据结构，它用来确保客户端至少消费了消息一次，而不会在网络传输的中途丢失了没处理。
- `消息 ID`：消息 ID 的形式是 timestampInMillis-sequence，例如 1527846880572-5，它表示当前的消息在毫米时间戳 1527846880572 时产生，并且是该毫秒内产生的第 5 条消息。消息 ID 可以由服务器自动生成，也可以由客户端自己指定，但是形式必须是 `整数-整数`，而且必须是后面加入的消息的 ID 要大于前面的消息 ID。
- `消息内容`：消息内容就是键值对，形如hash结构的键值对，这没什么特别之处。

### 使用

#### 增删改查

消息队列相关命令：

- XADD：添加消息到末尾
- XTRIM：对流进行修剪，限制长度
- XDEL：删除消息
- XLEN：获取流包含的元素数量，即消息长度
- XRANGE：获取消息列表，会自动过滤已经删除的消息
- XREVRANGE：反向获取消息列表，ID 从大到小
- XREAD：以阻塞或非阻塞方式获取消息列表

```C
# *号表示服务器自动生成ID，后面顺序跟着一堆key/value
127.0.0.1:6379> xadd codehole * name laoqian age 30  #  名字叫laoqian，年龄30岁
1527849609889-0  # 生成的消息ID
127.0.0.1:6379> xadd codehole * name xiaoyu age 29
1527849629172-0
127.0.0.1:6379> xadd codehole * name xiaoqian age 1
1527849637634-0
127.0.0.1:6379> xlen codehole
(integer) 3
127.0.0.1:6379> xrange codehole - +  # -表示最小值, +表示最大值
127.0.0.1:6379> xrange codehole - +
1) 1) 1527849609889-0
   1) 1) "name"
      1) "laoqian"
      2) "age"
      3) "30"
2) 1) 1527849629172-0
   1) 1) "name"
      1) "xiaoyu"
      2) "age"
      3) "29"
3) 1) 1527849637634-0
   1) 1) "name"
      1) "xiaoqian"
      2) "age"
      3) "1"
127.0.0.1:6379> xrange codehole 1527849629172-0 +  # 指定最小消息ID的列表
1) 1) 1527849629172-0
   2) 1) "name"
      2) "xiaoyu"
      3) "age"
      4) "29"
2) 1) 1527849637634-0
   2) 1) "name"
      2) "xiaoqian"
      3) "age"
      4) "1"
127.0.0.1:6379> xrange codehole - 1527849629172-0  # 指定最大消息ID的列表
1) 1) 1527849609889-0
   2) 1) "name"
      2) "laoqian"
      3) "age"
      4) "30"
2) 1) 1527849629172-0
   2) 1) "name"
      2) "xiaoyu"
      3) "age"
      4) "29"
127.0.0.1:6379> xdel codehole 1527849609889-0
(integer) 1
127.0.0.1:6379> xlen codehole  # 长度不受影响
(integer) 3
127.0.0.1:6379> xrange codehole - +  # 被删除的消息没了
1) 1) 1527849629172-0
   2) 1) "name"
      2) "xiaoyu"
      3) "age"
      4) "29"
2) 1) 1527849637634-0
   2) 1) "name"
      2) "xiaoqian"
      3) "age"
      4) "1"
127.0.0.1:6379> del codehole  # 删除整个Stream
(integer) 1
```

> 补充一点，xdel 并不会真正删除消息，虽然 xrange 看不到了，但是 xlen 其实没变。

#### 独立消费

我们可以在不定义消费组的情况下进行 Stream 消息的独立消费，当 Stream 没有新消息时，甚至可以阻塞等待。Redis 设计了一个单独的消费指令 xread，可以将 Stream 当成普通的消息队列(list)来使用。使用 xread 时，我们可以完全忽略消费组(Consumer Group)的存在，就好比 Stream 就是一个普通的列表(list)。

```C
# 从Stream头部读取两条消息
127.0.0.1:6379> xread count 2 streams codehole 0-0
1) 1) "codehole"
   2) 1) 1) 1527851486781-0
         2) 1) "name"
            2) "laoqian"
            3) "age"
            4) "30"
      2) 1) 1527851493405-0
         2) 1) "name"
            2) "yurui"
            3) "age"
            4) "29"
# 从Stream尾部读取一条消息，毫无疑问，这里不会返回任何消息
127.0.0.1:6379> xread count 1 streams codehole $
(nil)
# 从尾部阻塞等待新消息到来，下面的指令会堵住，直到新消息到来
127.0.0.1:6379> xread block 0 count 1 streams codehole $
# 我们从新打开一个窗口，在这个窗口往Stream里塞消息
127.0.0.1:6379> xadd codehole * name youming age 60
1527852774092-0
# 再切换到前面的窗口，我们可以看到阻塞解除了，返回了新的消息内容
# 而且还显示了一个等待时间，这里我们等待了93s
127.0.0.1:6379> xread block 0 count 1 streams codehole $
1) 1) "codehole"
   2) 1) 1) 1527852774092-0
         2) 1) "name"
            2) "youming"
            3) "age"
            4) "60"
(93.11s)
```

客户端如果想要使用 xread 进行顺序消费，一定要记住当前消费到哪里了，也就是返回的消息 ID。下次继续调用 xread 时，将上次返回的最后一个消息 ID 作为参数传递进去，就可以继续消费后续的消息。

> block 0 表示永远阻塞，直到消息到来，block 1000 表示阻塞 1s，如果 1s 内没有任何消息到来，就返回 nil。

#### 消费组消费

消费组消费示意图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/消费组消费示意图.png" width="600px">
</div>

相关命令：

- XGROUP CREATE：创建消费者组。
- XREADGROUP GROUP：读取消费者组中的消息。
- XACK：将消息标记为 "已处理"。
- XGROUP SETID：为消费者组设置新的最后递送消息 ID
- XGROUP DELCONSUMER：删除消费者。
- XGROUP DESTROY：删除消费者组。
- XPENDING：显示待处理消息的相关信息。
- XCLAIM：转移消息的归属权。
- XINFO：查看流和消费者组的相关信息。
- XINFO GROUPS：打印消费者组的信息。
- XINFO STREAM：打印流信息。

##### 创建消费组

Stream 通过 xgroup create 指令创建消费组(Consumer Group)，需要传递起始消息 ID 参数用来初始化 last_delivered_id 变量。

```C
127.0.0.1:6379> xgroup create codehole cg1 0-0  #  表示从头开始消费
OK
# $表示从尾部开始消费，只接受新消息，当前Stream消息会全部忽略
127.0.0.1:6379> xgroup create codehole cg2 $
OK
127.0.0.1:6379> xinfo stream codehole  # 获取Stream信息
 1) length
 2) (integer) 3  # 共3个消息
 3) radix-tree-keys
 4) (integer) 1
 5) radix-tree-nodes
 6) (integer) 2
 7) groups
 8) (integer) 2  # 两个消费组
 9) first-entry  # 第一个消息
10) 1) 1527851486781-0
    2) 1) "name"
       2) "laoqian"
       3) "age"
       4) "30"
11) last-entry  # 最后一个消息
12) 1) 1527851498956-0
    2) 1) "name"
       2) "xiaoqian"
       3) "age"
       4) "1"
127.0.0.1:6379> xinfo groups codehole  # 获取Stream的消费组信息
1) 1) name
   2) "cg1"
   3) consumers
   4) (integer) 0  # 该消费组还没有消费者
   5) pending
   6) (integer) 0  # 该消费组没有正在处理的消息
2) 1) name
   2) "cg2"
   3) consumers  # 该消费组还没有消费者
   4) (integer) 0
   5) pending
   6) (integer) 0  # 该消费组没有正在处理的消息
```

##### 消费组消费

Stream 提供了 xreadgroup 指令可以进行消费组的组内消费，需要提供消费组名称、消费者名称和起始消息 ID。它同 xread 一样，也可以阻塞等待新消息。读到新消息后，对应的消息 ID 就会进入消费者的 PEL（正在处理的消息）结构里，客户端处理完毕后使用 xack 指令通知服务器，本条消息已经处理完毕，该消息 ID 就会从 PEL 中移除。

```C
# >号表示从当前消费组的last_delivered_id后面开始读
# 每当消费者读取一条消息，last_delivered_id变量就会前进
127.0.0.1:6379> xreadgroup GROUP cg1 c1 count 1 streams codehole >
1) 1) "codehole"
   2) 1) 1) 1527851486781-0
         2) 1) "name"
            2) "laoqian"
            3) "age"
            4) "30"
127.0.0.1:6379> xreadgroup GROUP cg1 c1 count 1 streams codehole >
1) 1) "codehole"
   2) 1) 1) 1527851493405-0
         2) 1) "name"
            2) "yurui"
            3) "age"
            4) "29"
127.0.0.1:6379> xreadgroup GROUP cg1 c1 count 2 streams codehole >
1) 1) "codehole"
   2) 1) 1) 1527851498956-0
         2) 1) "name"
            2) "xiaoqian"
            3) "age"
            4) "1"
      2) 1) 1527852774092-0
         2) 1) "name"
            2) "youming"
            3) "age"
            4) "60"
# 再继续读取，就没有新消息了
127.0.0.1:6379> xreadgroup GROUP cg1 c1 count 1 streams codehole >
(nil)
# 那就阻塞等待吧
127.0.0.1:6379> xreadgroup GROUP cg1 c1 block 0 count 1 streams codehole >
# 开启另一个窗口，往里塞消息
127.0.0.1:6379> xadd codehole * name lanying age 61
1527854062442-0
# 回到前一个窗口，发现阻塞解除，收到新消息了
127.0.0.1:6379> xreadgroup GROUP cg1 c1 block 0 count 1 streams codehole >
1) 1) "codehole"
   2) 1) 1) 1527854062442-0
         2) 1) "name"
            2) "lanying"
            3) "age"
            4) "61"
(36.54s)
127.0.0.1:6379> xinfo groups codehole  # 观察消费组信息
1) 1) name
   2) "cg1"
   3) consumers
   4) (integer) 1  # 一个消费者
   5) pending
   6) (integer) 5  # 共5条正在处理的信息还有没有ack
2) 1) name
   2) "cg2"
   3) consumers
   4) (integer) 0  # 消费组cg2没有任何变化，因为前面我们一直在操纵cg1
   5) pending
   6) (integer) 0
# 如果同一个消费组有多个消费者，我们可以通过xinfo consumers指令观察每个消费者的状态
127.0.0.1:6379> xinfo consumers codehole cg1  # 目前还有1个消费者
1) 1) name
   2) "c1"
   3) pending
   4) (integer) 5  # 共5条待处理消息
   5) idle
   6) (integer) 418715  # 空闲了多长时间ms没有读取消息了
# 接下来我们ack一条消息
127.0.0.1:6379> xack codehole cg1 1527851486781-0
(integer) 1
127.0.0.1:6379> xinfo consumers codehole cg1
1) 1) name
   2) "c1"
   3) pending
   4) (integer) 4  # 变成了5条
   5) idle
   6) (integer) 668504
# 下面ack所有消息
127.0.0.1:6379> xack codehole cg1 1527851493405-0 1527851498956-0 1527852774092-0 1527854062442-0
(integer) 4
127.0.0.1:6379> xinfo consumers codehole cg1
1) 1) name
   2) "c1"
   3) pending
   4) (integer) 0  # pel空了
   5) idle
   6) (integer) 745505
```

##### 信息监控

Stream 提供了 xinfo 来实现对服务器信息的监控。

- 查看队列信息

```C
127.0.0.1:6379> Xinfo stream mq
 1) "length"
 2) (integer) 7
 3) "radix-tree-keys"
 4) (integer) 1
 5) "radix-tree-nodes"
 6) (integer) 2
 7) "groups"
 8) (integer) 1
 9) "last-generated-id"
10) "1553585533795-9"
11) "first-entry"
12) 1) "1553585533795-3"
    2) 1) "msg"
       2) "4"
13) "last-entry"
14) 1) "1553585533795-9"
    2) 1) "msg"
       2) "10"
```

- 消费组信息

```C
127.0.0.1:6379> Xinfo groups mq
1) 1) "name"
   2) "mqGroup"
   3) "consumers"
   4) (integer) 3
   5) "pending"
   6) (integer) 3
   7) "last-delivered-id"
   8) "1553585533795-4"
```

- 消费者组成员信息

```C
127.0.0.1:6379> XINFO CONSUMERS mq mqGroup
1) 1) "name"
   2) "consumerA"
   3) "pending"
   4) (integer) 1
   5) "idle"
   6) (integer) 18949894
2) 1) "name"
   2) "consumerB"
   3) "pending"
   4) (integer) 1
   5) "idle"
   6) (integer) 3092719
3) 1) "name"
   2) "consumerC"
   3) "pending"
   4) (integer) 1
   5) "idle"
   6) (integer) 23683256
```

### 深入

我们结合MQ中常见问题，看 Redis 是如何解决的，来进一步理解 Redis。

#### 消息队列设计要点

如果我们期望设计一种数据结构来实现消息队列，最重要的就是要理解设计一个消息队列需要考虑什么？

借助美团技术团队的一篇文章，[消息队列设计精要](https://tech.meituan.com/2016/07/01/mq-design.html) 中的图。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/消息队列设计精要-设计要点.png" width="600px">
</div>

Redis Stream 是一种超轻量 MQ，它并没有完全实现消息队列所有设计要点，这决定着它适用的场景。Redis 设计 Steam 考虑了以下几点：

- 消息 ID 的序列化生成
- 消息遍历
- 消息的阻塞和非阻塞读取
- 消息的分组消费
- 未完成消息的处理
- 消息队列监控

> Stream只是一个数据结构，它不会主动把消息推送给消费者，需要消费者主动来消费数据。

#### 消息 ID 的设计是否考虑了时间回拨的问题

XADD 生成的 1553439850328-0，就是 Redis 生成的消息 ID，由两部分组成：时间戳-序号。时间戳是毫秒级单位，是生成消息的 Redis 服务器时间，它是个 64 位整型（int64）。序号是在这个毫秒时间点内的消息序号，它也是个 64 位整型。

可以通过 multi 批处理，来验证序号的递增：

```C
127.0.0.1:6379> MULTI
OK
127.0.0.1:6379> XADD memberMessage * msg one
QUEUED
127.0.0.1:6379> XADD memberMessage * msg two
QUEUED
127.0.0.1:6379> XADD memberMessage * msg three
QUEUED
127.0.0.1:6379> XADD memberMessage * msg four
QUEUED
127.0.0.1:6379> XADD memberMessage * msg five
QUEUED
127.0.0.1:6379> EXEC
1) "1553441006884-0"
2) "1553441006884-1"
3) "1553441006884-2"
4) "1553441006884-3"
5) "1553441006884-4"
```

由于一个 Redis 命令的执行很快，所以可以看到在同一时间戳内，是通过序号递增来表示消息的。

为了保证消息是有序的，因此 Redis 生成的 ID 是单调递增有序的。由于 ID 中包含时间戳部分，为了避免服务器时间错误而带来的问题（例如服务器时间延后了），Redis 的每个 Stream 类型数据都维护一个 latest_generated_id 属性，用于记录最后一个消息的 ID。若发现当前时间戳退后（小于 latest_generated_id 所记录的），则采用时间戳不变而序号递增的方案来作为新消息 ID（这也是序号为什么使用 int64 的原因，保证有足够多的的序号），从而保证 ID 的单调递增性质。

> 建议使用 Redis 的方案生成消息 ID，因为这种 时间戳+序号 的单调递增的 ID 方案，几乎可以满足你全部的需求。但同时，记住 ID 是支持自定义的。

#### 消费者崩溃带来的会不会消息丢失问题

为了解决组内消息读取但处理期间消费者崩溃带来的消息丢失问题，Stream 设计了 pending_ids 列表，用于记录读取但并未处理完毕的消息。命令 XPENDIING 用来获消费组或消费内消费者的未处理完毕的消息。演示如下：

```C
127.0.0.1:6379> XPENDING mq mqGroup # mpGroup的Pending情况
1) (integer) 5 # 5个已读取但未处理的消息
2) "1553585533795-0" # 起始ID
3) "1553585533795-4" # 结束ID
4) 1) 1) "consumerA" # 消费者A有3个
      2) "3"
   2) 1) "consumerB" # 消费者B有1个
      2) "1"
   3) 1) "consumerC" # 消费者C有1个
      2) "1"

127.0.0.1:6379> XPENDING mq mqGroup - + 10 # 使用 start end count 选项可以获取详细信息
1) 1) "1553585533795-0" # 消息ID
   2) "consumerA" # 消费者
   3) (integer) 1654355 # 从读取到现在经历了1654355ms，IDLE
   4) (integer) 5 # 消息被读取了5次，delivery counter
2) 1) "1553585533795-1"
   2) "consumerA"
   3) (integer) 1654355
   4) (integer) 4
# 共5个，余下3个省略 ...

127.0.0.1:6379> XPENDING mq mqGroup - + 10 consumerA # 在加上消费者参数，获取具体某个消费者的Pending列表
1) 1) "1553585533795-0"
   2) "consumerA"
   3) (integer) 1641083
   4) (integer) 5
# 共3个，余下2个省略 ...
```

每个 Pending Entry 的消息有 4 个属性：

- 消息 ID
- 所属消费者
- IDLE，已读取时长
- delivery counter，消息被读取次数

上面的结果我们可以看到，我们之前读取的消息，都被记录在 pending_ids 列表中，说明全部读到的消息都没有处理，仅仅是读取了。那如何表示消费者处理完毕了消息呢？使用命令 XACK 完成告知消息处理完成，演示如下：

```C
127.0.0.1:6379> XACK mq mqGroup 1553585533795-0 # 通知消息处理结束，用消息ID标识
(integer) 1

127.0.0.1:6379> XPENDING mq mqGroup # 再次查看Pending列表
1) (integer) 4 # 已读取但未处理的消息已经变为4个
2) "1553585533795-1"
3) "1553585533795-4"
4) 1) 1) "consumerA" # 消费者A，还有2个消息处理
      2) "2"
   2) 1) "consumerB"
      2) "1"
   3) 1) "consumerC"
      2) "1"
127.0.0.1:6379>
```

有了这样一个 pending 机制，就意味着在某个消费者读取消息但未处理后，消息是不会丢失的。等待消费者再次上线后，可以读取该 pending 列表，就可以继续处理该消息了，保证消息的有序和不丢失。

#### 消费者彻底宕机后如何转移给其它消费者处理

还有一个问题，就是若某个消费者宕机之后，没有办法再上线了，那么就需要将该消费者 pending 的消息，转义给其他的消费者处理，就是消息转移。

消息转移的操作时将某个消息转移到自己的 pending_ids 列表中。使用语法 XCLAIM 来实现，需要设置组、转移的目标消费者和消息 ID，同时需要提供 IDLE（已被读取时长），只有超过这个时长，才能被转移。演示如下：

```C
# 当前属于消费者A的消息1553585533795-1，已经15907,787ms未处理了
127.0.0.1:6379> XPENDING mq mqGroup - + 10
1) 1) "1553585533795-1"
   2) "consumerA"
   3) (integer) 15907787
   4) (integer) 4

# 转移超过3600s的消息1553585533795-1到消费者B的Pending列表
127.0.0.1:6379> XCLAIM mq mqGroup consumerB 3600000 1553585533795-1
1) 1) "1553585533795-1"
   2) 1) "msg"
      2) "2"

# 消息1553585533795-1已经转移到消费者B的Pending中。
127.0.0.1:6379> XPENDING mq mqGroup - + 10
1) 1) "1553585533795-1"
   2) "consumerB"
   3) (integer) 84404 # 注意IDLE，被重置了
   4) (integer) 5 # 注意，读取次数也累加了1次
```

以上代码，完成了一次消息转移。转移除了要指定 ID 外，还需要指定 IDLE，保证是长时间未处理的才被转移。被转移的消息的 IDLE 会被重置，用以保证不会被重复转移，以为可能会出现将过期的消息同时转移给多个消费者的并发操作，设置了 IDLE，则可以避免后面的转移不会成功，因为 IDLE 不满足条件。例如下面的连续两条转移，第二条不会成功。

```C
127.0.0.1:6379> XCLAIM mq mqGroup consumerB 3600000 1553585533795-1
127.0.0.1:6379> XCLAIM mq mqGroup consumerC 3600000 1553585533795-1
```

#### 坏消息问题，Dead Letter，死信问题


正如上面所说，如果某个消息，不能被消费者处理，也就是不能被 XACK，这是要长时间处于 pending_ids 中，即使被反复的转移给各个消费者也是如此。此时该消息的 delivery counter 就会累加（上一节的例子可以看到），当累加到某个我们预设的临界值时，我们就认为是坏消息（也叫死信，DeadLetter，无法投递的消息），由于有了判定条件，我们将坏消息处理掉即可，删除即可。删除一个消息，使用 XDEL 语法，演示如下：

```C
# 删除队列中的消息
127.0.0.1:6379> XDEL mq 1553585533795-1
(integer) 1
# 查看队列中再无此消息
127.0.0.1:6379> XRANGE mq - +
1) 1) "1553585533795-0"
   2) 1) "msg"
      2) "1"
2) 1) "1553585533795-2"
   2) 1) "msg"
      2) "3"
```

本例中，并没有删除 pending_ids 中的消息，可以执行 XACK 标识其处理完毕。


