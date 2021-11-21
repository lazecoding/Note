# NioEventLoopGroup 和 NioEventLoop

在前面的 [Netty 架构模型](https://github.com/lazecoding/Note/blob/main/note/articles/netty/架构模型.md) 中，我们分析了 Netty 的线程处理模型：事件驱动模型。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/netty/Netty线程处理模型.png" width="600px">
</div>

流程分析：

- Boss Group 中的 Selector 通过 select 监听建立连接事件，当发生建立链接事件就与客户端建立关系并生成 NioSocketChannel，再将 NioSocketChannel 注册
  到 Worker Group 中的某个 NioEventLoop 的 Selector 上。
- Worker Group 中的 Selector 通过 select 监听 I/O 事件，当事件发生时在 NioSocketChannel 上执行 I/O 事件。

整个流程，NioEventLoopGroup 和 NioEventLoop 显得至关重要。

### NioEventLoopGroup

NioEventLoopGroup 从名字就可以看出它是 NioEventLoop 的集合。

NioEventLoopGroup 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/netty/NioEventLoopGroup类图.png" width="600px">
</div>

我们可以看到 NioEventLoopGroup 继承自 Executor，Executor 是线程池执行器接口，这意味着 NioEventLoopGroup 本身就是线程池的实现，相当于内部封装了线程。
既然 NioEventLoopGroup 是 NioEventLoop 的集合，那么意味着 NioEventLoopGroup 内部的线程封装是一组 NioEventLoop。

实例化 NioEventLoopGroup:

```java
// boos 1 个线程即可
NioEventLoopGroup boss = new NioEventLoopGroup(1);
// 默认值：核心数两倍
        NioEventLoopGroup worker = new NioEventLoopGroup();
```

我们进一步看 NioEventLoopGroup 构造函数，会发现跳转了很多方法，最终走到父类 MultithreadEventExecutorGroup 的构造函数。

NioEventLoopGroup 构造过程：

```java
// io/netty/channel/nio/NioEventLoopGroup.java#NioEventLoopGroup
public NioEventLoopGroup(int nThreads) {
        this(nThreads, (Executor) null);
        }

// io/netty/channel/nio/NioEventLoopGroup.java#NioEventLoopGroup
public NioEventLoopGroup(int nThreads, Executor executor) {
        this(nThreads, executor, SelectorProvider.provider());
        }

// io/netty/channel/nio/NioEventLoopGroup.java#NioEventLoopGroup
public NioEventLoopGroup(
        int nThreads, Executor executor, final SelectorProvider selectorProvider) {
        this(nThreads, executor, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
        }

// io/netty/channel/nio/NioEventLoopGroup.java#NioEventLoopGroup
public NioEventLoopGroup(int nThreads, Executor executor, final SelectorProvider selectorProvider,
final SelectStrategyFactory selectStrategyFactory) {
        // 父类是 io/netty/channel/MultithreadEventLoopGroup.java
        super(nThreads, executor, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
        }

// io/netty/channel/MultithreadEventLoopGroup.java#MultithreadEventLoopGroup
protected MultithreadEventLoopGroup(int nThreads, Executor executor, Object... args) {
        // 如果没设置 nThreads，默认是 DEFAULT_EVENT_LOOP_THREADS: NettyRuntime.availableProcessors() * 2；即 CPU 核心线程数的 2 倍
        // 父类是 io/netty/util/concurrent/MultithreadEventExecutorGroup
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, args);
        }

// io/netty/util/concurrent/MultithreadEventExecutorGroup.java#MultithreadEventExecutorGroup
protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
        }

// io/netty/util/concurrent/MultithreadEventExecutorGroup.java#MultithreadEventExecutorGroup
protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
        EventExecutorChooserFactory chooserFactory, Object... args) {
        // 真正构造的地方
        }
```

我们可以看到，在实例化 NioEventLoopGroup 的时候如果没有传入 nThreads 参数，构造函数默认设置为 `CPU 核心线程数 * 2`，最终构造的主体在
`io/netty/util/concurrent/MultithreadEventExecutorGroup.java` 中完成。

MultithreadEventExecutorGroup 构造函数：

```java
// io/netty/util/concurrent/MultithreadEventExecutorGroup.java#MultithreadEventExecutorGroup
protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
        EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) {
        throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        if (executor == null) {
        executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }
        // children 是个 EventExecutor[]，由线程池线程数量决定数组大小 
        children = new EventExecutor[nThreads];

        for (int i = 0; i < nThreads; i ++) {
        boolean success = false;
        try {
        // 重点！
        // newChild 是个接口，具体我们看 NioEventLoopGroup 中的实现
        // newChild 实际上创建的就是 NioEventLoop
        children[i] = newChild(executor, args);
        success = true;
        } catch (Exception e) {
        // TODO: Think about if this is a good exception type
        throw new IllegalStateException("failed to create a child event loop", e);
        } finally {
        if (!success) {
        for (int j = 0; j < i; j ++) {
        children[j].shutdownGracefully();
        }

        for (int j = 0; j < i; j ++) {
        EventExecutor e = children[j];
        try {
        while (!e.isTerminated()) {
        e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
        }
        } catch (InterruptedException interrupted) {
        // Let the caller handle the interruption.
        Thread.currentThread().interrupt();
        break;
        }
        }
        }
        }
        }

        // chooser 是一个分发器，根据 eventLoopGroup 中的 loop 数量来选择分发策略，把接受得到的事件分发给 Group 中的 EventLoop 执行。
        // next() 其实也是通过 chooser 完成的。
        // public EventExecutor next() {
        //    return chooser.next();
        // }
        chooser = chooserFactory.newChooser(children);

final FutureListener<Object> terminationListener = new FutureListener<Object>() {
@Override
public void operationComplete(Future<Object> future) throws Exception {
        if (terminatedChildren.incrementAndGet() == children.length) {
        terminationFuture.setSuccess(null);
        }
        }
        };

        for (EventExecutor e: children) {
        e.terminationFuture().addListener(terminationListener);
        }

        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
        }
```

我们可以看到，NioEventLoopGroup 线程数设置了多少，就创建多少个 EventLoop 存储在数组 children 中。children 是个 EventExecutor[] 数组，
这里存储的是 `NioEventLoopGroup#newChild` 返回的 NioEventLoop。

newChild 是个接口，我们这里需要关注它在 NioEventLoopGroup 中的实现。

NioEventLoopGroup#newChild：

```java
// io/netty/channel/nio/NioEventLoopGroup.java#newChild
@Override
protected EventLoop newChild(Executor executor, Object... args) throws Exception {
    return new NioEventLoop(this, executor, (SelectorProvider) args[0],
        ((SelectStrategyFactory) args[1]).newSelectStrategy(), (RejectedExecutionHandler) args[2]);
}
```

可以看到，newChild 其实就是 `new NioEventLoop`。

另一个的方法是 `EventExecutorChooserFactory#newChooser`，它也是一个接口，我们关注它的实现类  DefaultEventExecutorChooserFactory。它的作用是创建一个 EventLoop 分发器，根据 eventLoopGroup 中的线程池确定分片策略。

DefaultEventExecutorChooserFactory#newChooser：

```java
// io/netty/util/concurrent/DefaultEventExecutorChooserFactory.java#newChooser
public EventExecutorChooser newChooser(EventExecutor[] executors) {
    if (isPowerOfTwo(executors.length)) {
        return new PowerOfTwoEventExecutorChooser(executors);
    } else {
        return new GenericEventExecutorChooser(executors);
    }
}
```

根据 NioEventLoopGroup 中的线程数，EventExecutorChooser 分成两种策略，如果是 2 的 N 次幂采用 PowerOfTwoEventExecutorChooser，否则采用 GenericEventExecutorChooser。他们的区别就是一个采用位运算（&），一个采用取模运算（%），显然，位运算具有更好的性能，所以 `建议 NioEventLoopGroup 的线程数量设置成 2 的 N 次幂`。

分发器策略代码：

```java
private static final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
    private final AtomicInteger idx = new AtomicInteger();
    private final EventExecutor[] executors;

    PowerOfTwoEventExecutorChooser(EventExecutor[] executors) {
        this.executors = executors;
    }

    @Override
    public EventExecutor next() {
        return executors[idx.getAndIncrement() & executors.length - 1];
    }
}

private static final class GenericEventExecutorChooser implements EventExecutorChooser {
    private final AtomicInteger idx = new AtomicInteger();
    private final EventExecutor[] executors;

    GenericEventExecutorChooser(EventExecutor[] executors) {
        this.executors = executors;
    }

    @Override
    public EventExecutor next() {
        return executors[Math.abs(idx.getAndIncrement() % executors.length)];
    }
}
```