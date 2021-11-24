# ChannelPipeline

- 目录
    - [ChannelPipeline 接口类](#ChannelPipeline-接口类)
    - [实例化](#实例化)
    - [ChannelPipeline 中存储着什么](#ChannelPipeline-中存储着什么)

我们可以认为 ChannelPipeline 是一系列 ChannelHandler 实例,用于拦截流经一个 Channel 的入站和出站事件,这些 ChannelHandler 可以提供的交互的核心应用程序的数据和事件处理逻辑。
每创建一个 Channel 都会分配一个新的 ChannelPipeline。这个关联是永久性的：Channel 既不能附上另一个 ChannelPipeline 也不能分离当前这个。

根据事件的起源，一个事件将由 ChannelInboundHandler 或 ChannelOutboundHandler 处理。随后它将调用 ChannelHandlerContext 实现转发到下一个相同的超类型的处理程序。

一组 ChannelHandler 的典型 ChannelPipeline 布局如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/netty/一组ChannelHandler的典型ChannelPipeline布局示意图.png" width="600px">
</div>

可以看到，ChannelPipeline 的实现是一个 ChannelHandlerContext 组成的双向链表。
ChannelInboundHandler 是按照 addLast 的顺序执行的，而 ChannelOutboundHandler 是按照 addLast 的逆序执行的。

### ChannelPipeline 接口类

ChannelPipeline 是一个接口类，下面是它的接口方法：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/netty/ChannelPipeline接口类方法.png" width="600px">
</div>

从图中，我们可以大致看出，这是一个类似列表对象的 API，因为 ChannelPipeline 本身就是一个由 ChannelHandlerContext 组成的双向链表。

> ChannelHandlerContext 代表 ChannelHandler 和ChannelPipeline 之间的关联,并在 ChannelHandler 添加到 ChannelPipeline 时创建一个实例。
ChannelHandlerContext 的主要功能是管理通过同一个 ChannelPipeline 关联的 ChannelHandler 之间的交互。

不如，先欣赏一下 ChannelPipeline 的注释：

```java
/**
 * A list of {@link ChannelHandler}s which handles or intercepts inbound events and outbound operations of a
 * {@link Channel}.  {@link ChannelPipeline} implements an advanced form of the
 * <a href="http://www.oracle.com/technetwork/java/interceptingfilter-142169.html">Intercepting Filter</a> pattern
 * to give a user full control over how an event is handled and how the {@link ChannelHandler}s in a pipeline
 * interact with each other.
 *
 * <h3>Creation of a pipeline</h3>
 *
 * Each channel has its own pipeline and it is created automatically when a new channel is created.
 *
 * <h3>How an event flows in a pipeline</h3>
 *
 * The following diagram describes how I/O events are processed by {@link ChannelHandler}s in a {@link ChannelPipeline}
 * typically. An I/O event is handled by either a {@link ChannelInboundHandler} or a {@link ChannelOutboundHandler}
 * and be forwarded to its closest handler by calling the event propagation methods defined in
 * {@link ChannelHandlerContext}, such as {@link ChannelHandlerContext#fireChannelRead(Object)} and
 * {@link ChannelHandlerContext#write(Object)}.
 *
 * <pre>
 *                                                 I/O Request
 *                                            via {@link Channel} or
 *                                        {@link ChannelHandlerContext}
 *                                                      |
 *  +---------------------------------------------------+---------------+
 *  |                           ChannelPipeline         |               |
 *  |                                                  \|/              |
 *  |    +---------------------+            +-----------+----------+    |
 *  
 *  |    | Inbound Handler  N  |            | Outbound Handler  1  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler N-1 |            | Outbound Handler  2  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  .               |
 *  |               .                                   .               |
 *  | ChannelHandlerContext.fireIN_EVT() ChannelHandlerContext.OUT_EVT()|
 *  |        [ method call]                       [method call]         |
 *  |               .                                   .               |
 *  |               .                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  2  |            | Outbound Handler M-1 |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  1  |            | Outbound Handler  M  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  +---------------+-----------------------------------+---------------+
 *                  |                                  \|/
 *  +---------------+-----------------------------------+---------------+
 *  |               |                                   |               |
 *  |       [ Socket.read() ]                    [ Socket.write() ]     |
 *  |                                                                   |
 *  |  Netty Internal I/O Threads (Transport Implementation)            |
 *  +-------------------------------------------------------------------+
 * </pre>
 * An inbound event is handled by the inbound handlers in the bottom-up direction as shown on the left side of the
 * diagram.  An inbound handler usually handles the inbound data generated by the I/O thread on the bottom of the
 * diagram.  The inbound data is often read from a remote peer via the actual input operation such as
 * {@link SocketChannel#read(ByteBuffer)}.  If an inbound event goes beyond the top inbound handler, it is discarded
 * silently, or logged if it needs your attention.
 * <p>
 * An outbound event is handled by the outbound handler in the top-down direction as shown on the right side of the
 * diagram.  An outbound handler usually generates or transforms the outbound traffic such as write requests.
 * If an outbound event goes beyond the bottom outbound handler, it is handled by an I/O thread associated with the
 * {@link Channel}. The I/O thread often performs the actual output operation such as
 * {@link SocketChannel#write(ByteBuffer)}.
 * <p>
 * For example, let us assume that we created the following pipeline:
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * p.addLast("1", new InboundHandlerA());
 * p.addLast("2", new InboundHandlerB());
 * p.addLast("3", new OutboundHandlerA());
 * p.addLast("4", new OutboundHandlerB());
 * p.addLast("5", new InboundOutboundHandlerX());
 * </pre>
 * In the example above, the class whose name starts with {@code Inbound} means it is an inbound handler.
 * The class whose name starts with {@code Outbound} means it is a outbound handler.
 * <p>
 * In the given example configuration, the handler evaluation order is 1, 2, 3, 4, 5 when an event goes inbound.
 * When an event goes outbound, the order is 5, 4, 3, 2, 1.  On top of this principle, {@link ChannelPipeline} skips
 * the evaluation of certain handlers to shorten the stack depth:
 * <ul>
 * <li>3 and 4 don't implement {@link ChannelInboundHandler}, and therefore the actual evaluation order of an inbound
 *     event will be: 1, 2, and 5.</li>
 * <li>1 and 2 don't implement {@link ChannelOutboundHandler}, and therefore the actual evaluation order of a
 *     outbound event will be: 5, 4, and 3.</li>
 * <li>If 5 implements both {@link ChannelInboundHandler} and {@link ChannelOutboundHandler}, the evaluation order of
 *     an inbound and a outbound event could be 125 and 543 respectively.</li>
 * </ul>
 *
 * <h3>Forwarding an event to the next handler</h3>
 *
 * As you might noticed in the diagram shows, a handler has to invoke the event propagation methods in
 * {@link ChannelHandlerContext} to forward an event to its next handler.  Those methods include:
 * <ul>
 * <li>Inbound event propagation methods:
 *     <ul>
 *     <li>{@link ChannelHandlerContext#fireChannelRegistered()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelActive()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelRead(Object)}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelReadComplete()}</li>
 *     <li>{@link ChannelHandlerContext#fireExceptionCaught(Throwable)}</li>
 *     <li>{@link ChannelHandlerContext#fireUserEventTriggered(Object)}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelWritabilityChanged()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelInactive()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelUnregistered()}</li>
 *     </ul>
 * </li>
 * <li>Outbound event propagation methods:
 *     <ul>
 *     <li>{@link ChannelHandlerContext#bind(SocketAddress, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#connect(SocketAddress, SocketAddress, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#write(Object, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#flush()}</li>
 *     <li>{@link ChannelHandlerContext#read()}</li>
 *     <li>{@link ChannelHandlerContext#disconnect(ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#close(ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#deregister(ChannelPromise)}</li>
 *     </ul>
 * </li>
 * </ul>
 *
 * and the following example shows how the event propagation is usually done:
 *
 * <pre>
 * public class MyInboundHandler extends {@link ChannelInboundHandlerAdapter} {
 *     {@code @Override}
 *     public void channelActive({@link ChannelHandlerContext} ctx) {
 *         System.out.println("Connected!");
 *         ctx.fireChannelActive();
 *     }
 * }
 *
 * public class MyOutboundHandler extends {@link ChannelOutboundHandlerAdapter} {
 *     {@code @Override}
 *     public void close({@link ChannelHandlerContext} ctx, {@link ChannelPromise} promise) {
 *         System.out.println("Closing ..");
 *         ctx.close(promise);
 *     }
 * }
 * </pre>
 *
 * <h3>Building a pipeline</h3>
 * <p>
 * A user is supposed to have one or more {@link ChannelHandler}s in a pipeline to receive I/O events (e.g. read) and
 * to request I/O operations (e.g. write and close).  For example, a typical server will have the following handlers
 * in each channel's pipeline, but your mileage may vary depending on the complexity and characteristics of the
 * protocol and business logic:
 *
 * <ol>
 * <li>Protocol Decoder - translates binary data (e.g. {@link ByteBuf}) into a Java object.</li>
 * <li>Protocol Encoder - translates a Java object into binary data.</li>
 * <li>Business Logic Handler - performs the actual business logic (e.g. database access).</li>
 * </ol>
 *
 * and it could be represented as shown in the following example:
 *
 * <pre>
 * static final {@link EventExecutorGroup} group = new {@link DefaultEventExecutorGroup}(16);
 * ...
 *
 * {@link ChannelPipeline} pipeline = ch.pipeline();
 *
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 *
 * // Tell the pipeline to run MyBusinessLogicHandler's event handler methods
 * // in a different thread than an I/O thread so that the I/O thread is not blocked by
 * // a time-consuming task.
 * // If your business logic is fully asynchronous or finished very quickly, you don't
 * // need to specify a group.
 * pipeline.addLast(group, "handler", new MyBusinessLogicHandler());
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * A {@link ChannelHandler} can be added or removed at any time because a {@link ChannelPipeline} is thread safe.
 * For example, you can insert an encryption handler when sensitive information is about to be exchanged, and remove it
 * after the exchange.
 */
public interface ChannelPipeline
        extends ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Entry<String, ChannelHandler>> {
    
    // ...

}
```

服了服了，好的代码是这样，注释都优雅完美。

看注释，可以知道，ChannelPipeline 除了维护双向链表，还提供了 `event propagation methods`（事件传播方法），也就是之前我们看到的，Pipeline 触发 XXX 事件。

### 实例化

ChannelPipeline 是接口类，它的子类是 DefaultChannelPipeline。从 [Channel](https://github.com/lazecoding/Note/blob/main/note/articles/netty/Channel.md) 中我们知道，
在创建 Channel 的时候，会为 Channel 实例化一个 ChannelPipeline。

DefaultChannelPipeline 构造函数：

```java
// io/netty/channel/DefaultChannelPipeline.java#DefaultChannelPipeline
protected DefaultChannelPipeline(Channel channel) {
    // 当前 pipeline 所属的 channel
    this.channel = ObjectUtil.checkNotNull(channel, "channel");
    succeededFuture = new SucceededChannelFuture(channel, null);
    voidPromise =  new VoidChannelPromise(channel, true);

    // TailContext 是一种可以同时处理字节和消息的特殊的 hander
    tail = new TailContext(this);
    head = new HeadContext(this);

    // 这时候还是只有头尾的链表
    head.next = tail;
    tail.prev = head;
}
```

刚刚实例化的 DefaultChannelPipeline 是一个只有头、尾的双向链表。

### ChannelPipeline 中存储着什么

ChannelPipeline 中存储着什么？ChannelHandler？

前面我们说过：`ChannelPipeline 的实现是一个 ChannelHandlerContext 组成的双向链表`。

我们通过 `ChannelPipeline#addLast` 方法分析 ChannelPipeline 中存储的元素。

DefaultChannelPipeline#addLast：

```java
// io/netty/channel/DefaultChannelPipeline.java#addLast
@Override
public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
    final AbstractChannelHandlerContext newCtx;
    synchronized (this) {
        // 检查是否多例，即检查 @Sharable
        checkMultiplicity(handler);
        // 将 hander 包装成 ChannelHandlerContext
        newCtx = newContext(group, filterName(name, handler), handler);
        // 加入 Pipeline
        addLast0(newCtx);

        // 如果 registered is false ，说明 channel 没有在 eventloop 注册
        // 那么由 callHandlerCallbackLater(newCtx, true); 添加一个 ChannelHandler.handlerAdded(...) 任务，在 channel 被注册时候触发
        // 任务主体是 callHandlerAdded0()，这是一个回调方法
        if (!registered) {
            newCtx.setAddPending();
            callHandlerCallbackLater(newCtx, true);
            return this;
        }

        EventExecutor executor = newCtx.executor();
        if (!executor.inEventLoop()) {
            newCtx.setAddPending();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    // 回调方法
                    callHandlerAdded0(newCtx);
                }
            });
            return this;
        }
    }
    // 回调方法
    callHandlerAdded0(newCtx);
    return this;
}
```

`DefaultChannelPipeline#addLast` 方法其实很简单，就是将 hander 包装成 ChannelHandlerContext 再放入 Pipeline 中，
然后调用回调方法。

所以说：`ChannelPipeline 的实现是一个 ChannelHandlerContext 组成的双向链表`。