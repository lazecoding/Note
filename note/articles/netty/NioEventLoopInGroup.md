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

### NioEventLoop

从上面我们知道，NioEventLoop 是 NioEventLoopGroup 中的元素，一个线程数就对应着一个 NioEventLoop，从这里我们基本可以确定，是 NioEventLoop 封装了线程。

NioEventLoop 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/netty/NioEventLoop类图.png" width="600px">
</div>

<br>

从类图中，我们可以看到 NioEventLoop 层层继承，其中有一个父类是 SingleThreadEventExecutor，从名字看它是一个单线程的事件执行器。从这里我们已经知道，NioEventLoop 是通过 Executor 管理线程的，而且是单个线程。

下面我们开始分析 NioEventLoop。

NioEventLoop 构造函数：

```java
// io/netty/channel/nio/NioEventLoop.java#NioEventLoop
NioEventLoop(NioEventLoopGroup parent, Executor executor, SelectorProvider selectorProvider,
             SelectStrategy strategy, RejectedExecutionHandler rejectedExecutionHandler) {
    super(parent, executor, false, DEFAULT_MAX_PENDING_TASKS, rejectedExecutionHandler);
    if (selectorProvider == null) {
        throw new NullPointerException("selectorProvider");
    }
    if (strategy == null) {
        throw new NullPointerException("selectStrategy");
    }
    provider = selectorProvider;
    final SelectorTuple selectorTuple = openSelector();
    selector = selectorTuple.selector;
    unwrappedSelector = selectorTuple.unwrappedSelector;
    selectStrategy = strategy;
}
```

NioEventLoop 构造函数中主要完成了 Selector 的实例化并将它注入 NioEventLoop 实例中。其中，`openSelector()` 完成了 Selector 的实例化，底层调用 Java 实现并通过反射修改私有属性，不做详细说明。

看到这里，NioEventLoop 在构造函数中并没有创建线程，所以 NioEventLoop 创建线程的时间不是 NioEventLoopGroup 初始化的时候（服务端启动时）。

#### 线程创建的时机

我们现在第一个疑惑是：NioEventLoop 中的线程究竟是何时创建的。

上面说过，NioEventLoop 继承自 SingleThreadEventExecutor：

```java
// io/netty/util/concurrent/SingleThreadEventExecutor.java

// 任务队列
private final Queue<Runnable> taskQueue;

// 线程
private volatile Thread thread;
```

NioEventLoop 继承了 SingleThreadEventExecutor 的 taskQueue（任务队列）和一个 thread（处理任务的线程）。

我们使用 NioEventLoopGroup 来执行任务：

```java
EventLoop eventLoop = new NioEventLoopGroup().next();
// 获取一个 NioEventLoop 执行任务
eventLoop.execute(()->{
    System.out.println("doTask");
});
```

当我们执行 `EventLoop.execute(...);`,调用的是 `SingleThreadEventExecutor#execute`。

SingleThreadEventExecutor#execute：

```java
// io/netty/util/concurrent/SingleThreadEventExecutor.java#execute
@Override
public void execute(Runnable task) {
    if (task == null) {
        throw new NullPointerException("task");
    }

    // 判断当前线程是否为 NIO 线程
    // 判断方法为 return thread == this.thread;
    // this.thread 即为 NIO 线程，首次执行任务时，其为 null
    boolean inEventLoop = inEventLoop();

    if (inEventLoop) {
        // 当前线程是 NIO 线程 分支
        // 向任务队列 taskQueue 中添加任务
        addTask(task);
    } else {
        // 当前线程不是 NIO 线程 分支
        // 启动 NIO 线程的核心方法
        startThread();
        // 向任务队列 taskQueue 中添加任务
        addTask(task);
        if (isShutdown() && removeTask(task)) {
            reject();
        }
    }

    // 有任务需要被执行时，唤醒阻塞的 NIO 线程
    if (!addTaskWakesUp && wakesUpForTask(task)) {
        wakeup(inEventLoop);
    }
}
```

我们可以看到，NioEventLoop 中的线程，只通过 NIO 线程添加任务，如果不是就先使用 `startThread()` 创建线程再添加任务。最后检查如果有待执行的任务就去唤醒 NIO 线程，只有唤醒 NIO 线程干什么，后面细说（肯定是执行任务辣）。

> 所以说，NioEventLoop 中的线程是第一个任务执行时创建的。

SingleThreadEventExecutor#startThread:

```java
// io/netty/util/concurrent/SingleThreadEventExecutor.java#startThread
private void startThread() {
    // 查看 NIO 线程状态是否为未启动
    // 该 if 代码块只会执行一次
    // state 一开始的值就是 ST_NOT_STARTED
    // private volatile int state = ST_NOT_STARTED;
    if (state == ST_NOT_STARTED) {
        // 通过原子属性更新器将状态更新为启动（ST_STARTED）
        if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
            boolean success = false;
            try {
                // 执行启动线程
                doStartThread();
                success = true;
            } finally {
                if (!success) {
                    STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                }
            }
        }
    }
}
```

这里通过 CAS 保证 state 的线程安全，意味着只会创建一个线程。

SingleThreadEventExecutor#doStartThread：

```java
// io/netty/util/concurrent/SingleThreadEventExecutor.java#doStartThread
private void doStartThread() {
    assert thread == null;
    // 创建 NIO 线程并执行任务
    executor.execute(new Runnable() {
        @Override
        public void run() {
            // 这个 thread 即为 NIO 线程
            thread = Thread.currentThread();
            if (interrupted) {
                thread.interrupt();
            }

            boolean success = false;
            updateLastExecutionTime();
            try {
                // 通过 SingleThreadEventExecutor.this.run() 执行传入的任务
                // 该 run 方法是 NioEvnetLoop 的 run 方法
                SingleThreadEventExecutor.this.run();
                success = true;
            } catch (Throwable t) {
                logger.warn("Unexpected exception from an event executor: ", t);
            } finally {
                // ...
            }
        }
    });
}
```

doStartThread() 真正创建了 NIO 线程并执行 run 方法。

#### 任务是如何处理的

NioEventLoop#run 是 NioEventLoop 执行任务的地方，当线程被唤醒也就是在执行 run 方法。

NioEventLoop#run：

```java
// io/netty/channel/nio/NioEventLoop.java#run
@Override
protected void run() {
    // 这是一个死循环，不断地从任务队列中获取各种任务来执行
    for (;;) {
        try {
            // selectStrategy 有两个值，一个是 CONTINUE 一个是 SELECT。
            // hasTasks 校验 taskQueue 是否为空。
            // 1. 如果 taskQueue 不为空，执行 SelectStrategy.CONTINUE 分支，程序执行一次 selectNow()，该方法不会阻塞。
            // 2. 如果 taskQueue 为空，执行 SelectStrategy.SELECT 分支，进行 select(timeout)，这块是带超时的阻塞方法。
            // 总的说，就是按照是否有任务在排队来决定是否进行阻塞
            switch (selectStrategy.calculateStrategy(selectNowSupplier, hasTasks())) {
                case SelectStrategy.CONTINUE:
                    continue;
                case SelectStrategy.SELECT:
                    // 方法里面执行带 timeout 的 select
                    // 避免新加入任务，而线程被阻塞
                    select(wakenUp.getAndSet(false));

                    // 有任务需要被执行时，唤醒阻塞的 NIO 线程
                    if (wakenUp.get()) {
                        selector.wakeup();
                    }
                    // fall through
                default:
            }

            cancelledKeys = 0;
            needsToSelectAgain = false;
            final int ioRatio = this.ioRatio;
            // ioRatio 是处理 IO 事件用时的比例（分为处理 IO 事件和其他任务）
            if (ioRatio == 100) {
                try {
                    // 处理 IO 事件
                    processSelectedKeys();
                } finally {
                    // Ensure we always run tasks.
                    // 处理普通任务和定时任务
                    runAllTasks();
                }
            } else {
                final long ioStartTime = System.nanoTime();
                try {
                    // 处理 IO 事件
                    processSelectedKeys();
                } finally {
                    // Ensure we always run tasks.
                    // ioTime 为处理 IO 事件耗费的时间
                    final long ioTime = System.nanoTime() - ioStartTime;
                    // 计算出处理其他任务的时间
                    // 超过设定的时间后，将会停止任务的执行，会在下一次循环中再继续执行
                    runAllTasks(ioTime * (100 - ioRatio) / ioRatio);
                }
            }
        } catch (Throwable t) {
            handleLoopException(t);
        }
        // Always handle shutdown even if the loop processing threw an exception.
        try {
            if (isShuttingDown()) {
                closeAll();
                if (confirmShutdown()) {
                    return;
                }
            }
        } catch (Throwable t) {
            handleLoopException(t);
        }
    }
}
```

`NioEventLoop#run` 内部是一个死循环，根据队列中是否有任务来决定是否阻塞，它会循环处理 IO 事件的普通任务。

ioRatio 是处理 IO 事件用时的比例，根据这个参数决定处理 IO 事件和其他任务的时间。
`processSelectedKeys()` 处理 IO 事件，`runAllTasks()` 处理普通任务和定时任务。