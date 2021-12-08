# 分布式 Id

- 目录
    - [UUID](#UUID)
    - [数据库自增 ID](#数据库自增-ID)
    - [数据库多实例自增 ID](#数据库多实例自增-ID)
    - [号段模式](#号段模式)
    - [Redis](#Redis)
    - [雪花算法](#雪花算法)

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