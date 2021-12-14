# 分布式 Id

- 目录
    - [UUID](#UUID)
    - [数据库自增 ID](#数据库自增-ID)
    - [数据库多实例自增 ID](#数据库多实例自增-ID)
    - [号段模式](#号段模式)
    - [Redis](#Redis)
    - [雪花算法](#雪花算法)
    - [Leaf](#Leaf)
      - [Segment](#Segment)
        - [双 buffer](#双-buffer)
      - [Snowflake](#Snowflake)

数据库中的每条数据库通常需要唯一 ID 来标识，传统方式采用自增 ID 即可满足要求，但在分布式系统中往往需要对数据进行分库分表，这时候就需要一个全局唯一的 ID 来标识数据，这个全局唯一的 ID 就叫做 `分布式 ID`。

分布式 ID 是分布式系统的全局唯一 ID，分布式 ID 生成的好坏将直接影响到整个系统的性能。分布式 ID 需要满足如下条件：

- 全局唯一：必须保证 ID 全局性唯一。
- 高性能：延时低、响应快，否则反倒可能成为性能瓶颈。
- 高可用：作为数据的唯一标识，必须保持高可用。
- 趋势递增：数值类型最优，能够趋势递增。
- 易接入：在系统设计和实现上要尽可能的简单，开箱即可。

本文主要分析以下 9 种分布式 ID 生成方式以及优缺点：

- UUID
- 数据库自增 ID
- 数据库多主模式
- 号段模式
- Redis
- 雪花算法（SnowFlake）
- 美团（Leaf）
- 百度（UIDgenerator）
- 滴滴出品（TinyID）

### UUID

根据 UUID 生成分布式 ID 最容易想到的，UUID 存在多个版本。

- UUID Version 1：基于时间的 UUID

基于时间的 UUID 通过计算当前时间戳、随机数和机器 MAC 地址得到。由于在算法中使用了 MAC 地址，这个版本的 UUID 可以保证在全球范围的唯一性。
但与此同时，使用 MAC 地址会带来安全性问题，这就是这个版本 UUID 受到批评的地方。如果应用只是在局域网中使用，也可以使用退化的算法，以 IP 地址来代替 MAC 地址。

- UUID Version 2：DCE 安全的 UUID

DCE（Distributed Computing Environment）安全的 UUID 和基于时间的 UUID 算法相同，但会把时间戳的前 4 位置换为 POSIX 的 UID 或 GID。这个版本的 UUID 在实际中较少用到。

- UUID Version 3：基于名字的 UUID（MD5）

基于名字的 UUID 通过计算名字和名字空间的 MD5 散列值得到。这个版本的 UUID 保证了：相同名字空间中不同名字生成的 UUID 的唯一性；不同名字空间中的 UUID 的唯一性；
相同名字空间中相同名字的 UUID 重复生成是相同的。

- UUID Version 4：随机 UUID

根据随机数，或者伪随机数生成 UUID。这种 UUID 产生重复的概率是可以计算出来的，但随机的东西就像是买彩票：你指望它发财是不可能的，但狗屎运通常会在不经意中到来。

- UUID Version 5：基于名字的 UUID（SHA1）

和版本 3 的 UUID 算法类似，只是散列值计算使用 SHA1（Secure Hash Algorithm 1）算法。

权衡：

Version 1/2 适合应用于分布式计算环境下，具有高度的唯一性；
Version 3/5 适合于一定范围内名字唯一，且需要或可能会重复生成 UUID 的环境下；
至于 Version 4，个人的建议是最好不用（虽然它是最简单最方便的）。

优点：

- 本机生成，无性能问题；V1 版本理论上全球唯一。

缺点：

- ID 无序；字符串；长度过长；基于 MAC 生成可能造成 MAC 泄漏。
（对于数据库主键，最好的应该有序，短小，数值类型。尤其是无序会导致数据库聚簇索引结构频繁变动）

###  数据库自增 ID 

基于数据库的 auto_increment 自增 ID 完全可以充当分布式 ID，具体实现：需要一个单独的 MySQL 实例用来生成 ID，建表结构如下：‍

```sql
CREATE TABLE SEQUENCE_ID (    
  id bigint(20) unsigned NOT NULL auto_increment,     
  value char(10) NOT NULL default '',    
  PRIMARY KEY (id)
) ENGINE=MyISAM;
```

当我们需要一个 ID 的时候，向表中插入一条记录返回主键 ID，但这种方式有一个比较致命的缺点，访问量激增时 MySQL 本身就是系统的瓶颈，用它来实现分布式服务风险比较大，不推荐！

优点：

- 实现简单，ID 单调自增，数值类型查询速度快。

缺点：

- DB 单点存在宕机风险，`高并发场景下 DB 本身将成为性能瓶颈`。

###  数据库多实例自增 ID 

这个方案就是为了解决 MySQL 单点问题，在 auto_increment 基本上面，设置 step 步长。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/systemdesign/数据库多实例自增ID图.png" width="600px">
</div>

从上图可以看出，水平扩展的数据库集群，有利于解决数据库单点问题，同时为了 ID 生成特性，将自增步长按照机器数量来设置。

优点：

- 解决了单点问题。

缺点：

- 步长确定后无法扩容；`高并发场景下 DB 本身仍将成为性能瓶颈`。

### 号段模式

号段模式是主流分布式 ID 生成方式之一，号段模式可以理解为从数据库批量的获取自增 ID，每次从数据库取出一个号段范围，例如 (1,1000] 代表 1000 个 ID，
具体的业务服务将本号段，生成 1~1000 的自增 ID 并加载到内存。表结构如下：

```sql

CREATE TABLE id_generator (  
  id int(10) NOT NULL,  
  max_id bigint(20) NOT NULL COMMENT '当前最大id',  
  step int(20) NOT NULL COMMENT '号段的布长',  
  biz_typeint(20) NOT NULL COMMENT '业务类型',  
  version int(20) NOT NULL COMMENT '版本号',  
  PRIMARY KEY (`id`)
)
```

- biz_type ：代表不同业务类型。
- max_id ：当前最大的可用 Id。
- step ：代表号段的长度。
- version ：乐观锁，每次都更新 version，保证并发时数据的正确性。

当一批号段 ID 用完，再次向数据库申请新号段，对 max_id 字段做一次 update 操作，`update max_id= max_id + step`，update 成功则说明新号段获取成功，新的号段范围是 (max_id ,max_id +step]。

```sql
update id_generator set max_id = #{max_id+step}, 
version = version + 1 where version = #{version} and biz_type = XXX
```

由于多业务端可能同时操作，所以采用版本号 version 乐观锁方式更新，这种分布式 ID 生成方式不强依赖于数据库，不会频繁的访问数据库，对数据库的压力小很多。

###  Redis

利用 Redis 的原子性操作自增，一般算法为：时间戳 + 自增 Id, 补 0。

优点：

- 有序递增，可读性强。

缺点：

- 占用带宽，每次都要向 Redis 请求；Redis 不能保证强一致性，可能出现重复。

### 雪花算法

雪花算法（Snowflake）是 Twitter 公司内部分布式项目采用的 ID 生成算法，开源后广受国内大厂的好评，在该算法影响下各大公司相继开发出各具特色的分布式生成器。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/systemdesign/雪花算法示意图.png" width="600px">
</div>

如上图所示：

- 1 bit：是不用的，为啥呢？

> 因为二进制里第一个 bit 为如果是 1，那么都是负数，但是我们生成的 ID 都是正数，所以第一个 bit 统一都是 0。

- 41 bit：表示的是时间戳，单位是毫秒。

> 41 bit 可以表示的数字多达 2^41 - 1，可以标识 2 ^ 41 - 1 个毫秒值，换算成年就是表示 69 年的时间。

- 10 bit：记录工作机器 ID，代表的是这个服务最多可以部署在 2^10 台机器上，也就是 1024 台机器。

> 10 bit 里 5 个 bit 代表机房 id，5 个 bit 代表机器 ID。意思就是最多代表 2 ^ 5 个机房（32 个机房），每个机房里可以代表 2 ^ 5 个机器（32 台机器）。

- 12 bit：这个是用来记录同一个毫秒内产生的不同 ID。

> 12 bit 可以代表的最大正整数是 2 ^ 12 - 1 = 4096，也就是说可以用这个 12 bit 代表的数字来区分同一个毫秒内的 4096 个不同的 ID。理论上snowflake方案的QPS约为409.6w/s，这种分配方式可以保证在任何一个IDC的任何一台机器在任意毫秒内生成的ID都是不同的。

根据这个算法，只需要将这个算法用 Java 语言实现出来，封装为一个工具方法，那么各个业务应用可以直接使用该工具方法来获取分布式 ID，只需保证每个业务应用有自己的工作机器 ID 即可，
而不需要单独去搭建一个获取分布式 ID 的应用。

优点：

- 毫秒数在高位，自增序列在低位，ID 趋势递增。
- 不依赖第三方应用，本地生成，生成性能高。
- 可以根据自身业务特性分配 bit 位，非常灵活。

缺点：

- 强依赖机器时钟，时钟回拨可能会导致 ID 重复。

### Leaf

Leaf 由美团开发，Leaf 同时支持号段模式和 Snowflake 算法模式，可以切换使用。

#### Segment

通过 proxy server 批量获取分布式 ID，每次获取一个 segment 号段，用完之后再去数据库获取新的号段，大大的减轻数据库的压力；
各个业务不同的发号需求用 biz_tag 字段来区分，每个 biz-tag 的 ID 获取相互隔离，互不影响。如果以后有性能需求需要对数据库扩容，只需要对 biz_tag 分库分表就行。

建表语句如下：

```sql

DROP TABLE IF EXISTS `leaf_alloc`;

CREATE TABLE `leaf_alloc` (
  `biz_tag` varchar(128)  NOT NULL DEFAULT '' COMMENT '业务key,用来区分业务',
  `max_id` bigint(20) NOT NULL DEFAULT '1' COMMENT '当前已经分配了的最大id',
  `step` int(11) NOT NULL COMMENT '初始步长，也是动态调整的最小步长',
  `description` varchar(256)  DEFAULT NULL COMMENT '业务key的描述',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据库维护的更新时间',
  PRIMARY KEY (`biz_tag`)
) ENGINE=InnoDB;
```

大致架构如下图所示：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/systemdesign/Leaf-segment架构图.png" width="600px">
</div>

test_tag 在第一台 Leaf 机器上是 1~1000 的号段，当这个号段用完时，会去加载另一个长度为 step=1000 的号段，假设另外两台号段都没有更新，这个时候第一台机器新加载的号段就应该是 3001~4000。
同时数据库对应的 biz_tag 这条数据的 max_id 会从 3000 被更新成 4000，更新号段的 SQL 语句如下：

```sql
Begin
    UPDATEtableSETmax_id=max_id+step WHEREbiz_tag=xxx
    SELECTtag, max_id, step FROMtableWHEREbiz_tag=xxx
Commit
```

优点：

- 支持线性扩展，性能能够支撑大多数业务场景。
- ID 是趋势递增的 8byte 的 64 位数字。
- 容灾性高：即使 DB 宕机，短时间内 Leaf 仍能正常对外提供服务。
- 自定义 max_id 大小，方便业务以原有 ID 迁移过来。

缺点：

- ID 不够随机，能够泄露发号数量的信息，不太安全。
- TP999 更新号段，数据库 IO 可能导致用户线程阻塞。
- DB 宕机会造成整个系统不可用。

##### 双 buffer

针对 TP999 采用双 buffer 的方式，Leaf 服务内部有两个号段缓存区 segment。当前号段已下发 10% 时，如果下一个号段未更新，则另启一个更新线程去更新下一个号段。
当前号段全部下发完后，如果下个号段准备好了则切换到下个号段为当前 segment 接着下发，循环往复。

主要特性如下：

- 每个 biz-tag 都有消费速度监控，通常推荐 segment 长度设置为服务高峰期发号 QPS 的 600倍（10分钟），这样即使 DB 宕机，Leaf 仍能持续发号 10-20 分钟不受影响。
- 每次请求来临时都会判断下个号段的状态，从而更新此号段，所以偶尔的网络抖动不会影响下个号段的更新。

具体实现如下图所示：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/systemdesign/Leaf-segment双buffer示意图.png" width="600px">
</div>

#### Snowflake

鉴于 Leaf-segment 方案不适用于美团的订单号这种场景（Leaf-segment 方案可以生成趋势递增的 ID，同时 ID 号是可计算的，很容易被猜出美团每日的订单量这种商业秘密），
所以 Leaf-Snowflake 方案就应运而生了。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/systemdesign/Leaf-Snowflake示意图.png" width="600px">
</div>

Leaf-Snowflake 方案完全沿用 Snowflake 方案的 bit 位设计。对于 workerID 的分配，当服务集群数量较小的情况下，完全可以手动配置。Leaf 服务规模较大，动手配置成本太高。
所以 Leaf-Snowflake 使用 Zookeeper 持久顺序节点的特性自动对 Snowflake 节点配置 wokerID。Leaf-snowflake 是按照下面几个步骤启动的：

- 启动 Leaf-snowflake 服务，连接 Zookeeper，在 leaf_forever 父节点下检查自己是否已经注册过（是否有该顺序子节点）。
- 如果有注册过直接取回自己的 workerID（zk 顺序节点生成的 int 类型 ID 号），启动服务。
- 如果没有注册过，就在该父节点下面创建一个持久顺序节点，创建成功后取回顺序号当做自己的 workerID 号，启动服务。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/systemdesign/Leaf-Snowflake架构图.png" width="600px">
</div>

##### 弱依赖 ZooKeeper

除了每次会去 ZooKeeper 拿数据以外，也会在本机文件系统上缓存一个 workerID 文件。当 ZooKeeper 出现问题，恰好机器出现问题需要重启时，能保证服务能够正常启动，这样做到对第三方组件的弱依赖。

##### 解决时钟问题

这种方案依赖时间，如果机器的时钟发生了回拨，那么就会有可能生成重复的 ID 号。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/systemdesign/Leaf-Snowflake启动流程-处理时钟问题.png" width="600px">
</div>

参见上图整个启动流程图，服务启动时首先检查自己是否写过 ZooKeeper leaf_forever 节点：

- 若写过，则用自身系统时间与 leaf_forever/${self} 节点记录时间做比较，若小于 leaf_forever/${self} 时间则认为机器时间发生了大步长回拨，服务启动失败并报警。
- 若未写过，证明是新服务节点，直接创建持久节点 leaf_forever/${self} 并写入自身系统时间，接下来综合对比其余 Leaf 节点的系统时间来判断自身系统时间是否准确，
具体做法是取 leaf_temporary 下的所有临时节点（所有运行中的 Leaf-snowflake 节点）的服务 IP:Port，然后通过 RPC 请求得到所有节点的系统时间，计算 sum(time)/nodeSize。
- 若 abs( 系统时间-sum(time)/nodeSize ) < 阈值，认为当前系统时间准确，正常启动服务，同时写临时节点 leaf_temporary/${self} 维持租约。
- 否则认为本机系统时间发生大步长偏移，启动失败并报警。
- 每隔一段时间（3s）上报自身系统时间写入 leaf_forever/${self}。

由于强依赖时钟，对时间的要求比较敏感，在机器工作时 NTP 同步也会造成秒级别的回退，建议可以直接关闭 NTP 同步。要么在时钟回拨的时候直接不提供服务直接返回 ERROR_CODE，等时钟追上即可。
或者做一层重试，然后上报报警系统，更或者是发现有时钟回拨之后自动摘除本身节点并报警，如下：

```java
//发生了回拨，此刻时间小于上次发号时间
if (timestamp < lastTimestamp) {
    long offset = lastTimestamp - timestamp;
    if (offset <= 5) {
        try {
          //时间偏差大小小于5ms，则等待两倍时间
            wait(offset << 1);//wait
            timestamp = timeGen();
            if (timestamp < lastTimestamp) {
               //还是小于，抛异常并上报
                throwClockBackwardsEx(timestamp);
              }    
        } catch (InterruptedException e) {  
           throw  e;
        }
    } else {
        //throw
        throwClockBackwardsEx(timestamp);
    }
}
//分配ID
```






