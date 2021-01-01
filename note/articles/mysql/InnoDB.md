# InnoDB 存储引擎

### 概述

InnoDB 存储引擎最早由 Innobase Oy 公司开发，从 MySQL 数据库 5.5.8 版本开始，InnoDB 存储引擎是默认的存储引擎。该存储引擎是第一个完整支持 ACID 事务的 MySQL 存储引擎 （BDB 是第一个支持事务的 MySQL 存储引擎，已停止开发），其特点是行锁设计、支持 MVCC、支持外键、提供一致性非锁定读，同时被设计用来有效利用内存和 CPU。

### InnoDB 体系架构

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/mysql/InnoDB体系结构.png" width="600px">
</div>

上图简单展示了 InnoDB 存储引擎的体系架构。InnoDB 存储引擎由很多内存块组成一个大的内存池，主要负责如下工作：
- 维护所有进程/线程需要访问的多个内部数据结构。
- 缓存磁盘上的数据结构方便快速地读取，同时在对磁盘文件的数据修改之前在这里做缓存。
- 重做日志（redo log）缓冲。
后台线程主要作用是负责刷新内存池中数据，保证缓冲池的内存缓存的是最近的数据。此外将已修改的数据文件刷新到磁盘文件，同时保证在数据库异常情况 InnoDB 存储引擎能恢复到正常运行状态。
  
### 后台线程

InnoDB存储引擎是多线程的模型，因此后台有多个不同的线程，负责处理不同的任务

`Master Thread` ：Master Thread 是一个非常核心的后台线程，负责将缓冲池中的数据异步刷新到磁盘，保证数据的一致性，包括脏页的刷新，合并插入缓冲，undo 页的回收等

`IO Thread` ：InnoDB 存储引擎大量使用 AIO（Async IO）来处理写IO请求，而 IO Thread 主要负责这些 IO 请求的回调（call back）处理。InnoDB 存储引擎拥有四周 IO Thread，分别是 write thread、read thread、insert buffer thread 和 log thread。

`Purge Thread` ：Purge Thread 用于回收事务提交后不再需要的 undo 页。在 InnoDB 1.1 版本前，purge 操作仅在 Master Thread 中完成。从 InnoDB 1.1 版本开始，purge 操作可以通过命令启用独立的 Purge Thread 来回收 undo 页，以此减轻 Master Thread 的压力，从而提升 CPU 的使用率和存储引擎的性能。

`Page Cleaner Thread` ：Page Cleaner Thread 是 InnoDB  1.2.x 版本中引入的，作用是将刷新脏页操作放到独立线程处理，减轻 Master Thread 的压力 和 用户查询线程的阻塞，进一步提升 InnoDB 存储引擎的性能。

### 缓冲池

InnoDB 存储引擎是基于磁盘存储的，并将其中的记录按照页的方式进行管理。由于CPU速度和磁盘速度之间的鸿沟，需要使用缓冲池技术来提高数据库的整体性能，缓冲池简单来说就是一块内存区域，通过内存的速度来弥补磁盘速度对数据库性能的影响。

在数据库中进行页的读取操作，首先将从磁盘读到页存放的缓冲池中，这个过程叫做将页“FIX”在缓冲池中，下次读取相同页的时候首先读取缓冲池，未命中再读取磁盘中的页。对于数据库中页的写操作，首先修改在缓冲池中的页，然后以一定频率刷新到磁盘上（注意：页从缓冲池刷新回磁盘并不是每次页发生更新时触发，是通过 Checkpoint 机制刷回磁盘）。缓冲池的大小直接影响到数据库整体性能。

缓冲池中缓存的数据页类型有：索引页、数据页、undo 页、插入缓冲（insert buffer）、自适应哈希索引、InnoDB 存储的锁信息、数据字典信息等。从 InnoDB 1.0.x 版本开始，允许有多个缓冲池实例，每页根据哈希值分配到不同缓冲池实例。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/mysql/InnoDB内存数据对象.png" width="600px">
</div>

InnoDB 存储引擎的内存区域除了缓冲池外，还有重做日志缓冲（redo log buffer）。InnoDB 存储引擎首先将重做日志放到这个缓冲区，然后按照一定频率将其刷新到重做日志文件。

### LRU 算法

InnoDB 通过 LRU（Latest Recent Used，最近最少使用）算法来管理缓冲池，即最频繁使用的页在 LRU 列表前端，最少使用的页在 LRU 列表尾端，当缓冲池中不能存放新读取的页时，将首先释放 LRU 列表尾端的页。

InnoDB 存储引擎对传统 LRU算法做了优化。在 InnoDB 存储引擎中，LRU 列表中加入 midpoint 位置，新读到的页不是放到 LRU 列表前端，而且放到 midpoint 位置，默认配置该位置在 LRU 列表长度的 5/8 处，由参数 `innodb_old_blocks_pct` 控制。InnoDB 中把 midpoint 位置之前的列表称为 new 列表，之后的列表称为 old 列表。InnoDB 存储引擎通过 `innodb_old_block_time` 参数表示 midpoint 位置后需要等待多久才会被加入到 LRU 列表前端。之所以这样处理，是为了防止索引或数据扫描操作访问了不活跃的页，导致 LRU 列表被污染。

LRU 列表用来管理已经读取的页，当数据库刚启动时，LRU 列表是空的，这时页都存放在 Free 列表中，当需要从缓冲池中分页时，首先从 Free 列表中查找是否有可用的空闲页，如果有则将该页从 Free 列表移除，放入 LRU 列表。

在 LRU 列表中的页被修改，称该页为脏页（dirty page），即缓冲池中的页和磁盘上的页数据不一致。这时候数据库会通过 Checkpoint 机制将脏页刷回磁盘，而 Flush 列表中的页即为脏页。脏页即存在于 LRU 列表中，也存在于 Flush 列表中，LRU 列表中脏页保持缓冲池中页的可用性，Flush 列表中的脏页用来将数据刷回磁盘。

### Checkpoint 机制

每次写操作会产生脏页，数据库需要将脏页从缓冲池中刷回磁盘。倘若每次修改页都将新页刷回磁盘，这个开销十分大，而且如果热点数据是某几个页，会导致性能很差，同时如果在刷新脏页的时候数据库宕机了，那么数据难以恢复。为了避免这些问题，当前事务数据库往往采用  Write Ahead Log 策略，即当事务提交先写重做日志（redo log），在修改页，即使数据库宕机也可以通过重做日志恢复数据。

通过重做日志，可以实现 ACID 中的持久性，但是如果重做日志不可能无限大，即使是无限大，如果数据库宕机通过重做日志恢复数据耗时会很久，代价很大。因此产生了 Checkpoint （检查点） 机制，目的是：
- 缩短数据库恢复时间
- 缓冲池不够用时，刷新脏页
- 重做日志不可以时，刷新脏页

当数据库宕机，不需要重做所有日志，因为 Checkpoint 之前的页都已经刷新回磁盘，数据库只需要对 Checkpoint 之后的重做日志进行恢复。当缓冲池不够用的时候，根据 LRU 算法会溢出最近最少使用的页，若为脏页则强制执行 Checkpoint 刷新脏页。
当重做日志不可以使用，也必须强制执行 Checkpoint，将缓冲池中的页至少刷新到当前重做日志的位置。重做日志出现不可以的情况是因为当前事务数据库系统对重做日志的设计是循环使用，并不是无限扩大，数据库可以重用已经不在需要的日志部分。

InnoDB 存储引擎有两种 Checkpoint,分别是 Sharp CheckPoint 和 Fuzzy CheckPoint。Sharp CheckPoint 发生在数据库关闭时将所以脏页刷回磁盘，但如果数据库运行刷时也使用会影响数据库的可用性，故 InnoDB 存储引擎内部
使用 Fuzzy CheckPoint 进行脏页刷新，即只刷新一部分脏页。

Fuzzy CheckPoint 存在以下几种情况：

`Master Thread CheckPoint` ：Master Thread 中发生的 CheckPoint 约每秒或每十秒从缓冲池脏页中异步刷新一定比例脏页回磁盘，不会阻塞用户线程。

`FLUSH_LRU_LIST CheckPoint` ：InnoDB 1.1.x 版本之前，LRU需要保证拥有 100 个可以空闲页，当空间不够回淘汰列表尾端，如果存在脏页则通过 CheckPoint 刷新脏页。InnoDB 1.2.x 版本开始这个检查由单独的 Page Cleaner Thread 进行。

`Async/Sync Flush CheckPoint` ：当重做日志不可用，强制将一部分页刷回磁盘，InnoDB 1.2.x 版本之前,Async 线程回阻塞发现问题的线程，Sync 线程回阻塞所有用户线程，从1.2.x 版本开始这部分操作页由 Page Cleaner Thread 进行，故不会阻塞用户线程。

`Dirty Page too much CheckPoint` ：当脏页太多的时候，InnoDB 会强制进行 Checkpoint，可以通过 `innodb_max_dirty_pages_pct` 参数配置容量阈值。

### Master Thread 工作模式

InnoDB 存储引擎主要工作都是由 Master Thread 完成的，随着 InnoDB 版本迭代， Master Thread 也在持续优化。

`InnoDB 1.0.x 版本之前` ：Master Thread 具有最高的线程优先级别，内部有多个循环（loop）组成，主循环（loop），后台循环（backgroup loop），刷新循环（flush loop），暂停循环（suspend loop），Master Thread 会根据数据库状态在各个循环之间切换。Loop 被称为主循环，大多数操作都由它完成，主要分为两大部分操作————每秒一次操作和每 10 秒一次操作。
所谓的每秒一次和每 10 秒一次其实也是不准确，只是大概保持这个频率，通过 thread sleep 实现，实际上负载很大情况下可能存在延迟。

每秒一次操作：
- 日志缓冲刷新到磁盘，即使这个事务还没有提交（总是）；
- 合并插入缓冲（可能）；
- 至多刷新 100 个 InnoDB 的缓冲池中的脏页到磁盘（可能）；
- 如果当前没有用户活动，切换到后台循环（可能）。

每 10 秒一次操作：
- 刷新 100 个脏页到磁盘（可能）；
- 合并至少 5 个插入缓冲（总是）；
- 将日志缓冲刷新到磁盘（总是）；
- 删除无用的 undo 页（总是）；
- 刷新 100 个或者 10 个脏页到磁盘（总是）。

可以看到，即使事务没有提交，InnoDB 存储引擎也会每秒将重做日志缓冲中内容刷到重做日志文件中，这很好地解释了为什么再大的事务提交时间也很短。
InnoDB 存储引擎会根据最近数据状态来决定是否执行合并插入缓冲、刷新脏页等操作，如果当前没有用户活动会切换到后台循环。

后台循环（backgroup loop）操作：
- 删除无用 undo 页（总是）；
- 合并 20 个插入缓冲（总是）；
- 跳回主循环（总是）；
- 不断刷新 100 个页知道符合条件（可能，跳转到刷新循环中完成）。

如果刷新循环（flush loop）中也无事可做，InnoDB 存储引擎会切换到暂停循环，挂起 Master Thread 等待事件发生。

`InnoDB 1.2.x 版本之前` ： 在 InnoDB 1.0.x 版本之前，Master Thread 做了大量地刷新脏页、合并缓冲等操作，Master Thread 负载较大。 从 InnoDB 1.0.x 版本开始引入了 `innodb_io_capacity` 参数来控制磁盘 IO 吞吐量，合并插入缓冲和刷新脏页数量会受到 innodb_io_capacity 限制。
另一个问题， `innodb_max_dirty_pages_pct` 参数在 InnoDB 1.0.x 版本之前默认值是 90，意味着脏页占缓冲池的 90%，从 InnoDB 1.0.x 版本开始这个参数默认值修改为 75，这样即可以加快脏页刷新频率又保证了磁盘负载。
InnoDB 1.0.x 版本还带来了两个个参数 `innodb_adaptive_flushing` 和 `innodb_purge_batch_size` ， innodb_adaptive_flushing 影响每秒刷新脏页频率，innodb_purge_batch_size 控制每次 full purge 回收 undo 页的数量。此外，从 InnoDB 1.1 版本开始，purge 操作可以通过命令启用独立的 Purge Thread 来回收 undo 页，以此减轻 Master Thread 的压力，从而提升 CPU 的使用率和存储引擎的性能。

`InnoDB 1.2.x 版本` ： InnoDB 1.2.x 版本对 Master Thread 进一步优化，将刷新脏页操作从 Master Thread 线程分离到一个单独的 Page Cleaner Thread 中，从而提高系统并发性。 

### InnoDB 关键特性

InnoDB关键特性包括：
- 插入缓冲（Insert Buffer）
- 两次写（Double Write）
- 自适应哈希索引（Adaptive Hash Index）
- 异步IO（Async IO）
- 刷新邻接页（Flush Neighbor Page）

`插入缓冲（Insert Buffer）` ： Insert Buffer 听名字似乎与缓冲池有关，而缓冲池中也有 Insert Buffer 的信息，但插入 Insert Buffer 和数据页一样是物理页的组成部分。

在 InnoDB 存储引擎中，主键是行的唯一表示符，即使没有显式定义主键，InnoDB 存储引擎会为每一行生成一个 6 字节的 _rowid，并以此作为主键。通常应用程序是按照主键自增的顺序插入数据，这样的话插入数据就不需要随机读取另一个页的数据，因此这类情况下插入操作速度非常快。而一张表往往不是只有一个聚集索引的，大多情况下一张表存在多个辅助索引，这样插入操作时候对于辅助索引来说节点的插入不再是顺序的，存在随机读取导致插入性能下降。

InnoDB 存储引擎设计了Insert Buffer，对于辅助索引的插入或更新操作，不是直接插入到索引页，而是先判断插入的辅助索引页是否在缓冲池，如果在则插入；若不在则先放到一个 Insert Buffer 对象中，在以一定频率将 Insert Buffer 和 辅助索引页合并，这样避免了每次操作都产生随机读取页。大大提升了辅助索引插入的性能。要注意的是，InnoDB 存储引擎使用 Insert Buffer 有两个条件：一是索引是辅助索引，而是索引不是唯一的。第一点已经做过说明，第二点是因为如果插入缓冲时候去查询索引页判断唯一性，就势必产生随机读取，从而失去了 Insert Buffer 的意义。

InnoDB 1.0.x 版本引入了 Change Buffer,可以视为 Insert Buffer 的升级，这个版本开始 InnoDB 存储引擎可以对DML操作————INSERT、DELET、UPDATE都进行缓冲，分别是：Insert Buffer、Delete Buffer、Purge Buffer。和 Insert Buffer 一样，Change Buffer 使用的对象依然是非唯一的辅助索引。


`两次写（Double Write）` ：Double Write 保证了 InnoDB 存储引擎数据页的可靠性，当数据库宕机时，可能 InnoDB 存储引擎正在写入某个页到表中，而这个页写了一部分之后发生了宕机，这种情况称为部分写失效，InnoDB 存储引擎在重做日志之前，先通过页的副本还原该页，在进行重做，这就是 Double Write。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/mysql/DoubleWrite体系架构.png" width="600px">
</div>

上图是 InnoDB 存储引擎中 Double Write 体系架构图，由两部分组成：一部分是内存中的 doublewrite buffer,大小为 2MB，另一部分是物理磁盘上共享表空间中连续的 128 个页，即两个区，大小同样为 2MB。在对缓冲池的脏页进行刷新的时候，先将脏页复制到内存中的 doublewrite buffer,之后分两次每次 1MB 顺序地写入共享表空间的物理磁盘上，然后马上调用 fsync 函数，同步磁盘，避免缓冲写带来的问题。
在这个过程中，因为共享表空间中 doublewrite 页是连续的，所以这个过程是顺序写，开销不是很大。完成 doublewrite 页写入后再将 doublewrite buffer 中的页写入各个表空间文件中，最后的写入是离散的。

如果操作系统将页的数据写入磁盘的过程中发生了崩溃，在恢复过程中，InnoDB存储引擎可以从共享表空间中的 doublewrite 中找到该页的副本将其复制到表空间文件中，再应用重做日志。

`自适应哈希索引（Adaptive Hash Index）` ：哈希是一种快速查找方法，一般情况查找的事件复杂的为 O(1)，即一次查找就能定位，而 B+ 树索引的查找次数取决于树高。InnoDB 存储引擎会自动根据表索引页的访问频率和模式自动地为热点页建立哈希索引，称之为自适应哈希索引。

`异步IO（Async IO）` ：为了提高磁盘操作性能，InnoDB 存储引擎采用异步 IO（AIO） 的方式来处理磁盘操作。 AIO 一方面不需要阻塞线程，另一方面 AIO 可以将多个 IO 操作合并成一个 IO 操作，提升 IO 性能。

`刷新邻接页（Flush Neighbor Page）` ：InnoDB 存储引擎在刷新一个脏页时候，会检测该页所在区的所有页。如果是脏页则一起进行刷新，这样做可以提高 AIO 将多个 IO 合并成一个 IO 操作，对于机械磁盘有着显著优势，但如果是固态硬盘建议通过 `innodb_flush_neighbors` 参数关闭此特性。


### 结语

本章对 InnoDB 存储引擎做了分析，基本上我们使用 MySQL 数据库就是使用 InnoDB 存储引擎，了解其体系结构和特性是很有必要的。