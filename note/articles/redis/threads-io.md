# 多线程 IO

- 目录
  - [单线程 IO 的不足](#单线程-IO-的不足)
  - [多线程 IO 的实现](#多线程-IO-的实现)

在 Redis 6 之前，Redis 通过单线程来处理网络请求和命令执行，Redis 6 引入多线程 IO（Threaded I/O），但多线程部分只是用来处理网络数据的读写和协议解析，执行命令仍然是单线程。之所以这么设计是不想因为多线程而变得复杂，需要去控制 key、lua、事务、LPUSH/LPOP 等并发问题。

### 单线程 IO 的不足

Redis 的单线程 IO 的存在几个缺陷：

- 单一主线程处理请求，只能使用单个 CPU 的核，无法利用多核特性。
- 如果出现大 key，占用较大 IO，会拉低整体 QPS。 

Redis 主线程的时间消耗主要在两个方面：

- 逻辑计算的消耗。
- 同步 IO 读写，拷贝数据导致的消耗。

当 value 比较大时，瓶颈会先出现在同步 IO 上(假设带宽和内存足够)，这部分消耗在于两部分：

- 从 socket 中读取请求数据，会从内核态将数据拷贝到用户态（read 调用）。
- 将数据回写到 socket，会将数据从用户态拷贝到内核态（write 调用）。

这部分数据读写会占用大量的 CPU 时间，也直接导致了瓶颈。如果能有多个线程来分担这部分消耗，那 Redis 的 QPS 还能更上一层楼，这也是 Redis 引入多线程 IO 的目的。

### 多线程 IO 的实现

如何用多线程分担 IO 的负荷，其做法用简单的话来说就是：

- 用一组单独的线程专门进行 read/write socket 读写调用 （同步IO）。
- 读回调函数中不再读数据，而是将对应的连接追加到可读 clients_pending_read 的链表。
- 主线程将 IO 任务分给 IO 线程组，并自旋式等 IO 线程组处理完，再继续往下。
- IO 线程组要么同时在读，要么同时在写。
- 命令的执行由主线程串行执行(保持单线程)。
- IO线程数量可配置。

默认情况下，Redis 多线程是禁用的，我们可以在配置文件 `redis.conf` 开启：

```C
# 开启多线程 IO
io-threads-do-reads yes
 
# 配置线程数量，如果设为 1 就是主线程模式。
io-threads 4
```

多线程下数据交互关系：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/多线程下数据交互关系.png" width="600px">
</div>

多线程下执行流程：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/多线程下执行流程.png" width="600px">
</div>

流程简述：

- 主线程负责接收建立连接请求，获取 socket 放入全局等待读处理队列。
- 主线程处理完读事件之后，通过 RR(Round Robin) 将这些连接分配给这些 IO 线程。
- 主线程阻塞等待 IO 线程读取 socket 完毕。
- 主线程通过单线程的方式执行请求命令。
- 主线程阻塞等待 IO 线程将数据回写 socket 完毕。
- 解除绑定，清空等待队列。

特点：

- IO 线程要么同时在读 socket，要么同时在写，不会同时读或写（我觉得是为了避免处理读写并发的安全问题）。
- IO 线程只负责读写 socket 解析命令，不负责命令处理。