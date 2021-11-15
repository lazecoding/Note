# Reactor

- 目录
    - [单 Reactor 单线程](#单-Reactor-单线程)
    - [单 Reactor 多线程](#单-Reactor-多线程)
    - [主从 Reactor 多线程](#主从-Reactor-多线程)
    - [应用和优势](#应用和优势)

Reactor 是事件驱动模型，当服务端接收到一个或多个请求，由专门的线程处理传入的多路请求，并将它们同步分派给请求对应的处理线程。

Reactor 模型中有 2 个关键组成：

- Reactor：Reactor 在一个单独的线程中运行，负责监听和分发事件，分发给适当的处理程序来对IO事件做出反应。
- Handlers：Handlers 处理程序执行I/O事件要完成的实际事件。

Reactor 通过调度适当的处理程序来响应 I/O 事件，处理程序执行非阻塞操作。

根据 Reactor 的数量和处理资源池线程的数量不同，有 3 种典型的实现：

- 单 Reactor 单线程
- 单 Reactor 多线程
- 主从 Reactor 多线程

> 注意：Reactor 是建立在 I/O 复用模型基础上的，即 select 可以实现应用程序通过一个阻塞对象监听多路连接请求。

### 单 Reactor 单线程

单 Reactor 单线程模型图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/architecturemodel/单Reactor单线程.png" width="600px">
</div>

流程分析：

- Reactor 通过 select 监听客户端请求事件，收到事件后通过 dispatch 进行分发。
- 如果是建立连接请求事件，由 Acceptor 通过 accept 处理连接请求。
- 如果不是建立连接事件，则 Reactor 会分发调用连接对应的 Handler 来响应。
- Handler 负责完成 `read -> 业务处理 -> send` 的完整业务流程。

优点：

    模型简单，单线程没有并发安全问题，适合轻量短小的业务。

不足：

    单线程设计，一方面无法充分利用 CPU 多核特性，另一方面如果线程执行业务阻塞或者耗时，会影响整个系统的可用性。

### 单 Reactor 多线程

单 Reactor 多线程模型图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/architecturemodel/单Reactor多线程.png" width="600px">
</div>


流程分析：

- Reactor 通过 select 监听客户端请求事件，收到事件后通过 dispatch 进行分发。
- 如果是建立连接请求事件，由 Acceptor 通过 accept 处理连接请求。
- 如果不是建立连接事件，则 Reactor 会分发调用连接对应的 Handler 来响应。
- Handler只负责响应事件，不做具体业务处理，通过 read 读取数据后，会分发给后面的 Worker 线程池进行业务处理。
- Worker 线程池会分配独立的线程完成真正的业务处理，之后将响应结果发给 Handler。
- Handler 收到响应结果后通过 send 将响应结果返回给客户端。

优点：

    可以充分利用多核 CPU 的处理能力。

不足：

    多线程数据共享和访问比较复杂；Reactor 承担所有事件的监听和响应，在单线程中运行，高并发场景下容易成为性能瓶颈。

### 主从 Reactor 多线程

主从 Reactor 多线程模型图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/architecturemodel/主从Reactor多线程.png" width="600px">
</div>

流程分析：

- Reactor 主线程 MainReactor 对象通过 select 监听建立连接事件，收到事件后由 Acceptor 通过 accept 处理连接请求。
- Acceptor 处理建立连接事件后，MainReactor 将连接分配 Reactor 子线程给 SubReactor，由 SubReactor 监听 I/O 事件。
- 当有新的事件发生时，SubReactor 会调用连接对应的 Handler 进行响应。
- Handler 通过 read 读取数据后，会分发给后面的 Worker 线程池进行业务处理。
- Worker 线程池会分配独立的线程完成真正的业务处理，之后将响应结果发给 Handler。
- Handler 收到响应结果后通过 send 将响应结果返回给客户端。

优点：

    责任明确，父线程负责处理连接事件，子线程负责 I/O 事件；worker 线程池完成业务操作，避免了单一线程阻塞而导致程序不可以的问题。

### 应用和优势

很多程序都借鉴了 Reactor 模式，如 Redis 的事件驱动模块，Nginx 的主从 Reactor 多进程设计，Netty 的主从多线程模型等。

Reactor 优势：

- 响应快，不必为单个同步时间所阻塞，虽然 Reactor 本身依然是同步的。
- 编程相对简单，可以最大程度的避免复杂的多线程及同步问题，并且避免了多线程/进程的切换开销。
- 可扩展性，可以方便的通过增加 Reactor 实例个数来充分利用 CPU 资源。
- 可复用性，Reactor 模型本身与具体事件处理逻辑无关，具有很高的复用性。

