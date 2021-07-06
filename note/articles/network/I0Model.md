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

