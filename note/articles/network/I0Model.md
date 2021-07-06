# 网络 IO 模型

- 目录
    - [Socket](#Socket)
        - [Socket 通信过程](#Socket-通信过程)
    - [IO 模型](#IO-模型)
        - [阻塞式 IO](#阻塞式-IO)
        - [非阻塞式 IO](#非阻塞式-IO)
        - [IO 复用](#IO-复用)
        - [信号驱动 IO](#信号驱动-IO)
        - [异步 IO](#异步-IO)
        - [比较](#比较)
    - [多路复用](#多路复用)
        - [select](#select)
        - [poll](#poll)
        - [epoll](#epoll)
    
IO（input output）主要是文件 IO 和网络 IO 两类，本文的重点是网络 IO。

在行文前需要有一个共识：CPU 处理数据的速度远远大于 IO 准备数据的速度，而网络 IO 速度与磁盘读写速度相当，远小于固态和内存读写速度。

### Socket

Socket 又称套接字。

传输层协议将由网络层提供的主机到主机的服务延申到主机上进程到进程的交付服务。一个进程可以拥有一个或者多个套接字（Socket），它相当于从网络向进程传递数据和向网络传递数据的门户。
进程与进程通信，其实并不是直接将数据交付给对方进程，而且通过一个中间的套接字进行交付数据的。每个套接字都有唯一的标识，标识符的格式取决于它是 UDP 还是 TCP 套接字。

Socket 并不是一种协议，而是为方便直接使用更底层协议而存在的一个抽象层。Socket 和 TCP/IP 协议也没有必然联系，只是用 TCP/IP 协议栈更方便而已。所以 Socket 是对 TCP/IP 协议的封装，
它是一组接口，把复杂的 TCP/IP 协议族隐藏在 Socket 接口后面。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/SOCKET-TCP通信流程.png" width="600px">
</div>

以 UDP 和 TCP 为例，这两种协议的套接字也是有区别的：

- 一个 UDP 套接字是由一个二元组标识的，该二元组包含一个目的 IP 地址和一个目的端口号。因此，如果两个 UDP 报文段有不同的源 IP 地址或源端口号，但具有相同的目的 IP 地址和目的端口号，
  那么这两个报文段将通过相同的套接字被定向到相同的进程。
- 一个 TCP 套接字是由一个四元组标识的，该四元组包含源 IP 地址、源端口号、目的 IP 地址、目的端口号。这样，当一个 TCP 报文段从网络到达一个主机时，该主机使用四元组来将报文定向到对应的套接字。

#### Socket 通信过程

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/Socket通信模型.png" width="600px">
</div>

服务端调用 socket()、bind()、listen() 初始化 Socket，与本地地址和端口进行绑定，调用 accept() 阻塞等待客户端连接。调用 accept() 时，Socket 会进入 "waiting" 状态。客户请求连接时，方法建立连接并返回服务器。accept() 返回一个含有两个元素的元组 (conn, addr)。第一个元素 conn 是新的 Socket 对象，服务器必须通过它与客户通信；第二个元素 addr 是客户的IP地址及端口。

客户端调用 socket() 完成初始化，调用 connect() 与远程主机连接(TCP 协议)。建立 TCP 连接需要进行 "三次握手"。
- 客户端调用 connect()  发出 SYN 并阻塞等待服务器应答；
- 服务器完成了第一次握手，发送 SYN 和 ACK 应答；
- 客户端收到服务端发送的应答之后，从 connect() 返回，再发送一个 ACK 给服务器；
- 服务端 Socket 对象接收客户端第三次握手 ACK 确认，此时服务端从 accept() 返回，建立连接。

建立连接后，客户端和服务端通过 send() 和 receive() 通信（传输数据）。服务端从 accept() 返回后立刻调用 receive() 读取 Socket，读 Socket 就像读管道一样，如果没有数据到达就阻塞等待。当客户端调用 send() 发送请求给服务端，服务端通过 receie() 接收后对客户端的请求数据进行处理，在此期间客户端将调用 receive() 阻塞等待服务端的应答。当服务端处理完毕会调用 send() 将处理结果发回给客户端，接着再次调用 receive()  阻塞等待下一条请求。客户端通过 receive() 收到来自服务端的响应数据后，发送下一条请求，如此循环下去。

当客户端不再请求客户端，会进行 "四次挥手"，断开连接。
- 某个应用进程调用 close() 主动关闭，发送一个 FIN；
- 另一端接收到 FIN 后被动执行关闭，并发送 ACK 确认；
- 之后被动执行关闭的应用进程调用 close() 关闭 Socket，并也发送一个 FIN；
- 接收到这个 FIN 的一端向另一端 ACK 确认。

### IO 模型

Unix 有五种 I/O 模型：

- 阻塞式 I/O
- 非阻塞式 I/O
- I/O 复用（select 和 poll）
- 信号驱动式 I/O（SIGIO）
- 异步 I/O（AIO）

####  阻塞式 IO

应用进程被阻塞，直到数据从内核缓冲区复制到应用进程缓冲区中才返回。

应该注意到，在阻塞的过程中，其它应用进程还可以执行，因此阻塞不意味着整个操作系统都被阻塞。因为其它应用进程还可以执行，所以不消耗 CPU 时间，这种模型的 CPU 利用率会比较高。

下图中，recvfrom() 用于接收 Socket 传来的数据，并复制到应用进程的缓冲区 buf 中。这里把 recvfrom() 当成系统调用。

```C
ssize_t recvfrom(int sockfd, void *buf, size_t len, int flags, struct sockaddr *src_addr, socklen_t *addrlen);
```

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/阻塞式IO模型.png" width="600px">
</div>

####  非阻塞式 IO

应用进程执行系统调用之后，内核返回一个错误码。应用进程可以继续执行，但是需要不断的执行系统调用来获知 I/O 是否完成，这种方式称为轮询（polling）。

由于 CPU 要处理更多的系统调用，因此这种模型的 CPU 利用率比较低。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/非阻塞式IO模型.png" width="600px">
</div>

####  IO 复用

使用 select 或者 poll 等待数据，并且可以等待多个套接字中的任何一个变为可读。这一过程会被阻塞，当某一个套接字可读时返回，之后再使用 recvfrom 把数据从内核复制到进程中。

它可以让单个进程具有处理多个 I/O 事件的能力。又被称为 Event Driven I/O，即事件驱动 I/O。

如果一个 Web 服务器没有 I/O 复用，那么每一个 Socket 连接都需要创建一个线程去处理。如果同时有几万个连接，那么就需要创建相同数量的线程。相比于多进程和多线程技术，I/O 复用不需要进程线程创建和切换的开销，系统开销更小。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/IO复用模型.png" width="600px">
</div>

####  信号驱动 IO

应用进程使用 sigaction 系统调用，内核立即返回，应用进程可以继续执行，也就是说等待数据阶段应用进程是非阻塞的。内核在数据到达时向应用进程发送 SIGIO 信号，应用进程收到之后在信号处理程序中调用 recvfrom 将数据从内核复制到应用进程中。

相比于非阻塞式 I/O 的轮询方式，信号驱动 I/O 的 CPU 利用率更高。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/信号驱动IO模型.png" width="600px">
</div>

####  异步 IO

应用进程执行 aio_read 系统调用会立即返回，应用进程可以继续执行，不会被阻塞，内核会在所有操作完成之后向应用进程发送信号。

异步 I/O 与信号驱动 I/O 的区别在于，异步 I/O 的信号是通知应用进程 I/O 完成，而信号驱动 I/O 的信号是通知应用进程可以开始 I/O。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/异步IO模型.png" width="600px">
</div>

#### 比较

上述五类 I/O 可以分为两类：

同步 I/O：将数据从内核缓冲区复制到应用进程缓冲区的阶段（第二阶段），应用进程会阻塞。
异步 I/O：第二阶段应用进程不会阻塞。

同步 I/O 包括阻塞式 I/O、非阻塞式 I/O、I/O 复用和信号驱动 I/O ，它们的主要区别在第一个阶段。

非阻塞式 I/O 、信号驱动 I/O 和异步 I/O 在第一阶段不会阻塞。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/IO模型比较.png" width="600px">
</div>

### 多路复用

常见的多路复用 API 有 select、poll、epoll，它们是操作系统提供给用户线程的 API，用于取代用户线程轮询。如果是用户线程轮询就要涉及用户态和内核态的频繁切换，这部分开销是巨大的。

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
- epoll_wait 函数等待 epoll 事件从 epoll 实例中发生（rdllist 中存在或 timeout），并返回事件以及对应文件描述符。

当调用 epoll_create 函数时，内核会创建一个 eventpoll 结构体用于存储使用 epoll_ctl 函数向 epoll 对象中添加进来的事件。这些事件都存储在红黑树中，通过红黑树可以保持稳定高效的查询效率，而且可以高效地识别重复添加地事件。
所有添加到 epoll 中的事件都会与设备(网卡)驱动程序建立回调关系，当相应的事件发生时会调用这个回调方法，它会将发生的事件添加到 rdllist 链表中。
调用 epoll_wait 函数检查是否有事件发生时，只需要检查 eventpoll 对象中的 rdllist 链表中是否存在 epitem 元素(每一个事件都对应一个 epitem 结构体)，如果 rdllist 不为空，只需要把这些事件从内核态拷贝到用户态，同时返回事件数量即可。
因此，epoll 是十分高效地，可以轻松支持起百万级并发。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/epoll数据结构示意图.png" width="600px">
</div>

对于 fd 集合的拷贝问题，epoll 通过内核与用户空间 mmap(内存映射)将用户空间的一块地址和内核空间的一块地址同时映射到相同的一块物理内存地址，使得这块物理内存对内核和对用户均可见，减少用户态和内核态之间的数据交换。

综上，epoll 没有描述符个数限制，所以 epoll 不存在 select  中文件描述符数量限制问题；mmap 解决了 select 中用户态和内核态频繁切换的问题；通过 rdllist 解决了 select  中遍历所有事件的问题。
