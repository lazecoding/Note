# 事件模块

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