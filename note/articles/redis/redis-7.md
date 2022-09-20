# Redis 7.0 总览

- 目录
  - [Function](#Function)
  - [Multi-part AOF](#Multi-part-AOF)
  - [Client-eviction](#Client-eviction)
  - [ACL v2](#ACL-v2)
  - [harded-pubsub](#harded-pubsub)
  - [listpack](#listpack)

Redis 始终保持着 1-2 年发布大版本的迭代速度，而每个历史大版本都有一些重要的核心特性出现。比如 Redis 3.0 中的 Cluster 解决了 Redis 的单机瓶颈，将其从主从架构变成了分布式集群架构；4.0 版本的 lazy-free 彻底解决了大 Key 删除阻塞的运维痛点，modules 则将 Redis 的功能进行了进一步拓展，使其能够实现更多能力，比如 RedisSearch 带来了全文搜索能力；5.0 版本的 Stream 使 Redis 真正意义上成为了轻量级的消息队列；6.0 版本的多线程 IO 和 SSL 提高了 Redis 的性能和安全性。

所有核心特性都在保持 Redis 稳定性的前提下，围绕着性能、扩展性、安全场景和拓展四个方面不断增强。

在 2022 年 4 月正式发布的 Redis 7.0，它是目前 Redis 历史版本中变化最大的版本。首先，它有超过 50 个以上新增命令；其次，它有大量核心特性的新增和改进。

Redis 7.0 新增的核心特性不仅能够解决一些之前难以解决的使用问题，同时也将 Redis 的使用场景进行了进一步拓展。

### Function

Function 是Redis脚本方案的全新实现，在 Redis 7.0 之前用户只能使用 EVAL 命令族来执行 Lua 脚本，但是 Redis 对 Lua 脚本的持久化和主从复制一直是 undefined 状态，在各个大版本甚至 release 版本中也都有不同的表现。因此社区也直接要求用户在使用 Lua 脚本时必须在本地保存一份（这也是最为安全的方式），以防止实例重启、主从切换时可能造成的 Lua 脚本丢失，维护 Redis 中的 Lua 脚本一直是广大用户的痛点。

Function 的出现很好的对 Lua 脚本进行了补充，它允许用户向 Redis 加载自定义的函数库，一方面相对于 EVALSHA 的调用方式用户自定义的函数名可以有更为清晰的语义，另一方面 Function 加载的函数库明确会进行主从复制和持久化存储，彻底解决了过去 Lua 脚本在持久化上含糊不清的问题。

自 7.0 开始，Function 命令族和 EVAL 命令族有了各自明确的定义：`FUNCTION LOAD` 会把函数库自动进行主从复制和持久化存储；而 `SCRIPT LOAD` 则不会进行持久化和主从复制，脚本仅保存在当前执行节点。并且社区也在计划后续版本中让 Function 支持更多语言，例如 JavaScript、Python。

总的来说，Function 在 7.0 中被设计为数据的一部分，因此能够被保存在 RDB、AOF 文件中，也能通过主从复制将 Function 由主库复制到所有从库，可以有效解决之前 Lua 脚本丢失的问题，建议逐步将 Redis 中的 Lua 脚本替换为 Function。

### Multi-part AOF

AOF 是 Redis 数据持久化的核心解决方案，其本质是不断追加数据修改操作的 redo log，那么既然是不断追加就需要做回收也即 compaction，在 Redis 中称为 AOF Rewrite。

AOF 触发 Rewrite 的时候，Redis 会先将全量数据做内存快照，然后落盘。落盘的漫长过程中会产生增量数据，此时 Redis 会开辟一块新的内存空间来记录这些增量数据，这就带来了额外的内存开销。在极端情况下，AOF Rewrite 过程中的额外内存占用会与 Redis 的数据内存几乎相等，极易发生 OOM ,因此也被迫需要在操作系统中预留更多内存来避免 Redis OOM 的发生，这也是 Redis 内存资源浪费的重要原因。

此外，在 AOF Rewrite 的最后，Redis 会将全量数据与增量数据做一次合并操作，导致一份数据带来两次磁盘 IO。 同时在 AOF 的合并过程中，主进程和子进程之间的数据交互也会占用 CPU 资源。

所以在 AOF Rewrite 过程中，内存、IO、CPU 都会被占用，而这些都是额外的负担，非常影响业务。因此，通常需要将 AOF 自动 Rewrite 改在业务低峰期，通过脚本触发，甚至关闭 AOF。

`Multi-part AOF` 彻底解决了上述问题。

Multi-part AOF 是对 AOF 的彻底改造。它将 AOF 分为三个部分，分别是 base AOF、incr AOF 和 History AOF 。其中 base AOF 用来记录 AOF Rewrite 时刻的全量内存数据，incr AOF 用来记录 rewrite 过程中所有增量数据。incr AOF 的出现，使 Redis 不需要再开辟新的内存空间来记录增量数据。而多文件设计的理念也使得新版 AOF 无需做数据合并，因为它的全量和增量被放在不同文件中，天然隔离。在 AOF React 的最后，此前的历史 AOF 文件都会成为 history AOF 被直接删除，因而也不存在合并。为了管理这些AOF文件，还引入了一个 manifest（清单）文件来跟踪、管理这些 AOF。

另一个重要改进是 AOF 的增量数据带上了时间戳。在此之前，如果误操作造成数据损坏需要对数据进行恢复，通常需要先用最近一次的 RDB 全量备份做基础数据，然后用 AOF 文件做增量数据恢复。但由于 AOF 中的数据并没有时间戳，因此需要进行繁杂的人工分析。在没有时间信息的情况下，人工找到异常位置难度极大且容易出错，而新版 AOF 中时间戳的加入可以大幅度减轻人工分析的复杂度。

### Client-eviction

Redis 内存占用主要分为三个部分： data 是用户数据的内存占用， metadata 是元数据的内存占用，client buffer 是连接内存占用。在之前的版本中，可以通过 maxmemory 对 Redis 的内存上限进行控制，但数据占用内存和连接占用内存并不会被 maxmemory 区分。虽然 Redis 提供了参数，比如 clientoutput、buffer limit ，允许对每个连接的内存占用进行限制，但它并不能解决总连接内存的占用。

所以一旦 Redis 连接较多，再加上每个连接的内存占用都比较大的时候， Redis 总连接内存占用可能会达到 maxmemory 的上限，出现内存未被数据用尽却无法写入数据的情况，进而导致丢数据。为了解决上述问题，通常需要给 Redis 分配更多内存来避免连接内存太大而影响业务或数据。

Redis 7.0 新增了 client-invocation 参数，能够从全局的角度对 Redis 连接总内存占用进行控制。举个例子，如果连接总内存占用超过配置上限， Redis 会优先清理内存占用较大的连接，在一定程度上实现了对内存占用和数据内存占用的隔离控制，能够更好地管理 Redis 内存，节约内存使用成本，无须再预留过多内存。

### ACL v2

Redis 6.0 大版本中引入了 ACL v1，虽然能够实现一定程度的权限控制，但实用性并不强，比如无法支持粒度至 KEY 的权限访问控制，所有 KEY 的权限必须一致。而在 Redis 7.0 中， ACL v2 正式支持粒度至 KEY 的权限访问控制，可以轻松实现账户对不同 KEY 有不同的权限访问控制。

### Sharded-pubsub

Redis 自 2.0 开始便支持发布订阅机制，使用 pub/sub 命令族用户可以很方便地建立消息通知订阅系统，但是在 Cluster 集群模式下 Redis 的 pub/sub 存在一些问题，最为显著的就是在大规模集群中带来的广播风暴。

Redis 的 pub/sub 是按 channel 频道进行发布订阅，然而在集群模式下 channel 不被当做数据处理，也即不会参与到 hash 值计算无法按 slot 分发，所以在集群模式下 Redis 对用户发布的消息采用的是在集群中广播的方式。

那么问题显而易见，假如一个集群有 100 个节点，用户在节点 1 对某个 channel 进行 publish 发布消息，该节点就需要把消息广播给集群中其他 99 个节点，如果其他节点中只有少数节点订阅了该频道，那么绝大部分消息都是无效的，这对网络、CPU 等资源造成了极大的浪费。

Sharded-pubsub 便是用来解决这个问题，意如其名，sharded-pubsub 会把 channel 按分片来进行分发，一个分片节点只负责处理属于自己的 channel 而不会进行广播，以很简单的方法避免了资源的浪费。

### listpack

listpack 压缩列表。作为 ziplist 的替代品，从 2017 年引入 Redis 后，到 Redis 7.0 已经完全取代 ziplist。