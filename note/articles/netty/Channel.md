# Channel

- 目录
    - [AbstractChannel](#AbstractChannel)
        - [Channel 什么时候被创建](#Channel-什么时候被创建)
    - [ChannelHandler](#ChannelHandler)
        - [Sharable 注解](#Sharable-注解)

Channel 表示 IO 源与目标打开的连接，Java Channel 是由 `java.nio.channels` 包定义的。Channel 类似于传统的 "流"。只不过 Channel 本身不能直接访问数据，Channel 只能与 Buffer 进行交互。

常见的 Channel 有以下四种，其中 FileChannel 主要用于文件传输，其余三种用于网络通信。

- FileChannel
- DatagramChannel
- SocketChannel
- ServerSocketChannel

更多 [Java Channel](https://github.com/lazecoding/Note/blob/main/note/articles/java/NIO.md#Channel) 内容,点击查看。

Netty 对 Channel 也进行了封装，Channel 是 Netty 的核心概念之一，它是 Netty 网络通信的主体，由它负责同对端进行网络通信、注册和数据操作等功能。

### AbstractChannel

AbstractChannel 是 Netty 对 Channel 的抽象，类图如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/netty/AbstractChannel类图.png" width="600px">
</div>

AbstractChannel 是所有 Channel 的父类，这里我们关注的是 NIO 实现的 Channel，尤指 NioServerSocketChannel（服务端） 和 ServerSocketChannel（客户端）。

与 JDK NIO 呼应的，NioServerSocketChannel 与 ServerSocketChannel，ServerSocketChannel 与 SocketChannel 是一一对应的。

AbstractChannel 构造函数：

```java
// io/netty/channel/AbstractChannel.java#AbstractChannel
protected AbstractChannel(Channel parent) {
    this.parent = parent;
    // 每个 Channel 的实例都应该有一个唯一的 id
    id = newId();
    // 如 AbstractNioByteChannel$NioByteUnsafe、AbstractNioMessageChannel.java$NioMessageUnsafe
    unsafe = newUnsafe();
    // 为 Channel 设置 Pipeline
    pipeline = newChannelPipeline();
}
```

AbstractChannel 构造函数中，会为每个 Channel 设置一个唯一的 id、注入 unsafe 实现类、绑定 Pipeline。

AbstractNioChannel 构造函数：

```java
// io/netty/channel/nio/AbstractNioChannel.java:86
protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
    super(parent);
    this.ch = ch;
    // 监听的事件
    this.readInterestOp = readInterestOp;
    try {
        // 设置为非阻塞
        ch.configureBlocking(false);
    } catch (IOException e) {
        try {
            ch.close();
        } catch (IOException e2) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Failed to close a partially initialized socket.", e2);
            }
        }

        throw new ChannelException("Failed to enter non-blocking mode.", e);
    }
}
```

AbstractNioChannel 构造函数主要是设置了监听的事件和将 Channel 设置为非阻塞。

NioServerSocketChannel 和 ServerSocketChannel 是 AbstractNioChannel 的子类，它们的构造思路是一直的。

NioServerSocketChannel 构造函数：

```java
// io/netty/channel/socket/nio/NioServerSocketChannel.java#NioServerSocketChannel
public NioServerSocketChannel(SelectorProvider provider) {
    // newSocket 顾名思义，创建 SocketChannel，这里是创建 JDK ServerSocketChannel
    this(newSocket(provider));
}

// io/netty/channel/socket/nio/NioServerSocketChannel.java#newSocket
private static ServerSocketChannel newSocket(SelectorProvider provider) {
    try {
        // 创建 JDK ServerSocketChannel
        return provider.openServerSocketChannel();
    } catch (IOException e) {
        throw new ChannelException(
                "Failed to open a server socket.", e);
    }
}

// io/netty/channel/socket/nio/NioServerSocketChannel.java#NioServerSocketChannel
public NioServerSocketChannel(ServerSocketChannel channel) {
    // AbstractNioMessageChannel > AbstractNioChannel
    super(null, channel, SelectionKey.OP_ACCEPT);
    // 设置参数,关联 NioServerSocketChannel 和 ServerSocketChannel
    config = new NioServerSocketChannelConfig(this, javaChannel().socket());
}
```

NioServerSocketChannel 构造函数相比 AbstractNioChannel，创建了 JDK ServerSocketChannel 并设置参数，关联 NioServerSocketChannel 和 ServerSocketChannel。

NioSocketChannel 构造函数：

```java
// io/netty/channel/socket/nio/NioSocketChannel.java#NioSocketChannel
public NioSocketChannel(SelectorProvider provider) {
    // newSocket 顾名思义，创建 SocketChannel，这里是创建 JDK SocketChannel
    this(newSocket(provider));
}

// io/netty/channel/socket/nio/NioSocketChannel.java#newSocket
private static SocketChannel newSocket(SelectorProvider provider) {
    try {
        // 创建 JDK SocketChannel
        return provider.openSocketChannel();
    } catch (IOException e) {
        throw new ChannelException("Failed to open a socket.", e);
    }
}

public NioSocketChannel(Channel parent, SocketChannel socket) {
    // AbstractNioByteChannel > AbstractNioChannel
    super(parent, socket);
    // 设置参数,关联 NioSocketChannel 和 SocketChannel
    config = new NioSocketChannelConfig(this, socket.socket());
}
```

NioServerSocketChannel 构造函数与 NioServerSocketChannel 类似，创建了 JDK SocketChannel 并设置参数，关联 NioSocketChannel 和 SocketChannel。

#### Channel 什么时候被创建

从前面的学习，我们知道 Boss Group 处理建立连接事件，Worker Group 处理读写。既然 Channel 是数据交互的通道，那么理所当然在 Boss Group
处理建立连接事件时候，会创建 Channel。

点击 [EventLoop 处理 accept 事件](https://github.com/lazecoding/Note/blob/main/note/articles/netty/NioEventLoopInGroup.md#accept-事件) 了解更多。

`io/netty/channel/socket/nio/NioServerSocketChannel.java#doReadMessages`。

但是客户端想通过 Channel 进行通信之前，还需要服务端绑定操作系统端口，服务端想和操作系统通信也需要建立 Channel。

这意味着在服务端启动时候，写需要创建 Channel，再绑定端口才能提供服务保证和客户端的数据交互。

点击 [服务端初始化和注册](https://github.com/lazecoding/Note/blob/main/note/articles/netty/启动流程.md#initAndRegisterd) 了解更多。

总的说，Channel 的创建时机：

- 服务端启动时候，初始化 Channel 以注册绑定。
- Boss Group 处理 accept 事件，为连接创建 Channel。

> 总的说，Netty Channel 主要实例化了 JDK Channel 并设置了非阻塞模式，为 Channel 设置唯一 Id、unsafe 和 Pipeline。

### ChannelHandler

Channel 有个简单但强大的状态模型，与 ChannelInboundHandler API 密切相关。Channel 的 4 个状态如下：

- channelUnregistered：channel 创建但未注册到一个 EventLoop。
- channelRegistered：channel 注册到一个 EventLoop。
- channelActive：channel 的活动的(连接到了它的 remote peer（远程对等方）)，现在可以接收和发送数据了。
- channelInactive：channel 没有连接到 remote peer（远程对等方）。

当 Channel 状态变化出现，对应的事件将会生成，这样与 ChannelPipeline 中的 ChannelHandler 的交互就能及时响应。Channel 的一般的生命周期如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/netty/Channel状态转移示意图.png" width="600px">
</div>

<br>

ChannelHandler 定义的生命周期操作：当 ChannelHandler 添加到 ChannelPipeline，或者从 ChannelPipeline 移除后，这些将会调用。每个方法都会带 ChannelHandlerContext 参数。

- handlerAdded：当 ChannelHandler 添加到 ChannelPipeline 调用。
- handlerRemoved：当 ChannelHandler 从 ChannelPipeline 移除时调用。
- exceptionCaught：当 ChannelPipeline 执行发生错误时调用。

Netty 提供 2 个重要的 ChannelHandler 子接口：

- ChannelInboundHandler：处理进站数据，并且所有状态都更改。
- ChannelOutboundHandler：处理出站数据，允许拦截各种操作。

打个比喻，每个 Channel 是一个产品的加工车间，Pipeline 是车间中的流水线，ChannelHandler 就是流水线上的各道工序，而后面要讲的 ByteBuf 是原材料，经过很多工序的加工：
先经过一道道入站工序，再经过一道道出站工序最终变成产品。

#### Sharable 注解

ChannelHandler@Sharable：

```java
// io/netty/channel/ChannelHandler.java@Sharable
/**
* Indicates that the same instance of the annotated {@link ChannelHandler}
* can be added to one or more {@link ChannelPipeline}s multiple times
* without a race condition.
* <p>
* If this annotation is not specified, you have to create a new handler
* instance every time you add it to a pipeline because it has unshared
* state such as member variables.
* <p>
* This annotation is provided for documentation purpose, just like
* <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
*/
@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface Sharable {
    // no value
}
```

添加 @Sharable 注解的 ChannelHandler 的同一个实例可以多次添加到 ChannelPipeline 中而不存在竞争条件。