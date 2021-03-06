# 线程池

- 目录
  - [什么是线程池](#什么是线程池)
  - [为什么用线程池](#为什么用线程池)
  - [Executor](#Executor)
    - [ThreadPoolExecutor](#ThreadPoolExecutor)
      - [ThreadPoolExecutor 构造](#ThreadPoolExecutor-构造)
      - [ThreadPoolExecutor 扩展](#ThreadPoolExecutor-扩展)
      - [ThreadPoolExecutor 更多实现](#ThreadPoolExecutor-更多实现)
    - [Executors](#Executors)
  - [任务接口](#任务接口)
    - [线程创建方式](#线程创建方式)
    - [为什么调用 start 而不是 run](#为什么调用-start-而不是-run)
    - [Callable 和 Future](#Callable-和-Future)
      - [RunnableFuture](#RunnableFuture)
    - [CompletableFuture](#CompletableFuture)
      

### 什么是线程池

简单地说，线程池就是管理线程地池子。

### 为什么用线程池

我们可以采取显式地创建线程来执行任务，但这种方式存在很多缺陷：

- 线程的创建和销毁开销较高，给轻量的任务创建新线程性价比太低。
- 线程会消耗系统资源，尤其是内存，大量空闲线程会占用许多内存，增加 GC 压力。
- 大量线程竞争 CPU 资源也会产生开销，如果已经存在足够多的活跃线程使得 CPU 处于忙碌状态，这时候如果创建更多线程反而会降低性能。
- 不同平台的线程数阈值是不同的，Java 虚拟机栈空间大小是不同的，过多的线程可能会导致 OOM 异常。

为了保证 CPU 性能的有效使用，我们往往采用线程池的方式来管理工作线程。

大多数并发应用都是围绕任务执行来构造的：任务通常是一些抽象且离散的工作单元。通过把应用程序的工作分解到多个任务中，可以简化程序的组织结构，提供一种自然边界来优化错误恢复的过程，以及提供一种自然的并行工作结构来提升并发性。

线程池是指管理一组同构工作线程的资源池，线程池是与工作队列密切相关的，工作队列中保存着所有等待执行的任务。工作线程从工作队列中获取任务、执行任务、返回线程池并等待下一个任务。线程池通过复用已有线程，避免线程频繁创建和销毁，而且采用线程池还有一个好处在于当任务到达时工作线程已经存在，可以更快的开始执行任务。

使用线程池的好处如下：

- 降低资源消耗：通过重复利用已创建的线程降低线程创建和销毁造成的消耗，减少内存消耗和降低 GC 压力。
- 提高响应速度：当任务到达时，可以不需要等待线程创建就能立即执行。
- 提高线程的可管理性：线程是稀缺资源，如果无限制的创建，不仅会消耗系统资源，还会降低系统的稳定性，使用线程池可以进行统一的分配，监控和调优。
  
### Executor

Executor 框架是一种线程池实现，在 Java 类库中任务执行的主要抽象不是 Thread，而是 Executor。Executor 只是一个接口，该框架支持多种类型任务执行策略，用 Runnable 表示任务。

```java

public interface Executor {
    void execute(Runnable command);
}
```

Executor 是基于生产者 - 消费者模式构建的，提交任务相当于生产者行为，执行任务的线程相当于消费者行为。Executor 将任务的提交和执行解构，更容易地指定执行策略。执行策略就是任务的执行行为，包括线程池中执行什么任务、允许多少个任务并发执行、按什么顺序执行、等待队列中有多少个任务等待、如何拒绝任务、在执行任务前后应该做那些动作。

ExecutorService 是 Executor 接口的扩展，它的生命周期有三种状态：运行、关闭、已终止。ExecutorService 中关键接口：

- shutdown() 执行平缓的关闭过程：不再接受新的任务，同时等待已经提交的任务全部执行完毕。
- shutdownNow() 执行粗暴的关闭过程：尝试取消运行中任务并不再启动队列中等待的任务。线程池关闭后提交的任务由拒绝策略处理。
- submit(Runnable task···) 和 execute(Runnable command) 都可以用来提交任务，区别在于前者有返回值，后者没有。

#### ThreadPoolExecutor

ThreadPoolExecutor 继承了 AbstractExecutorService 抽象类，AbstractExecutorService 实现了是 ExecutorService 接口。ThreadPoolExecutor 具备 ExecutorService 的生命周期和行为，可用于构造线程池。

##### ThreadPoolExecutor 构造

ThreadPoolExecutor构造方法：

```java

public ThreadPoolExecutor(int corePoolSize,
                          int maximumPoolSize,
                          long keepAliveTime,
                          TimeUnit unit,
                          BlockingQueue<Runnable> workQueue,
                          ThreadFactory threadFactory,
                          RejectedExecutionHandler handler) {
    if (corePoolSize < 0 ||
        maximumPoolSize <= 0 ||
        maximumPoolSize < corePoolSize ||
        keepAliveTime < 0)
        throw new IllegalArgumentException();
    if (workQueue == null || threadFactory == null || handler == null)
        throw new NullPointerException();
    this.corePoolSize = corePoolSize;
    this.maximumPoolSize = maximumPoolSize;
    this.workQueue = workQueue;
    this.keepAliveTime = unit.toNanos(keepAliveTime);
    this.threadFactory = threadFactory;
    this.handler = handler;
}
```

ThreadPoolExecutor参数:

| 参数               | 含义           |
| ----------------- | -------------- |
| corePoolSize      | 核心线程池数量   |
| maximumPoolSize   | 最大线程池大小   |
| keepAliveTime     | 线程最大空闲时间 |
| unit              | 时间单位        |
| workQueue         | 任务等待队列     |
| threadFactory     | 线程创建工厂     |
| handler           | 拒绝策略         |

线程池数量大小的设置原则是线程等待时间越长越需要更多的工作线程，线程的活跃时间越短需要的线程数量越小，大致为：线程数量 = 核心数 ×（线程运行总时间 / CPU 活跃时间）。
maximumPoolSize 是最大线程数量，只有当工作队列满了的情况下才会创建超出 corePoolSize 的线程。

keepAliveTime 是线程池中空闲线程等待工作的超时时间，当线程池中线程数量大于 corePoolSize 或设置了 allowCoreThreadTimeOut（是否允许空闲核心线程超时）时，线程会根据 keepAliveTime 进行活性检查，一旦超时便销毁线程。

workQueue 是工作队列，ThreadPoolExecutor 提供了三种基本的工作队列：无界队列、有界队列和同步移交。无界队列和有界队列都是阻塞队列，区别在于是否有界。同步移交（SynchronousQueue）并不是一个真正的队列而是一种线程之间进行移交的机制，可以通过 SynchronousQueue 来避免任务排队，可以直接将任务从生产者移交给消费者。

RejectedExecutionHandler 是拒绝策略。

- AbortPolicy：中止策略，丢弃任务并抛出 RejectedExecutionException 异常。
- DiscardPolicy：抛弃策略，丢弃任务，但不抛出异常。
- DiscardOldestPolicy：抛弃最旧策略，丢弃队列最前面的任务，然后重新尝试执行任务。
- CallerRunsPolicy：调用者执行策略，由调用线程处理该任务。

##### ThreadPoolExecutor 扩展

ThreadPoolExecutor 是可以扩展的，可以通过重写 beforeExecute(Thread t, Runnable r)、afterExecute(Runnable r, Throwable t)、terminated() 扩展行为来完成各种功能，比如执行完发送通知、记录日志、统计信息等。

````java

@Override
protected void beforeExecute(Thread t, Runnable r) {
    System.out.println("beforeExecute...");
}

@Override
protected void afterExecute(Runnable r, Throwable t) {
    System.out.println("afterExecute...");
}

@Override
protected void terminated() {
    System.out.println("terminated...");
}
````

##### ThreadPoolExecutor 更多实现

ThreadPoolExecutor 其实还要很多子类，如 ScheduledThreadPoolExecutor 类。我们可以用它处理延时任务，如下：

```java
/**
 * 延迟任务执行器
 */
private static ScheduledExecutorService delayExecutor = new ScheduledThreadPoolExecutor(CORENUMS);

/**
 * 延迟队列
 *
 * @param task      待执行任务
 * @param delayTime 延迟时间（单位秒）
 */
public static void doDelayTask(Runnable task, Long delayTime) {
    delayExecutor.scheduleWithFixedDelay(new Runnable() {
        @Override
        public void run() {
            task.run();
        }
    }, delayTime, delayTime, TimeUnit.SECONDS);
}
```

#### Executors

Executors 是 Executor 框架的静态工厂，我们可以通过 Executors 创建线程池。

- newCachedThreadPool：可缓存的线程池，如果线程池线程数量超过处理需求将回收空闲线程，如果处理需求增加创建线程，无线程数量限制。
- newFixedThreadPool：线程数量固定的线程池，可控制线程最大并发数，超出的线程会在队列中等待，如果某个线程因为异常而结束，线程池会创建一个新的线程补充。
- newSingleThreadExecutor：单线程的执行器，它只有唯一的工作线程来执行任务，保证所有任务按序执行，如果这个线程因异常而结束，执行器会创建一个新的线程来代替。
- newScheduledThreadPool：线程数量固定的线程池，以延迟或定时的方式执行任务。

并不建议使用 Executors 创建线程池:

- newFixedThreadPool 和 newSingleThreadExecutor:主要问题是堆积的请求处理队列可能会耗费非常大的内存，甚至 OOM。
- newCachedThreadPool 和 newScheduledThreadPool:主要问题是线程数最大数是 Integer.MAX_VALUE，可能会创建数量非常多的线程，甚至 OOM。

### 任务接口

#### 线程创建方式

在 JDK 1.5 之前我们创建线程只有两种方式：一是继承 Thread 类，二是实现 Runable 接口。Runnable 接口很简洁，只有一个抽象的 run() 方法，Thread 是 Runnable 的实现类。但是从本质上来说，继承 Thread 类还是为了实现 Runable 接口的 run() 方法，我们通过继承 Thread 类并重写 run() 方法创建线程和实现 Runable 接口放到线程中执行是一样的。但这种方式存在一个明显的缺点：无法获取任务执行的执行结果，如果想获得执行结果就必须引入线程安全的共享变量，增加了程序的复杂性。

Runable 和 Thread：

```java

public interface Runnable {
    public abstract void run();
}

public class Thread implements Runnable {
    @Override
    public void run() {
        if (target != null) {
            target.run();
        }
    }

    // othor methods...
}
```

Runable 接口创建线程：

```java

public class RunableDemo {
    public static void main(String[] args) {
        new Thread(new MyRunable()).start();
    }
}

class MyRunable implements Runnable {
    @Override
    public void run() {
        System.out.println("do somethings ...");
    }
}
```

继承 Thread 创建线程：

```java

public class ThreadDemo {
    public static void main(String[] args) {
        new MyThread().start();
    }
}

class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("do somethings ...");
    }
}
```
##### 为什么调用 start 而不是 run

new 一个 Thread，线程进入了新建状态。调用 start()方法，会启动一个线程并使线程进入了就绪状态，当分配到时间片后就可以开始运行了。 start() 会执行线程的相应准备工作，然后自动执行 run() 方法的内容，这是真正的多线程工作。 但是，直接执行 run() 方法，会把 run() 方法当成一个 main 线程下的普通方法去执行，并不会在某个线程中执行它，所以这并不是多线程工作。

#### Callable 和 Future

JDK 1.5 引入了 Callable 和 Future，通过它们可以在任务执行完毕之后得到任务执行结果。

Callable 和 Future：

```java

public interface Callable<V> {
    V call() throws Exception;
}

public interface Future<V> {
    boolean cancel(boolean mayInterruptIfRunning);
    boolean isCancelled();
    boolean isDone();
    V get() throws InterruptedException, ExecutionException;
    V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
}
```

Callable 是一个泛型接口，call() 函数返回的类型就是创建 Callable 时传递的 V 类型。Callable 与 Runnable 的功能大致相似，但 Callable 功能更强一些，它可以返回返回值和抛出异常。

Future 表示一个任务的生命周期，并提供相应的方法来判断释放已经完成或者取消，以及获取任务的结果和取消任务等。
- get() 方法：get 方法的行为取决于任务的状态，如果任务未完成会阻塞直到任务完成，如果任务以及完成会立即返回或抛出异常，如果任务被取消 CancellationException。get()方法还提供限时的获取，如果在指定时间内，还没获取到结果，就直接返回 null，这个就避免了一直获取不到结果使得当前线程一直阻塞的情况发生。
- cancel() 方法：取消任务，如果取消任务成功则返回 true，如果取消任务失败则返回 false。
- isCancelled() 方法：表示任务是否被取消成功，如果在任务正常完成前被取消成功，则返回 true。
- isDone 方法：表示任务是否已经完成，若任务完成，则返回 true。

Callable + Future 实现任务执行：

```java
public class CallableDemo {
    public static void main(String[] args) 
        throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        Future<String> future = executorService.submit(new MyCallable());
        System.out.println("do somethings ...");
        System.out.println("得到异步任务返回结果：" + future.get());
        System.out.println("end....");
    }
}

class MyCallable implements Callable<String> {
    @Override
    public String call() throws Exception {
        System.out.println("耗时的任务  开始...");
        Thread.sleep(5000);
        System.out.println("耗时的任务 完成...");
        return "OK";
    }
}
```

##### RunnableFuture

FutureTask 类实现了 RunnableFuture 接口，RunnableFuture 接口继承了 Runable 接口和 Tuture 接口。所以 FutureTask 既可以作为 Runnable 被线程执行，又可以作为 Future 得到 Callable 的返回值。

Callable + FutureTask 实现任务执行（基于Executor）：

```java

public class FutureTaskWithExecutor {
    public static void main(String[] args) 
        throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newCachedThreadPool();
        FutureTask<String> futureTask 
            = new FutureTask<String>(new Callable<String>() {
            @Override
            public String call() throws Exception {
                System.out.println("耗时的任务  开始...");
                Thread.sleep(5000);
                System.out.println("耗时的任务 完成...");
                return "OK";
            }
        });
        executor.submit(futureTask);
        System.out.println("do somethings ...");
        System.out.println("得到异步任务返回结果：" + futureTask.get());
        System.out.println("end....");
        executor.shutdown();
    }
}
```

Callable + FutureTask 实现任务执行（基于Thread）：

```java
public class FutureTaskWithThread {
    public static void main(String[] args) 
        throws ExecutionException, InterruptedException {
        FutureTask<String> futureTask 
            = new FutureTask<>(new Callable<String>() {
            @Override
            public String call() throws Exception {
                System.out.println("耗时的任务  开始...");
                Thread.sleep(5000);
                System.out.println("耗时的任务 完成...");
                return "OK";
            }
        });
        new Thread(futureTask).start();
        System.out.println("do somethings ...");
        System.out.println("得到异步任务返回结果：" + futureTask.get());
        System.out.println("end....");
    }
}
```

Callable 和 Future 的引入，让我们可以轻松获取线程的执行结果，但它们是存在不足的。Future 的 get() 方法是阻塞的，当我们有一组任务路径各不相同且存在依赖的任务完成就必须要得到前置任务的结果才能继续执行，这种场景中 Future 被认为是一种无效方案。

#### CompletableFuture

JDK 1.8 中引入了 CompletableFuture，CompletableFuture 类实现了 CompletionStage 和 Future 接口，CompletionStage 是一个异步任务执行阶段的抽象，一个阶段完成以后可能会触发另外一个阶段。我们可以基于 CompletableFuture 创建任务和链式处理多个任务，作为使用 CompletableFuture 将一系列操作组合的示例，我们模拟制作蛋糕的过程。

首先，我们准备各种原材料并混合成面糊：

```java
public class Batter {
    static class Eggs {
    }

    static class Milk {
    }

    static class Sugar {
    }

    static class Flour {
    }

    static <T> T prepare(T ingredient) {
        return ingredient;
    }

    static <T> CompletableFuture<T> prep(T ingredient) {
        return CompletableFuture
                .completedFuture(ingredient)
                .thenApplyAsync(Batter::prepare);
    }

    public static CompletableFuture<Batter> mix() {
        CompletableFuture<Eggs> eggs = prep(new Eggs());
        CompletableFuture<Milk> milk = prep(new Milk());
        CompletableFuture<Sugar> sugar = prep(new Sugar());
        CompletableFuture<Flour> flour = prep(new Flour());
        CompletableFuture
                .allOf(eggs, milk, sugar, flour)
                .join();
        return CompletableFuture.completedFuture(new Batter());
    }
}
```

每种原料都需要一些时间来准备。allOf()  等待所有的配料都准备好并混合成面糊。接下来我们把面糊放入三个平底锅中烘烤。产品作为 CompletableFutures 流返回：

```java

public class Baked {
    static class Pan {
    }

    static Pan pan(Batter b) {
        return new Pan();
    }

    static Baked heat(Pan p) {
        return new Baked();
    }

    static CompletableFuture<Baked> bake(CompletableFuture<Batter> cfb) {
        return cfb
                .thenApplyAsync(Baked::pan)
                .thenApplyAsync(Baked::heat);
    }

    public static Stream<CompletableFuture<Baked>> batch() {
        CompletableFuture<Batter> batter = Batter.mix();
        return Stream.of(
                bake(batter),
                bake(batter),
                bake(batter)
        );
    }
}
```

最后，我们对烘烤好的蛋糕进行糖化：

```java
final class Frosting {
    private Frosting() {
    }

    static CompletableFuture<Frosting> make() {
        return CompletableFuture
                .completedFuture(new Frosting());
    }
}

public class FrostedCake {
    public FrostedCake(Baked baked, Frosting frosting) {
    }

    @Override
    public String toString() {
        return "FrostedCake";
    }

    public static void main(String[] args) {
        Baked.batch().forEach(
                baked -> baked
                        .thenCombineAsync(Frosting.make(),
                                (cake, frosting) ->
                                        new FrostedCake(cake, frosting))
                        .thenAcceptAsync(System.out::println)
                        .join());
    }
}
```

除了例子中的用法，CompletableFuture 还有很多 API，为我们提供了丰富的异步计算调用方式和链式任务执行方式。