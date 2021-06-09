# 事件模块

- 目录
    - [事件的定义](#事件的定义)
    - [ngx_events_module](#ngx_events_module)
    - [事件驱动模型](#事件驱动模型])
      - [select](#select)
      - [poll](#poll)
      - [epoll](#epoll)
  
Nginx 是一个事件驱动架构的 Web 服务器，事件驱动架构所要解决的问题是如何收集、管理、分发事件。
这里所说的事件，主要以网络事件和定时器事件为主，而网络事件中又以 TCP 网络事件为主（Nginx 毕竟是个Web服务器），本章将围绕这两种事件为中心论述。

### 事件的定义

在 Nginx 中，每一个事件都由 ngx_event_t 结构体表示的

ngx_event_t：

```C
struct ngx_event_s {
    // 事件相关的对象。通常 data 都是指向 ngx_connection_t 连接对象
    void            *data;

    // 标志位，为 1 时表示事件是可写的。
    unsigned         write:1;

    // 标志位，为 1 时表示为此事件可以建立新的连接。
    unsigned         accept:1;

    // 用于区分当前事件是否是过期的，它仅仅是给事件驱动模块使用的，而事件消费模块可不用关心
    unsigned         instance:1;

    // 标志位，为1时表示当前事件是活跃的，为0时表示事件是不活跃的。
    unsigned         active:1;

    // 标志位，为1时表示禁用事件，仅在 kqueue 或者 rtsig 事件驱动模块中有效，而对于 epoll 事件驱动模块则无意义。
    unsigned         disabled:1;

    // 标志位，为1时表示当前事件已经准备就绪，
    unsigned         ready:1;

    // 仅在 kqueue 或者 rtsig 事件驱动模块中有效，而对于 epoll 事件驱动模块则无意义。
    unsigned         oneshot:1;

    /* aio operation is complete */
    //  标志位，为1时表示 AIO 操作完成
    unsigned         complete:1;

    // 标志位，为 1 时表示当前处理的字符流已经结束
    unsigned         eof:1;

    // 标志位，为 1 时表示事件在处理过程中出现错误
    unsigned         error:1;

    // 标志位，为1时表示这个事件已经超时，用以提示事件的消费模块做超时处理
    unsigned         timedout:1;
    
    // 标志位，为1时表示这个事件存在于定时器中
    unsigned         timer_set:1;

    // 标志位，delayed为1时表示需要延迟处理这个事件，它仅用于限速功能
    unsigned         delayed:1;

    unsigned         deferred_accept:1;

    // 标志位，为1时表示等待字符流结束，它只与kqueue和aio事件驱动机制有关
    unsigned         pending_eof:1;

    unsigned         posted:1;

    // 标志位，为1时表示当前事件已经关闭，epoll 模块没有使用它
    unsigned         closed:1;

    /* to test on worker exit */
    unsigned         channel:1;
    unsigned         resolver:1;
    unsigned         cancelable:1;

#if (NGX_HAVE_KQUEUE)
    unsigned         kq_vnode:1;

    /* the pending errno reported by kqueue */
    int              kq_errno;
#endif

    /*
     * kqueue only:
     *   accept:     number of sockets that wait to be accepted
     *   read:       bytes to read when event is ready
     *               or lowat when event is set with NGX_LOWAT_EVENT flag
     *   write:      available space in buffer when event is ready
     *               or lowat when event is set with NGX_LOWAT_EVENT flag
     *
     * iocp: TODO
     *
     * otherwise:
     *   accept:     1 if accept many, 0 otherwise
     *   read:       bytes to read when event is ready, -1 if not known
     */
    // 标志位，在 epoll 事件驱动机制下表示一次尽可能多地建立TCP连接，它与 multi_accept 配置项对应
    int              available;
    
    // 这个事件发生时的处理方法，每个事件消费模块都会重新实现它
    ngx_event_handler_pt  handler;


#if (NGX_HAVE_IOCP)
    ngx_event_ovlp_t ovlp;
#endif

    ngx_uint_t       index;

    ngx_log_t       *log;

    ngx_rbtree_node_t   timer;

    /* the posted queue */
    ngx_queue_t      queue;

#if 0

    /* the threads support */

    /*
     * the event thread context, we store it here
     * if $(CC) does not understand __thread declaration
     * and pthread_getspecific() is too costly
     */

    void            *thr_ctx;

#if (NGX_EVENT_T_PADDING)

    /* event should not cross cache line in SMP */

    uint32_t         padding[NGX_EVENT_T_PADDING];
#endif
#endif
};
```

每个事件最核心的部分是 handler 回调方法，它将由每一个事件消费模块实现，以此决定这个事件究竟如何被 "消费"。

作为 Web 服务器，每一个用户请求至少对应着一个 TCP 连接，为了及时处理这个连接，至少需要一个读事件和一个写事件，使得 epoll 可以有效地根据触发的事件调度相应模块读取请求或者发送响应。
因此，Nginx 中定义了基本的数据结构 ngx_connection_t 来表示连接，这个连接表示是客户端主动发起的、Nginx 服务器被动接受的 TCP 连接，我们可以简单称其为被动连接。

### ngx_events_module

ngx_events_module 模块是一个核心模块，它定义了一类新模块：事件模块。它的功能如下：定义新的事件类型，并定义每个事件模块都需要实现的 ngx_event_module_t 接口，
还需要管理这些事件模块生成的配置项结构体，并解析事件类配置项，

```C
static ngx_command_t  ngx_events_commands[] = {

    { ngx_string("events"),
      NGX_MAIN_CONF|NGX_CONF_BLOCK|NGX_CONF_NOARGS,
      ngx_events_block,
      0,
      0,
      NULL },

      ngx_null_command
};
```

ngx_event_core_module 模块是一个事件类型的模块，它在所有事件模块中的顺序是第一位（configure 执行时必须把它放在其他事件模块之前）。
这就保证了它会先于其他事件模块执行，由此它选择事件驱动机制的任务才可以完成。

```C
static ngx_command_t  ngx_event_core_commands[] = {

    /* 连接池的大小，也就是每个 worker 进程中支持的 TCP 最大连接数 */
    { ngx_string("worker_connections"),
      NGX_EVENT_CONF|NGX_CONF_TAKE1,
      ngx_event_connections,
      0,
      0,
      NULL },

    /* 确定选择哪一个事件模块作为事件驱动机制 */
    { ngx_string("use"),
      NGX_EVENT_CONF|NGX_CONF_TAKE1,
      ngx_event_use,
      0,
      0,
      NULL },

    /* 对应事件定义 ngx_event_s 结构体的成员 available 字段。对于 epoll 事件驱动模式来说，
     * 意味着在接收到一个新连接事件时，调用 accept 以尽可能多地接收连接 */
    { ngx_string("multi_accept"),
      NGX_EVENT_CONF|NGX_CONF_FLAG,
      ngx_conf_set_flag_slot,
      0,
      offsetof(ngx_event_conf_t, multi_accept),
      NULL },

    /* 确定是否使用 accept_mutex 负载均衡锁，默认为开启 */
    { ngx_string("accept_mutex"),
      NGX_EVENT_CONF|NGX_CONF_FLAG,
      ngx_conf_set_flag_slot,
      0,
      offsetof(ngx_event_conf_t, accept_mutex),
      NULL },

    /* 启用 accept_mutex 负载均衡锁后，延迟 accept_mutex_delay 毫秒后再试图处理新连接事件 */
    { ngx_string("accept_mutex_delay"),
      NGX_EVENT_CONF|NGX_CONF_TAKE1,
      ngx_conf_set_msec_slot,
      0,
      offsetof(ngx_event_conf_t, accept_mutex_delay),
      NULL },

    /* 需要对来自指定 IP 的 TCP 连接打印 debug 级别的调试日志 */
    { ngx_string("debug_connection"),
      NGX_EVENT_CONF|NGX_CONF_TAKE1,
      ngx_event_debug_connection,
      0,
      0,
      NULL },

      ngx_null_command
};
```

### 事件驱动模型

事件驱动模型有个问题，就是事件什么时候完成，如何告知调用方。

事件轮询 API 就是用来解决这个问题的，这是一种多路复用机制。常见的事件轮询 API 有 select、poll、epoll，它们是操作系统提供给用户线程的 API，用于取代用户线程轮询。
如果是用户线程轮询就要涉及用户态和内核态的频繁切换，这部分开销是巨大的。

#### select

最简单事件轮询 API 是 select，下面是 select 函数：

```C
int select(int maxfdp1,fd_set *readset,fd_set *writeset,fd_set *exceptset,const struct timeval *timeout);
```

select() 的机制中提供一种 fd_set 的数据结构，实际上是一个 long 类型的数组，每一个数组元素都能与一打开的文件句柄（不管是 Socket 句柄,还是其他文件或命名管道或设备句柄）建立联系，当调用 select() 时，由内核根据 IO 状态修改 fd_set 的内容，由此来通知执行了 select() 的进程哪一 socket 或文件可读写。

使用 select() 最大的优势是可以在一个线程内同时处理多个 socket 的 IO 请求。用户可以注册多个 socket，然后不断地调用 select() 读取被激活的 socket，即可达到在同一个线程内同时处理多个 IO 请求的目的。但 select 存在三个问题：一是为了减少数据拷贝带来的性能损坏，内核对被监控的 fd_set 集合大小做了限制，并且这个是通过宏控制的，大小不可改变(限制为 1024)；二是每次调用 select()，都需要把 fd_set 集合从用户态拷贝到内核态，如果 fd_set 集合很大时，那这个开销很大；三是每次调用 select() 都需要在内核遍历传递进来的所有 fd_set，如果 fd_set 集合很大时，那这个开销也很大。

#### poll

poll 的机制与 select 类似，与 select 在本质上没有多大差别，管理多个描述符也是进行轮询，根据描述符的状态进行处理，只是 poll 没有最大文件描述符数量的限制。

#### epoll

epoll 在 Linux 2.6 内核正式提出，是基于事件驱动的 I/O 方式，相对于 select 来说，epoll 没有描述符个数限制，使用一个文件描述符管理多个描述符，将用户关心的文件描述符的事件存放到内核的一个事件表中，
这样在用户空间和内核空间的 copy 只需一次。

Linux 提供的 epoll 相关函数如下：

```C
int epoll_create(int size);
int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event);
int epoll_wait(int epfd, struct epoll_event * events, int maxevents, int timeout);
```

- epoll_create 函数创建一个epoll 的实例，返回一个表示当前 epoll 实例的文件描述符，后续相关操作都需要传入这个文件描述符。
- epoll_ctl 函数讲将一个 fd 添加到一个 eventpoll 中，或从中删除，或如果此 fd 已经在 eventpoll 中，可以更改其监控事件。
- epoll_wait 函数等待 epoll 事件从 epoll 实例中发生（rdlist 中存在或 timeout），并返回事件以及对应文件描述符。

当调用 epoll_create 函数时，内核会创建一个 eventpoll 结构体用于存储使用 epoll_ctl 函数向 epoll 对象中添加进来的事件。这些事件都存储在红黑树中，通过红黑树高效地识别重复添加地事件。所有添加到 epoll 中的事件都会与设备(网卡)驱动程序建立回调关系，当相应的事件发生时会调用这个回调方法，它会将发生的事件添加到 rdlist 链表中。调用 epoll_wait 函数检查是否有事件发生时，只需要检查 eventpoll 对象中的 rdlist 链表中是否存在 epitem 元素(每一个事件都对应一个 epitem 结构体)。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/epoll数据结构示意图.png" width="600px">
</div>

对于 fd 集合的拷贝问题，epoll 通过内核与用户空间 mmap(内存映射)将用户空间的一块地址和内核空间的一块地址同时映射到相同的一块物理内存地址，使得这块物理内存对内核和对用户均可见，减少用户态和内核态之间的数据交换。

综上，epoll 没有描述符个数限制，所以 epoll 不存在 select  中文件描述符数量限制问题；mmap 解决了 select 中用户态和内核态频繁切换的问题；通过 rdlist 解决了 select  中遍历所有事件的问题。
