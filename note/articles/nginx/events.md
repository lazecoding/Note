# 事件模块

- 目录
    - [事件的定义](#事件的定义)
    - [ngx_events_module](#ngx_events_module)
    - [事件驱动模型](#事件驱动模型])
      - [select](#select)
      - [poll](#poll)
      - [epoll](#epoll)
    - [ngx_epoll_module](#ngx_epoll_module)
      - [ngx_epoll_init](#ngx_epoll_init)
      - [ngx_epoll_add_event](#ngx_epoll_add_event)
      - [ngx_epoll_process_events](#ngx_epoll_process_events)
    - [定时器事件](#定时器事件)
      - [缓存时间管理](#缓存时间管理)
      - [定时器的实现](#定时器的实现)
    - [建立连接](#建立连接)
      - [post 事件队列](#post-事件队列)
      - [如何建立新连接](#如何建立新连接)
      - [惊群问题](#惊群问题)
      - [负载均衡](#负载均衡)
    - [事件驱动框架执行流程](#事件驱动框架执行流程)

Nginx 是一个事件驱动架构的 Web 服务器，事件驱动架构所要解决的问题是如何收集、管理、分发事件。这里所说的事件，主要以网络事件和定时器事件为主，而网络事件中又以 TCP 网络事件为主（Nginx 毕竟是个Web服务器），本章将围绕这两种事件为中心论述。

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

作为 Web 服务器，每一个用户请求至少对应着一个 TCP 连接，为了及时处理这个连接，至少需要一个读事件和一个写事件，使得 epoll 可以有效地根据触发的事件调度相应模块读取请求或者发送响应。因此，Nginx 中定义了基本的数据结构 ngx_connection_t 来表示连接，这个连接表示是客户端主动发起的、Nginx 服务器被动接受的 TCP 连接，我们可以简单称其为被动连接。

### ngx_events_module

ngx_events_module 模块是一个核心模块，它定义了一类新模块：事件模块。它的功能如下：定义新的事件类型，并定义每个事件模块都需要实现的 ngx_event_module_t 接口，还需要管理这些事件模块生成的配置项结构体，并解析事件类配置项，

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

ngx_event_core_module 模块是一个事件类型的模块，它在所有事件模块中的顺序是第一位（configure 执行时必须把它放在其他事件模块之前）。这就保证了它会先于其他事件模块执行，由此它选择事件驱动机制的任务才可以完成。

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

事件轮询 API 就是用来解决这个问题的，这是一种多路复用机制。常见的事件轮询 API 有 select、poll、epoll，它们是操作系统提供给用户线程的 API，用于取代用户线程轮询。如果是用户线程轮询就要涉及用户态和内核态的频繁切换，这部分开销是巨大的。

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

epoll 在 Linux 2.6 内核正式提出，是基于事件驱动的 I/O 方式，相对于 select 来说，epoll 没有描述符个数限制，使用一个文件描述符管理多个描述符，将用户关心的文件描述符的事件存放到内核的一个事件表中，这样在用户空间和内核空间的 copy 只需一次。

Linux 提供的 epoll 相关函数如下：

```C
int epoll_create(int size);
int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event);
int epoll_wait(int epfd, struct epoll_event * events, int maxevents, int timeout);
```

- epoll_create 函数创建一个epoll 的实例，返回一个表示当前 epoll 实例的文件描述符，后续相关操作都需要传入这个文件描述符。
- epoll_ctl 函数讲将一个 fd 添加到一个 eventpoll 中，或从中删除，或如果此 fd 已经在 eventpoll 中，可以更改其监控事件。
- epoll_wait 函数等待 epoll 事件从 epoll 实例中发生（rdllist 中存在或 timeout），并返回事件以及对应文件描述符。

当调用 epoll_create 函数时，内核会创建一个 eventpoll 结构体用于存储使用 epoll_ctl 函数向 epoll 对象中添加进来的事件。这些事件都存储在红黑树中，通过红黑树可以保持稳定高效的查询效率，而且可以高效地识别重复添加地事件。所有添加到 epoll 中的事件都会与设备(网卡)驱动程序建立回调关系，当相应的事件发生时会调用这个回调方法，它会将发生的事件添加到 rdllist 链表中。调用 epoll_wait 函数检查是否有事件发生时，只需要检查 eventpoll 对象中的 rdllist 链表中是否存在 epitem 元素(每一个事件都对应一个 epitem 结构体)，如果 rdllist 不为空，只需要把这些事件从内核态拷贝到用户态，同时返回事件数量即可。因此，epoll 是十分高效地，可以轻松支持起百万级并发。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/epoll数据结构示意图.png" width="600px">
</div>

对于 fd 集合的拷贝问题，epoll 通过内核与用户空间 mmap(内存映射)将用户空间的一块地址和内核空间的一块地址同时映射到相同的一块物理内存地址，使得这块物理内存对内核和对用户均可见，减少用户态和内核态之间的数据交换。

综上，epoll 没有描述符个数限制，所以 epoll 不存在 select  中文件描述符数量限制问题；mmap 解决了 select 中用户态和内核态频繁切换的问题；通过 rdllist 解决了 select  中遍历所有事件的问题。

### ngx_epoll_module

ngx_epoll_module 是 Nginx 众多事件模块的其中一个，它是 epoll 机制在 Nginx 中的实现。

首先，ngx_epoll_commands 指明了对哪些配置项感兴趣：

```C
static ngx_command_t  ngx_epoll_commands[] = {
    // 这个配置项表示调用一次 epoll_wait 最多可以返回的事件数
    { ngx_string("epoll_events"),
      NGX_EVENT_CONF|NGX_CONF_TAKE1,
      ngx_conf_set_num_slot,
      0,
      offsetof(ngx_epoll_conf_t, events),
      NULL },
    // 指明在开启异步 I/O 且使用 io_setup 系统调用初始化异步 I/O 上下文环境时，初始分配的异步 I/O 事件个数
    { ngx_string("worker_aio_requests"),
      NGX_EVENT_CONF|NGX_CONF_TAKE1,
      ngx_conf_set_num_slot,
      0,
      offsetof(ngx_epoll_conf_t, aio_requests),
      NULL },

      ngx_null_command
};
```

ngx_epoll_module 主要是实现了 ngx_event.h 中规定的事件模块的接口，即实现了 ngx_event_module_t 中定义的回调函数，如下：

```C
static ngx_event_module_t  ngx_epoll_module_ctx = {
    &epoll_name,
    ngx_epoll_create_conf,               /* create configuration */
    ngx_epoll_init_conf,                 /* init configuration */

    {
        ngx_epoll_add_event,             /* add an event */
        ngx_epoll_del_event,             /* delete an event */
        ngx_epoll_add_event,             /* enable an event */
        ngx_epoll_del_event,             /* disable an event */
        ngx_epoll_add_connection,        /* add an connection */
        ngx_epoll_del_connection,        /* delete an connection */
#if (NGX_HAVE_EVENTFD)
        ngx_epoll_notify,                /* trigger a notify */
#else
        NULL,                            /* trigger a notify */
#endif
        ngx_epoll_process_events,        /* process the events */
        ngx_epoll_init,                  /* init the events */
        ngx_epoll_done,                  /* done the events */
    }
};
```

类型 ngx_event_module_t(src/event/ngx_event.h):

```C
typedef struct {
    ngx_str_t               *name;//事件模块名称
 
    void                    *(*create_conf)(ngx_cycle_t *cycle);//解析配置项前，这个回调方法用户创建存储配置项参数的结构体
    char                    *(*init_conf)(ngx_cycle_t *cycle, void *conf);//解析配置项完成后，这个方法被调用用以综合处理当前事件模块感兴趣的全部配置项
 
    ngx_event_actions_t     actions;
} ngx_event_module_t;
```

ngx_event_module_t 中的 actions 对应着事件驱动机制下，每个事件模块需要实现的 10 个抽象方法：

```C
typedef struct {
    //添加事件
    ngx_int_t  (*add)(ngx_event_t *ev, ngx_int_t event, ngx_uint_t flags);
    //删除事件
    ngx_int_t  (*del)(ngx_event_t *ev, ngx_int_t event, ngx_uint_t flags);
 
    // 启用事件和禁用事件，目前事件框架不会调用这两个方法
    ngx_int_t  (*enable)(ngx_event_t *ev, ngx_int_t event, ngx_uint_t flags);
    ngx_int_t  (*disable)(ngx_event_t *ev, ngx_int_t event, ngx_uint_t flags);
 
    // 添加连接，意味着这个连接上的读写事件都添加到事件驱动机制中了
    ngx_int_t  (*add_conn)(ngx_connection_t *c);
    ngx_int_t  (*del_conn)(ngx_connection_t *c, ngx_uint_t flags);
 
    // 仅在多线程环境下调用，暂时 Nginx 不支持
    ngx_int_t  (*process_changes)(ngx_cycle_t *cycle, ngx_uint_t nowait);
    // 在正常的工作循环中，将通过调用这个方法来处理时间事件。这个方法仅在方法 ngx_process_events_and_timers 中调用，它是处理、分发事件的核心
    ngx_int_t  (*process_events)(ngx_cycle_t *cycle, ngx_msec_t timer,
                   ngx_uint_t flags);
    
    // 初始化事件驱动模块
    ngx_int_t  (*init)(ngx_cycle_t *cycle, ngx_msec_t timer);
    // 退出事件驱动模块前调用的方法
    void       (*done)(ngx_cycle_t *cycle);
} ngx_event_actions_t;
```

#### ngx_epoll_init

ngx_epoll_init 方法它做了 2 件事： 

- 调用 epoll_create 方法创建 epoll 对象。
- 创建 event_list 数组，用于进行 epoll_wait 调用时传递内核对象。

ngx_epoll_init 源码：

```C
static ngx_int_t
ngx_epoll_init(ngx_cycle_t *cycle, ngx_msec_t timer)
{   
    // 存储配置项的结构体
    ngx_epoll_conf_t  *epcf;

    epcf = ngx_event_get_conf(cycle->conf_ctx, ngx_epoll_module);

    if (ep == -1) {
        // 创建 epoll 对象
        ep = epoll_create(cycle->connection_n / 2);

        if (ep == -1) {
            ngx_log_error(NGX_LOG_EMERG, cycle->log, ngx_errno,
                          "epoll_create() failed");
            return NGX_ERROR;
        }

#if (NGX_HAVE_EVENTFD)
        if (ngx_epoll_notify_init(cycle->log) != NGX_OK) {
            ngx_epoll_module_ctx.actions.notify = NULL;
        }
#endif

#if (NGX_HAVE_FILE_AIO)
        // 异步 I/O
        ngx_epoll_aio_init(cycle, epcf);
#endif

#if (NGX_HAVE_EPOLLRDHUP)
        ngx_epoll_test_rdhup(cycle);
#endif
    }
    // 解析配置项所得的 epoll_wait 一次可最多返回的时间个数较大
    if (nevents < epcf->events) {
        if (event_list) {
            // 销毁原来的存储返回事件的链表
            ngx_free(event_list);
        }
        // 重新分配链表空间
        event_list = ngx_alloc(sizeof(struct epoll_event) * epcf->events,
                               cycle->log);
        if (event_list == NULL) {
            return NGX_ERROR;
        }
    }

    nevents = epcf->events;

    ngx_io = ngx_os_io;

    // Nginx 事件框架处理事件时封装的接口
    ngx_event_actions = ngx_epoll_module_ctx.actions;

#if (NGX_HAVE_CLEAR_EVENT)
    ngx_event_flags = NGX_USE_CLEAR_EVENT
#else
    ngx_event_flags = NGX_USE_LEVEL_EVENT
#endif
                      |NGX_USE_GREEDY_EVENT
                      |NGX_USE_EPOLL_EVENT;

    return NGX_OK;
}
```

#### ngx_epoll_add_event

ngx_epoll_add_event 方法调用 epoll_ctl 向 epoll 中添加事件。

ngx_epoll_add_event 源码：

```C
static ngx_int_t
static ngx_int_t
ngx_epoll_add_event(ngx_event_t *ev, ngx_int_t event, ngx_uint_t flags)
{
    int                  op;
    uint32_t             events, prev;
    ngx_event_t         *e;
    ngx_connection_t    *c;
    struct epoll_event   ee;

    // 每个事件的 data 成员存放着其对应的 ngx_connection_t 连接
    c = ev->data;

    //下面会根据 event 参数确定当前事件是读事件还是写事件，这会决定 events 是加上 EPOLLIN 标志还是 EPOLLOUT 标志位
    events = (uint32_t) event;

    if (event == NGX_READ_EVENT) {
        e = c->write;
        prev = EPOLLOUT;
#if (NGX_READ_EVENT != EPOLLIN|EPOLLRDHUP)
        events = EPOLLIN|EPOLLRDHUP;
#endif

    } else {
        e = c->read;
        prev = EPOLLIN|EPOLLRDHUP;
#if (NGX_WRITE_EVENT != EPOLLOUT)
        events = EPOLLOUT;
#endif
    }
    // 依据 active 标志位确定是否为活跃事件
    if (e->active) {
        // 是活跃事件，修改事件
        op = EPOLL_CTL_MOD;
        events |= prev;

    } else {
        // 不是活跃事件，添加事件
        op = EPOLL_CTL_ADD;
    }

#if (NGX_HAVE_EPOLLEXCLUSIVE && NGX_HAVE_EPOLLRDHUP)
    if (flags & NGX_EXCLUSIVE_EVENT) {
        events &= ~EPOLLRDHUP;
    }
#endif
    // 设定 events 标志位
    ee.events = events | (uint32_t) flags;
    // ptr 存储的是 ngx_connection_t 连接，instance 是过期事件标志位
    ee.data.ptr = (void *) ((uintptr_t) c | ev->instance);

    ngx_log_debug3(NGX_LOG_DEBUG_EVENT, ev->log, 0,
                   "epoll add event: fd:%d op:%d ev:%08XD",
                   c->fd, op, ee.events);
    // 调用 epoll_ctl 向 epoll 中添加或修改事件
    if (epoll_ctl(ep, op, c->fd, &ee) == -1) {
        ngx_log_error(NGX_LOG_ALERT, ev->log, ngx_errno,
                      "epoll_ctl(%d, %d) failed", op, c->fd);
        return NGX_ERROR;
    }
    // 设置 active = 1，标识事件活跃
    ev->active = 1;
#if 0
    ev->oneshot = (flags & NGX_ONESHOT_EVENT) ? 1 : 0;
#endif

    return NGX_OK;
}
```

#### ngx_epoll_process_events

ngx_epoll_process_events 调用 epoll_wait 收集当前发生的所有事件，对于不需要加入延后队列的事件会直接调用该事件的回调方法。

ngx_epoll_process_events 源码：

```C
static ngx_int_t
ngx_epoll_process_events(ngx_cycle_t *cycle, ngx_msec_t timer, ngx_uint_t flags)
{
    int                events;
    uint32_t           revents;
    ngx_int_t          instance, i;
    ngx_uint_t         level;
    ngx_err_t          err;
    ngx_event_t       *rev, *wev;
    ngx_queue_t       *queue;
    ngx_connection_t  *c;

    /* NGX_TIMER_INFINITE == INFTIM */

    ngx_log_debug1(NGX_LOG_DEBUG_EVENT, cycle->log, 0,
                   "epoll timer: %M", timer);

    // 调用 epoll_wait 获取事件
    events = epoll_wait(ep, event_list, (int) nevents, timer);

    err = (events == -1) ? ngx_errno : 0;

    // 更新 Nginx 缓存时间
    if (flags & NGX_UPDATE_TIME || ngx_event_timer_alarm) {
        ngx_time_update();
    }

    // 错误处理
    if (err) {
        if (err == NGX_EINTR) {

            if (ngx_event_timer_alarm) {
                ngx_event_timer_alarm = 0;
                return NGX_OK;
            }

            level = NGX_LOG_INFO;

        } else {
            level = NGX_LOG_ALERT;
        }

        ngx_log_error(level, cycle->log, err, "epoll_wait() failed");
        return NGX_ERROR;
    }

    // 没有获取到事件
    if (events == 0) {
        if (timer != NGX_TIMER_INFINITE) {
            return NGX_OK;
        }

        ngx_log_error(NGX_LOG_ALERT, cycle->log, 0,
                      "epoll_wait() returned no events without timeout");
        return NGX_ERROR;
    }

    // 遍历本次获取的事件
    for (i = 0; i < events; i++) {
        // 获取连接 ngx_connection_t 的地址
        c = event_list[i].data.ptr;

        // 取出连接的地址最后一位，用于存储instance变量
        instance = (uintptr_t) c & 1;
        c = (ngx_connection_t *) ((uintptr_t) c & (uintptr_t) ~1);
        // 取出读事件
        rev = c->read;
        // 判断读事件是否过期
        if (c->fd == -1 || rev->instance != instance) {

            /*
             * the stale event from a file descriptor
             * that was just closed in this iteration
             */

            ngx_log_debug1(NGX_LOG_DEBUG_EVENT, cycle->log, 0,
                           "epoll: stale event %p", c);
            continue;
        }
        // 取出事件类型
        revents = event_list[i].events;

        ngx_log_debug3(NGX_LOG_DEBUG_EVENT, cycle->log, 0,
                       "epoll: fd:%d ev:%04XD d:%p",
                       c->fd, revents, event_list[i].data.ptr);
        
        if (revents & (EPOLLERR|EPOLLHUP)) {
            ngx_log_debug2(NGX_LOG_DEBUG_EVENT, cycle->log, 0,
                           "epoll_wait() error on fd:%d ev:%04XD",
                           c->fd, revents);

            /*
             * if the error events were returned, add EPOLLIN and EPOLLOUT
             * to handle the events at least in one active handler
             */

            revents |= EPOLLIN|EPOLLOUT;
        }

#if 0
        if (revents & ~(EPOLLIN|EPOLLOUT|EPOLLERR|EPOLLHUP)) {
            ngx_log_error(NGX_LOG_ALERT, cycle->log, 0,
                          "strange epoll_wait() events fd:%d ev:%04XD",
                          c->fd, revents);
        }
#endif
        // 如果是读事件且事件活跃
        if ((revents & EPOLLIN) && rev->active) {

#if (NGX_HAVE_EPOLLRDHUP)
            if (revents & EPOLLRDHUP) {
                rev->pending_eof = 1;
            }
#endif

            rev->ready = 1;
            rev->available = -1;
            // 是否含有 NGX_POST_EVENTS 标识，表示延后处理
            if (flags & NGX_POST_EVENTS) {
                // 根据事件是建立连接事件(ngx_posted_accept_events)还是普通事件(ngx_posted_events)选择对应队列
                queue = rev->accept ? &ngx_posted_accept_events
                                    : &ngx_posted_events;
                // 将事件放入延后队列中
                ngx_post_event(rev, queue);

            } else {
                // 立即调用读事件的回调方法处理事件
                rev->handler(rev);
            }
        }
        // 取出写事件
        wev = c->write;

        if ((revents & EPOLLOUT) && wev->active) {
            // 判断是否过期
            if (c->fd == -1 || wev->instance != instance) {

                /*
                 * the stale event from a file descriptor
                 * that was just closed in this iteration
                 */

                ngx_log_debug1(NGX_LOG_DEBUG_EVENT, cycle->log, 0,
                               "epoll: stale event %p", c);
                continue;
            }

            wev->ready = 1;
#if (NGX_THREADS)
            wev->complete = 1;
#endif

            if (flags & NGX_POST_EVENTS) {
                // 将事件放入延后队列中
                ngx_post_event(wev, &ngx_posted_events);

            } else {
                 // 立即调用读事件的回调方法处理事件
                wev->handler(wev);
            }
        }
    }

    return NGX_OK;
}
```

这里出现了过期事件，其实很好理解。假设某次 epoll_wait 获取了 3 个事件,但是在处理第一个事件的时候由于业务需要关闭了一个连接，
而这个连接恰好对应这第三个事件，当处理第三个事件的时候这个事件已经被处理过了，也就是过期事件。

### 定时器事件

和 epoll 等事件驱动机制不同，Nginx 自己实现了定时器事件触发机制。

#### 缓存时间管理

在实现定时器之前，Nginx 首先设计了一套时间管理方案——缓存时间。Nginx 每个进程独立管理当前时间，它将时间缓存在内存中，这样获取时间不需要每次都调用 gettimeofday，只需要获取内存的几个整型变量即可。

gettimeofday 是 C 库提供的函数（不是系统调用），它封装了内核里的 sys_gettimeofday 系统调用，就是说，归根到底是系统调用。当我们调用 gettimeofday 时，将会向内核发送软中断，然后将陷入内核态，这时内核至少要做下列事：处理软中断、保存所有寄存器值、从用户态复制函数参数到内核态、执行、将结果复制到用户态。

Nginx 每个进程都会独自管理当前时间，ngx_time_t 结构体是缓存时间变量的类型：

```C
typedef struct {
    // 格林威治时间 1970 年 1 月 1 日凌晨 0 点 0 分 0 秒到当前时间的秒数
    time_t      sec;
    // sec 成员只能精确到秒，msec 则是当前时间相对于 sec 的毫秒偏移量 
    ngx_uint_t  msec;
    // 时区
    ngx_int_t   gmtoff;
}ngx_time_t;
```

#### 定时器的实现

定时器是通过红黑树实现的。ngx_event_timer_rbtree 就是所有定时器事件组成的红黑树，而 ngx_event_timer_sentinel 就是这棵红黑树的哨兵节点。


```C
// 全局变量
// 管理定时事件的红黑树
ngx_rbtree_t              ngx_event_timer_rbtree;
// 红黑树的节点
static ngx_rbtree_node_t  ngx_event_timer_sentinel;
```

这棵红黑树中的每个节点都是 ngx_event_t 事件中的 timer 成员，而 ngx_rbtree_node_t 节点的关键字就是事件的超时时间，以这个超时时间的大小组成了红黑树 ngx_event_timer_rbtree。这样，如果需要找出最有可能超时的事件，那么将 ngx_event_timer_rbtree 树中最左边的节点取出来即可。只要用当前时间去比较这个最左边节点的超时时间，就会知道这个事件有没有触发超时，如果还没有触发超时，那么会知道最少还要经过多少毫秒满足超时条件而触发超时。

定时器核心方法：

- ngx_event_timer_init

log 是记录日志的 ngx_log_t 对象，ngx_event_timer_init 的作用是初始化定时器。

```C
ngx_int_t
ngx_event_timer_init(ngx_log_t *log)
{
    ngx_rbtree_init(&ngx_event_timer_rbtree, &ngx_event_timer_sentinel,
                    ngx_rbtree_insert_timer_value);

    return NGX_OK;
}
```

- ngx_event_find_timer

找出红黑树最左面节点，如果改节点的超时事件大于当前事件表明当前没有事件触发定时器，此时返回需要经过多少秒会有定时事件触发；如果改节点的超时事件小于或等于当前事件，则返回 0，表示定时器中已经存在需要触发的定时事件。

```C
ngx_msec_t
ngx_event_find_timer(void)
{
    ngx_msec_int_t      timer;
    ngx_rbtree_node_t  *node, *root, *sentinel;

    if (ngx_event_timer_rbtree.root == &ngx_event_timer_sentinel) {
        return NGX_TIMER_INFINITE;
    }

    root = ngx_event_timer_rbtree.root;
    sentinel = ngx_event_timer_rbtree.sentinel;

    node = ngx_rbtree_min(root, sentinel);

    timer = (ngx_msec_int_t) (node->key - ngx_current_msec);

    return (ngx_msec_t) (timer > 0 ? timer : 0);
}
```

- ngx_event_expire_timers

检查定时器中的所有事件，按红黑树节点大小顺序依次调用已经满足超时事件需要触发事件的 handler 回调方法。

```C
void
ngx_event_expire_timers(void)
{
    ngx_event_t        *ev;
    ngx_rbtree_node_t  *node, *root, *sentinel;

    sentinel = ngx_event_timer_rbtree.sentinel;

    for ( ;; ) {
        root = ngx_event_timer_rbtree.root;

        if (root == sentinel) {
            return;
        }

        node = ngx_rbtree_min(root, sentinel);

        /* node->key > ngx_current_msec */

        if ((ngx_msec_int_t) (node->key - ngx_current_msec) > 0) {
            return;
        }

        ev = (ngx_event_t *) ((char *) node - offsetof(ngx_event_t, timer));

        ngx_log_debug2(NGX_LOG_DEBUG_EVENT, ev->log, 0,
                       "event timer del: %d: %M",
                       ngx_event_ident(ev->data), ev->timer.key);

        ngx_rbtree_delete(&ngx_event_timer_rbtree, &ev->timer);

#if (NGX_DEBUG)
        ev->timer.left = NULL;
        ev->timer.right = NULL;
        ev->timer.parent = NULL;
#endif

        ev->timer_set = 0;

        ev->timedout = 1;

        ev->handler(ev);
    }
}
```

- ngx_event_del_timer

ev 是待操作事件，ngx_event_del_timer 的作用是移除一个事件。

```C
static ngx_inline void
ngx_event_del_timer(ngx_event_t *ev)
{
    ngx_log_debug2(NGX_LOG_DEBUG_EVENT, ev->log, 0,
                   "event timer del: %d: %M",
                    ngx_event_ident(ev->data), ev->timer.key);

    ngx_rbtree_delete(&ngx_event_timer_rbtree, &ev->timer);

#if (NGX_DEBUG)
    ev->timer.left = NULL;
    ev->timer.right = NULL;
    ev->timer.parent = NULL;
#endif

    ev->timer_set = 0;
}
```

- ngx_event_add_timer

ev 是待操作事件，timer 是 timer 毫秒后超时，ngx_event_add_timer 是作用是添加一个定时器事件，超时时间为 timer 毫秒。

```C
static ngx_inline void
ngx_event_add_timer(ngx_event_t *ev, ngx_msec_t timer)
{
    ngx_msec_t      key;
    ngx_msec_int_t  diff;

    key = ngx_current_msec + timer;

    if (ev->timer_set) {

        /*
         * Use a previous timer value if difference between it and a new
         * value is less than NGX_TIMER_LAZY_DELAY milliseconds: this allows
         * to minimize the rbtree operations for fast connections.
         */

        diff = (ngx_msec_int_t) (key - ev->timer.key);

        if (ngx_abs(diff) < NGX_TIMER_LAZY_DELAY) {
            ngx_log_debug3(NGX_LOG_DEBUG_EVENT, ev->log, 0,
                           "event timer: %d, old: %M, new: %M",
                            ngx_event_ident(ev->data), ev->timer.key, key);
            return;
        }

        ngx_del_timer(ev);
    }

    ev->timer.key = key;

    ngx_log_debug3(NGX_LOG_DEBUG_EVENT, ev->log, 0,
                   "event timer add: %d: %M:%M",
                    ngx_event_ident(ev->data), timer, ev->timer.key);

    ngx_rbtree_insert(&ngx_event_timer_rbtree, &ev->timer);

    ev->timer_set = 1;
}
```

### 建立连接

监听连接的读事件的回调方法被设为 ngx_event_accept 方法，监听到请求连接的会将读事件会添加到 ngx_epoll_module 事件驱动模块中。当执行 ngx_epoll_process_events 方法时，如果存在建立连接事件，则会调用 ngx_event_accept 方法来建立新连接。

但是，建立连接并没有这么简单。Nginx 是多进程架构，多个 worker 子进程监听相同的端口，这意味着多个子进程建立新连接存在竞争，又称 "惊群" 问题。 另一方面，建立连接还会涉及到负载均衡的问题，多个子进程竞争最终只有一个子进程会成功建立连接，应该让服务压力尽可能均匀分散到每个子进程中。

#### post 事件队列

实际上，上述问题的解决离不开 Nginx 的 post 事件队列。post 事件表示允许事件延后处理。Nginx 涉及了两个 post 队列，一个是处理新连接的 ngx_posted_accept_events 队列，另一个是处理普通读写事件的 ngx_posted_events 队列。

epoll_wait 收集的一批事件分到 ngx_posted_accept_events 队列和 ngx_posted_events 队列中，ngx_posted_accept_events 队列优先执行，这是解决 "惊群" 问题和负载均衡的关键。

#### 如何建立新连接

ngx_event_accept 是处理建立连接事件的回调函数：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/nginx/ngx_event_accept建立新连接流程.png" width="400px">
</div>

- 首先调用 accept 方法试图建立新连接，如果没有需要处理的新连接事件，直接返回。
- 初始化负载均衡的阈值 ngx_accept_disabled，这个阈值是进程允许的总连接数的 1/8 减去空闲连接数。
- 调用 ngx_get_connection 方法从连接池中获取一个 ngx_connection_t 连接对象。
- 为 ngx_connection_t 中的 pool 指针建立内存池。
- 设置套接字属性，如设为非阻塞套接字。
- 将这个新连接对应的读事件添加到 epoll 等事件驱动模块中，等待用户请求 epoll_wait 收集该事件。
- 调用监听对象 ngx_listening_t 中的 hander 回调方法。

#### 惊群问题

Nginx 通过 accept_mutex 锁来解决 "惊群" 问题。开启 accept_mutex 情况下，只有通过 ngx_trylock_accept_mutex 成功获取 accept_mutex 锁的 worker 进程才会去试着监听端口。

ngx_trylock_accept_mutex 源码：

```C
ngx_int_t
ngx_trylock_accept_mutex(ngx_cycle_t *cycle)
{
    // 使用进程见的同步锁，试图获取 accept_mutex 锁。
    // ngx_shmtx_trylock 返回 1 表示获取锁成功，0 表示获取锁失败。
    if (ngx_shmtx_trylock(&ngx_accept_mutex)) {

        ngx_log_debug0(NGX_LOG_DEBUG_EVENT, cycle->log, 0,
                       "accept mutex locked");
                       
        // ngx_accept_mutex_held 是标志位，为 1 表示当前线程（之前）已经获取到锁，直接返回成功
        if (ngx_accept_mutex_held && ngx_accept_events == 0) {
            return NGX_OK;
        }
        
        // 将所有监听连接的读事件添加到当前的事件驱动模块（如 epoll）
        if (ngx_enable_accept_events(cycle) == NGX_ERROR) {
            ngx_shmtx_unlock(&ngx_accept_mutex);
            return NGX_ERROR;
        }

        ngx_accept_events = 0;
        ngx_accept_mutex_held = 1;

        return NGX_OK;
    }

    ngx_log_debug1(NGX_LOG_DEBUG_EVENT, cycle->log, 0,
                   "accept mutex lock failed: %ui", ngx_accept_mutex_held);
    // 行至此处表示获取锁失败，还原 ngx_accept_mutex_held 状态
    if (ngx_accept_mutex_held) {
        if (ngx_disable_accept_events(cycle, 0) == NGX_ERROR) {
            return NGX_ERROR;
        }

        ngx_accept_mutex_held = 0;
    }

    return NGX_OK;
}
```

post 事件队列将事件归类，有限处理 ngx_posted_accept_events 队列，即新连接事件。这样 worker 线程占有 accept_mutex 锁后可以快速处理完新连接事件并释放 accept_mutex 锁，这样就大大减少了 accept_mutex 锁的占用时间。

#### 负载均衡

和 "惊群问题" 一样，只有打开了 accept_mutex 锁才能实现 worker 子进程间的负载均衡。
 
上面提到，worker 进程在建立新连接时候会初始化全局变量 ngx_accept_disabled，这个是负载均衡的阈值。第一次初始化 ngx_accept_disabled 是个负值，是总连接数的 -7/8，随着连接的建立，free_connection_n（空闲连接数）会减小，（connection_n，总链接数不变），ngx_accept_disabled 值逐渐变大。当 ngx_accept_disabled > 0，意味着当前使用的连接数达到了总连接数的 7/8，该 worker 进程将不再处理新连接，每次 ngx_process_events_and_timers 调用 ngx_accept_disabled 的值都会 -1，直到 ngx_accept_disabled < 0，即当前使用的连接数小于总连接数的 7/8，该 worker 进程才会尝试调用 ngx_trylock_accept_mutex 处理新连接。

下面是 ngx_accept_disabled 初始化和使用源码：

```C
// src/event/ngx_event_accept.c#ngx_event_accept
ngx_accept_disabled = ngx_cycle->connection_n / 8
                              - ngx_cycle->free_connection_n;

// src/event/ngx_event.c#ngx_process_events_and_timers
if (ngx_accept_disabled > 0) {
    ngx_accept_disabled--;

} else {
    if (ngx_trylock_accept_mutex(cycle) == NGX_ERROR) {
        return;
    }

    if (ngx_accept_mutex_held) {
        flags |= NGX_POST_EVENTS;

    } else {
        if (timer == NGX_TIMER_INFINITE
            || timer > ngx_accept_mutex_delay)
        {
            timer = ngx_accept_mutex_delay;
        }
    }
}
```

### 事件驱动框架执行流程

每个 worker 进程都在 ngx_worker_process_cycle 方法中循环处理事件，具体处理分发事件实际上就是调用的 ngx_process_events_and_timers 方法。

循环调用 ngx_process_events_and_timers 方法就是在处理所有的事件，这正是事件驱动机制的核心。ngx_process_events_and_timers 方法既会处理普通的网络事件，也会处理定时器事件。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/nginx/ngx_process_events_and_timers执行流程.png" width="400px">
</div>

核心操作：

- `ngx_trylock_accept_mutex(cycle)`：尝试处理处理新连接事件。
- `(void) ngx_process_events(cycle, timer, flags)`：处理网络事件。
- `ngx_event_expire_timers()`：处理定时器事件。
- `ngx_event_process_posted(cycle, &ngx_posted_accept_events)`：处理 ngx_posted_accept_events 事件队列，即新连接事件。
- `ngx_event_process_posted(cycle, &ngx_posted_events)`：处理 ngx_posted_events 事件队列，即普通事件。

ngx_process_events_and_timers 源码：

```C
void
ngx_process_events_and_timers(ngx_cycle_t *cycle)
{
    ngx_uint_t  flags;
    ngx_msec_t  timer, delta;

    if (ngx_timer_resolution) {
        timer = NGX_TIMER_INFINITE;
        flags = 0;

    } else {
        timer = ngx_event_find_timer();
        flags = NGX_UPDATE_TIME;

#if (NGX_WIN32)

        /* handle signals from master in case of network inactivity */

        if (timer == NGX_TIMER_INFINITE || timer > 500) {
            timer = 500;
        }

#endif
    }
    
    if (ngx_use_accept_mutex) {
        // 负载均衡
        if (ngx_accept_disabled > 0) {
            ngx_accept_disabled--;

        } else {
            // 尝试 ngx_trylock_accept_mutex，处理新连接
            if (ngx_trylock_accept_mutex(cycle) == NGX_ERROR) {
                return;
            }

            if (ngx_accept_mutex_held) {
                flags |= NGX_POST_EVENTS;

            } else {
                if (timer == NGX_TIMER_INFINITE
                    || timer > ngx_accept_mutex_delay)
                {
                    timer = ngx_accept_mutex_delay;
                }
            }
        }
    }

    if (!ngx_queue_empty(&ngx_posted_next_events)) {
        ngx_event_move_posted_next(cycle);
        timer = 0;
    }
    
    delta = ngx_current_msec;

    // 处理网络事件
    (void) ngx_process_events(cycle, timer, flags);

    // ngx_process_events 执行消耗的时间
    delta = ngx_current_msec - delta;

    ngx_log_debug1(NGX_LOG_DEBUG_EVENT, cycle->log, 0,
                   "timer delta: %M", delta);

    // 处理 ngx_posted_accept_events 事件队列，即新连接事件
    ngx_event_process_posted(cycle, &ngx_posted_accept_events);

    if (ngx_accept_mutex_held) {
        ngx_shmtx_unlock(&ngx_accept_mutex);
    }

    // 处理定时器事件
    ngx_event_expire_timers();

    // 处理 ngx_posted_events 事件队列，即普通事件
    ngx_event_process_posted(cycle, &ngx_posted_events);
}
```

