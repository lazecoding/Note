# Future

在 Netty 中所有的 IO 操作都是异步的，不能立刻得到 IO 操作的执行结果，但是可以通过注册一个监听器来监听其执行结果。在 Java 的并发编程当中可以通过 Future 来进行异步结果的监听，但是在 Netty 当中是通过 ChannelFuture 来实现异步结果的监听。通过注册一个监听的方式进行监听，当操作执行成功或者失败时监听会自动触发注册的监听事件。

### ChannelFuture

ChannelFuture 继承了 Netty 的 Future，Netty 的 Future 继承了 JDK 的 Future。

ChannelFuture 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/netty/ChannelFuture类图.png" width="600px">
</div>

Netty 的 Future，在继承 JDK 的 Future 基础上，扩展了自己的方法：

```java
// io.netty.util.concurrent.Future.java
public interface Future<V> extends java.util.concurrent.Future<V> {
    // 任务执行成功，返回 true
    boolean isSuccess();

    // 任务被取消，返回 true
    boolean isCancellable();

    // 任务执行失败，返回异常
    Throwable cause();

    // 同步阻塞等待任务结束；执行失败话，会将 "异常信息" 重新抛出来
    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> var1);

    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... var1);

    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> var1);

    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... var1);

    // 同步阻塞等待任务结束；执行失败话，会将 "异常信息" 重新抛出来
    Future<V> sync() throws InterruptedException;

    Future<V> syncUninterruptibly();

    // 同步阻塞等待任务结束，和 sync 方法一样，只不过不会抛出异常信息
    Future<V> await() throws InterruptedException;

    Future<V> awaitUninterruptibly();

    boolean await(long var1, TimeUnit var3) throws InterruptedException;

    boolean await(long var1) throws InterruptedException;

    boolean awaitUninterruptibly(long var1, TimeUnit var3);

    boolean awaitUninterruptibly(long var1);

    // 非阻塞，获取执行结果
    V getNow();

    // 取消任务
    boolean cancel(boolean var1);
}
```

JDK 的 Future 的任务结果获取需要主动查询，而 Netty 的 Future 通过添加监听器 Listener，可以做到异步非阻塞处理任务结果，可以称为被动回调。

同步阻塞有两种方式：sync() 和 await()，区别：sync() 方法在任务失败后，会把异常信息抛出；await() 方法对异常信息不做任何处理，当我们不关心异常信息时可以用 await()。通过阅读源码可知 sync() 方法里面其实调的就是 await() 方法。推荐使用 sync() 方法。

```java
// DefaultPromise 类
@Override
public Promise<V> sync() throws InterruptedException {
    await();
    rethrowIfFailed();
    return this;
}
```

我们可以看到 Future 接口没有和 IO 操作关联在一起，接下来让我们来看看 Future 的子接口 ChannelFuture，它和 IO 操作中的 channel 关联在一起了，用于异步处理 channel 事件。

ChannelFuture 接口：

```java
// io.netty.channel.ChannelFuture.java
/**
 * The result of an asynchronous {@link Channel} I/O operation.
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which gives you the information about the
 * result or status of the I/O operation.
 * <p>
 * A {@link ChannelFuture} is either <em>uncompleted</em> or <em>completed</em>.
 * When an I/O operation begins, a new future object is created.  The new future
 * is uncompleted initially - it is neither succeeded, failed, nor cancelled
 * because the I/O operation is not finished yet.  If the I/O operation is
 * finished either successfully, with failure, or by cancellation, the future is
 * marked as completed with more specific information, such as the cause of the
 * failure.  Please note that even failure and cancellation belong to the
 * completed state.
 * <pre>
 *                                      +---------------------------+
 *                                      | Completed successfully    |
 *                                      +---------------------------+
 *                                 +---->      isDone() = true      |
 * +--------------------------+    |    |   isSuccess() = true      |
 * |        Uncompleted       |    |    +===========================+
 * +--------------------------+    |    | Completed with failure    |
 * |      isDone() = false    |    |    +---------------------------+
 * |   isSuccess() = false    |----+---->      isDone() = true      |
 * | isCancelled() = false    |    |    |       cause() = non-null  |
 * |       cause() = null     |    |    +===========================+
 * +--------------------------+    |    | Completed by cancellation |
 *                                 |    +---------------------------+
 *                                 +---->      isDone() = true      |
 *                                      | isCancelled() = true      |
 *                                      +---------------------------+
 * </pre>
 *
 * Various methods are provided to let you check if the I/O operation has been
 * completed, wait for the completion, and retrieve the result of the I/O
 * operation. It also allows you to add {@link ChannelFutureListener}s so you
 * can get notified when the I/O operation is completed.
 *
 * <h3>Prefer {@link #addListener(GenericFutureListener)} to {@link #await()}</h3>
 *
 * It is recommended to prefer {@link #addListener(GenericFutureListener)} to
 * {@link #await()} wherever possible to get notified when an I/O operation is
 * done and to do any follow-up tasks.
 * <p>
 * {@link #addListener(GenericFutureListener)} is non-blocking.  It simply adds
 * the specified {@link ChannelFutureListener} to the {@link ChannelFuture}, and
 * I/O thread will notify the listeners when the I/O operation associated with
 * the future is done.  {@link ChannelFutureListener} yields the best
 * performance and resource utilization because it does not block at all, but
 * it could be tricky to implement a sequential logic if you are not used to
 * event-driven programming.
 * <p>
 * By contrast, {@link #await()} is a blocking operation.  Once called, the
 * caller thread blocks until the operation is done.  It is easier to implement
 * a sequential logic with {@link #await()}, but the caller thread blocks
 * unnecessarily until the I/O operation is done and there's relatively
 * expensive cost of inter-thread notification.  Moreover, there's a chance of
 * dead lock in a particular circumstance, which is described below.
 *
 * <h3>Do not call {@link #await()} inside {@link ChannelHandler}</h3>
 * <p>
 * The event handler methods in {@link ChannelHandler} are usually called by
 * an I/O thread.  If {@link #await()} is called by an event handler
 * method, which is called by the I/O thread, the I/O operation it is waiting
 * for might never complete because {@link #await()} can block the I/O
 * operation it is waiting for, which is a dead lock.
 * <pre>
 * // BAD - NEVER DO THIS
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.awaitUninterruptibly();
 *     // Perform post-closure operation
 *     // ...
 * }
 *
 * // GOOD
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.addListener(new {@link ChannelFutureListener}() {
 *         public void operationComplete({@link ChannelFuture} future) {
 *             // Perform post-closure operation
 *             // ...
 *         }
 *     });
 * }
 * </pre>
 * <p>
 * In spite of the disadvantages mentioned above, there are certainly the cases
 * where it is more convenient to call {@link #await()}. In such a case, please
 * make sure you do not call {@link #await()} in an I/O thread.  Otherwise,
 * {@link BlockingOperationException} will be raised to prevent a dead lock.
 *
 * <h3>Do not confuse I/O timeout and await timeout</h3>
 *
 * The timeout value you specify with {@link #await(long)},
 * {@link #await(long, TimeUnit)}, {@link #awaitUninterruptibly(long)}, or
 * {@link #awaitUninterruptibly(long, TimeUnit)} are not related with I/O
 * timeout at all.  If an I/O operation times out, the future will be marked as
 * 'completed with failure,' as depicted in the diagram above.  For example,
 * connect timeout should be configured via a transport-specific option:
 * <pre>
 * // BAD - NEVER DO THIS
 * {@link Bootstrap} b = ...;
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly(10, TimeUnit.SECONDS);
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     // You might get a NullPointerException here because the future
 *     // might not be completed yet.
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 *
 * // GOOD
 * {@link Bootstrap} b = ...;
 * // Configure the connect timeout option.
 * <b>b.option({@link ChannelOption}.CONNECT_TIMEOUT_MILLIS, 10000);</b>
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly();
 *
 * // Now we are sure the future is completed.
 * assert f.isDone();
 *
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 * </pre>
 */
public interface ChannelFuture extends Future<Void> {

    /**
     * Returns a channel where the I/O operation associated with this
     * future takes place.
     */
    Channel channel();

    // 添加Listener，异步非阻塞处理回调结果
    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    // 同步阻塞等待任务结束；执行失败话，会将 "异常信息" 重新抛出来
    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture syncUninterruptibly();

    // 同步阻塞等待任务结束，和 sync 方法一样，只不过不会抛出异常信息
    @Override
    ChannelFuture await() throws InterruptedException;

    @Override
    ChannelFuture awaitUninterruptibly();

    // 

    /**
     * 
     * 标记 Futrue 是否为 Void，如果 ChannelFuture 是一个 void 的 Future，不允许调用 addListener()，await()，sync() 相关方法
     * 
     * Returns {@code true} if this {@link ChannelFuture} is a void future and so not allow to call any of the
     * following methods:
     * <ul>
     *     <li>{@link #addListener(GenericFutureListener)}</li>
     *     <li>{@link #addListeners(GenericFutureListener[])}</li>
     *     <li>{@link #await()}</li>
     *     <li>{@link #await(long, TimeUnit)} ()}</li>
     *     <li>{@link #await(long)} ()}</li>
     *     <li>{@link #awaitUninterruptibly()}</li>
     *     <li>{@link #sync()}</li>
     *     <li>{@link #syncUninterruptibly()}</li>
     * </ul>
     */
    boolean isVoid();
}
```

ChannelFuture 接口相比父类 Future 接口，就增加了 channel() 和 isVoid() 两个方法，其他方法都是覆盖父类接口。

ChannelFuture 只有两种状态 Uncompleted（未完成）和 Completed（完成），Completed 包括三种，执行成功，执行失败和任务取消。注意：执行失败和任务取消都属于 Completed。

```C
* <pre>
*                                      +---------------------------+
*                                      | Completed successfully    |
*                                      +---------------------------+
*                                 +---->      isDone() = true      |
* +--------------------------+    |    |   isSuccess() = true      |
* |        Uncompleted       |    |    +===========================+
* +--------------------------+    |    | Completed with failure    |
* |      isDone() = false    |    |    +---------------------------+
* |   isSuccess() = false    |----+---->      isDone() = true      |
* | isCancelled() = false    |    |    |       cause() = non-null  |
* |       cause() = null     |    |    +===========================+
* +--------------------------+    |    | Completed by cancellation |
*                                 |    +---------------------------+
*                                 +---->      isDone() = true      |
*                                      | isCancelled() = true      |
*                                      +---------------------------+
* </pre>
```

> Netty 的 Future 继承 JDK 的 Future，通过 Object 的 wait/notify 机制，实现了线程间的同步；使用观察者设计模式，实现了异步非阻塞回调处理。





