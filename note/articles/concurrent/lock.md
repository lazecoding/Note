### 锁

- 目录
  - [互斥同步](#互斥同步)
    - [synchronized](#synchronized)
      - [应用](#应用)
      - [锁优化](#锁优化)
    - [Lock](#Lock)
      - [ReentrantLock](#ReentrantLock)
      - [ReentrantReadWriteLock](#ReentrantReadWriteLock)
    - [synchronized 和 Lock 对比](#synchronized-和-Lock-对比)
  - [非互斥同步](#非互斥同步)
    - [CAS](#CAS)
      - [Atomic 原子类](#Atomic-原子类)
      - [CAS 问题](#CAS-问题)
  - [源码分析](#源码分析)
    - [ReentrantLock 源码分析](#ReentrantLock-源码分析)
    - [ReentrantReadWriteLock 源码分析](#ReentrantReadWriteLock-源码分析)

    
### 互斥同步

Java 提供了两种锁机制来控制多个线程对共享资源的互斥访问，第一个是 JVM 实现的 synchronized，而另一个是 JDK 实现的 Lock。

#### synchronized

synchronized 关键字用于修饰代码块和方法，被 synchronized 关键字修饰的代码块被称为同步代码块，被 synchronized 关键字修饰的方法是横跨整个方法体的同步代码块，以该方法所在对象为锁，如果是静态的 synchronized 方法则以 Class 对象为锁。线程进入同步代码块之前会自动获取锁，退出代码块会自动释放锁，Java 的内置锁是可重入的，每个锁关联着一个计数器和一个持有者线程。当计数器为 0 时，这个锁被认为未被任何线程持有，当一个线程请求一个未被持有的锁，JVM 将记下锁的持有者线程并将计数器置为1，如果同一个线程再次获取该锁，计数器递增，当线程退出同步代码块计数器将相应递减，当计数器为0表示这个锁被释放。

synchronized 相当于一种互斥体，因此由内置锁保护的同步代码块会以原子方式执行，保证多个线程的串行访问。此外, synchronized 还起到了同步的作用。我们不仅希望某个线程使用对象状态时其他线程无法修改，而且希望当一个线程修改了对象状态后其他线程能够看到状态变化，又称内存可见性。synchronized 可以用于确保某个线程以一种可预测的方式查看另一个线程的执行结果。当一个线程执行由锁保护的同步代码块时，可以看到前一个线程在同步代码块中的操作结果，所以为了保证所有线程都能看到共享变量的最新值，所有执行读操作或写操作的线程都必须在同一个锁上同步。

##### 应用

- 同步一个代码块

```java
public void func1() {
    synchronized (this) {
        // ...
    }
}
```

它只作用于同一个对象实例，如果调用两个对象上的同步代码块，就不会进行同步。

对于以下代码，使用 ExecutorService 执行了两个线程，由于调用的是同一个对象实例的同步代码块，因此这两个线程会进行同步，当一个线程进入同步语句块时，另一个线程就必须等待。

- 同步一个方法

```java
public synchronized void func2() {
    // ...
}
```

它和同步代码块一样，作用于同一个对象实例。

- 同步一个类

```java
public void func3() {
    synchronized (SyncDemo.class) {
        // ...
    }
}
```

作用于整个类，也就是说两个线程调用同一个类的不同对象上的这种同步语句，也会进行同步。

- 同步一个静态方法

```java
public synchronized static void fun3() {
    // ...
}
```

它和同步一个类一样，作用于类。

##### 锁优化

这里的锁优化主要是指 JVM 对 synchronized 的优化。

- 自旋锁

互斥同步进入阻塞状态的开销都很大，应该尽量避免。在许多应用中，共享数据的锁定状态只会持续很短的一段时间。自旋锁的思想是让一个线程在请求一个共享数据的锁时执行忙循环（自旋）一段时间，如果在这段时间内能获得锁，就可以避免进入阻塞状态。

自旋锁虽然能避免进入阻塞状态从而减少开销，但是它需要进行忙循环操作占用 CPU 时间，它只适用于共享数据的锁定状态很短的场景。

在 JDK 1.6 中引入了自适应的自旋锁。自适应意味着自旋的次数不再固定了，而是由前一次在同一个锁上的自旋次数及锁的拥有者的状态来决定。

- 锁消除

锁消除是指对于被检测出不可能存在竞争的共享数据的锁进行消除。

锁消除主要是通过逃逸分析来支持，如果堆上的共享数据不可能逃逸出去被其它线程访问到，那么就可以把它们当成私有数据对待，也就可以将它们的锁进行消除。

对于一些看起来没有加锁的代码，其实隐式的加了很多锁。例如下面的字符串拼接代码就隐式加了锁：

```java
public static String concatString(String s1, String s2, String s3) {
    return s1 + s2 + s3;
}
```

String 是一个不可变的类，编译器会对 String 的拼接自动优化。在 JDK 1.5 之前，会转化为 StringBuffer 对象的连续 append() 操作：

```java
public static String concatString(String s1, String s2, String s3) {
    StringBuffer sb = new StringBuffer();
    sb.append(s1);
    sb.append(s2);
    sb.append(s3);
    return sb.toString();
}
```

每个 append() 方法中都有一个同步块。虚拟机观察变量 sb，很快就会发现它的动态作用域被限制在 concatString() 方法内部。也就是说，sb 的所有引用永远不会逃逸到 concatString() 方法之外，其他线程无法访问到它，因此可以进行消除。

- 锁粗化

如果一系列的连续操作都对同一个对象反复加锁和解锁，频繁的加锁操作就会导致性能损耗。

上一节的示例代码中连续的 append() 方法就属于这类情况。如果虚拟机探测到由这样的一串零碎的操作都对同一个对象加锁，将会把加锁的范围扩展（粗化）到整个操作序列的外部。对于上一节的示例代码就是扩展到第一个 append() 操作之前直至最后一个 append() 操作之后，这样只需要加锁一次就可以了。

- 锁升级

JDK 1.6 引入了偏向锁和轻量级锁，从而让锁拥有了 4 个状态：无锁状态（unlocked）、偏向锁状态（biasble）、轻量级锁状态（lightweight locked）和重量级锁状态（inflated）。

不同锁类型的对象头的 Mark Word 如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/concurrent/锁升级.png" width="600px">
</div>

锁可以升级但不能降级，这种锁升级却不能降级的策略目的是为了提高获得锁和释放锁的效率。

- 偏向锁

经过 HotSpot 的作者大量的研究发现，大多数时候是不存在锁竞争的，常常是一个线程多次获得同一个锁，因此如果每次都要竞争锁会增大很多没有必要付出的代价，为了降低获取锁的代价，才引入的偏向锁。

偏向锁的思想是偏向于让第一个获取锁对象的线程，这个线程在之后获取该锁就不再需要进行同步操作，甚至连 CAS 操作也不再需要。

当锁对象第一次被线程获得的时候，虚拟机会把对象头中的锁标志位设置为 01、把偏向模式设置为 1，同时使用 CAS 操作将线程 ID 记录到 Mark Word 中，如果 CAS 操作成功，这个线程以后每次进入这个锁相关的同步块就不需要再进行任何同步操作。当有另外一个线程去尝试获取这个锁对象时，偏向状态就宣告结束，此时撤销偏向（Revoke Bias）后恢复到未锁定状态或者轻量级锁状态。

偏向锁可以提升带有同步但鲜有竞争的程序性能，但如果程序大大多数的锁总是被多个不同的线程访问，那么偏向模式便是多余的，需要使用 `-XX:-UseBiasedLocking = false` 参数来禁止偏向锁。

- 轻量级锁

轻量级锁考虑的是竞争锁对象的线程不多，而且线程持有锁的时间也不长的情景。因为阻塞线程需要 CPU 从用户态转到内核态，代价较大，如果刚刚阻塞不久这个锁就被释放了，那这个代价就有点得不偿失了，因此这个时候就干脆不阻塞这个线程，让它自旋这等待锁释放。

轻量级锁是相对于传统的重量级锁而言，它使用 CAS 操作来避免重量级锁使用互斥量的开销。对于绝大部分的锁，在整个同步周期内都是不存在竞争的，因此也就不需要都使用互斥量进行同步，可以先采用 CAS 操作进行同步，如果 CAS 失败了再改用互斥量进行同步。

当尝试获取一个锁对象时，如果对象头的锁标志位为 01，说明锁对象的锁未锁定（unlocked）状态。此时虚拟机在当前线程的虚拟机栈中创建 Lock Record，然后使用 CAS 操作将对象的 Mark Word 更新为 Lock Record 指针。如果 CAS 操作成功了，那么线程就获取了该对象上的锁，并且对象的 Mark Word 的锁标记变为 00，表示该对象处于轻量级锁状态。如果 CAS 操作失败了，虚拟机首先会检查对象的 Mark Word 是否指向当前线程的虚拟机栈，如果是的话说明当前线程已经拥有了这个锁对象，那就可以直接进入同步块继续执行，否则说明这个锁对象已经被其他线程线程抢占了。如果有两条以上的线程争用同一个锁，那轻量级锁就不再有效，要膨胀为重量级锁。

轻量级锁能提升程序性能是 "绝大多数的锁，在整个同步周期内都是不存在禁止的"。如果没有竞争，轻量级锁可以利用 CAS 操作成功避免使用互斥量的开销；但如果确实存在锁竞争，就除了互斥量本身的开销，还额外增加了 CAS 的开销。因此，如果是有竞争的情况下，轻量级锁反而比重量级锁更慢。

#### Lock

Lock 是一组抽象接口，与内置锁机制不同，Lock 提供了无条件的、可轮询的、可定时的、可中断的锁获取方式，所有的加锁和解锁都是显式的。

```java
public interface Lock {
    void lock();

    void lockInterruptibly() throws InterruptedException;

    boolean tryLock();

    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    void unlock();

    Condition newCondition();
}
```

##### ReentrantLock

ReentrantLock 实现了 Lock 接口，并提供了与 synchronized 相同的互斥性和内存可见性，但比 synchronized 具有更灵活的获取模式。

ReentrantLock 的使用比内置锁更复杂一些：
必须在 try-catch-finally 块外加锁，在 finally 块中解锁。在 finally 块中解锁很容易理解，保证锁最终一定会被解除。那么为什么必须在 try-catch-finally 块外加锁而不是在 try 块的第一行加锁呢？因为如果在 try 块第一行加锁，如果加锁异常了，执行 finally 块中解锁操作会抛出 IllegalMonitorStateException 异常，这对于我们并不是有效的异常。而在 finally 块之外加锁异常了程序会直接抛出异常，这是可预见的异常处理。

```java
Lock lock = new ReentrantLock();
lock.lock();
try {
    // do somethings
} catch (Exception e) {
    e.printStackTrace();
} finally {
    lock.unlock();
}
```

在内置锁中，死锁是一个严重的问题，重启是恢复程序的唯一方法，防止死锁的也只能避免不一致的加锁顺序。从 Lock 接口中可以看到 tryLock 方法，这个一个可轮询、可定时的获取锁模式，为避免死锁提供了可能。tryLock 会尝试获取锁，并且可以设置超时时间，一旦获取锁失败会返回 false。

在 ReentrantLock 构造函数提供了非公平锁和公平锁两种构造方法。公平锁按照请求顺序获取锁，非公平锁在请求锁时先尝试获取锁，如果获取失败才加入 CLH 同步队列。从性能角度来说，非公平锁性能更高，一个关键的原因是：恢复一个被挂起的线程与该线程真正开始运行之间存在严重的延时。

ReentrantLock 也是可重入的，它有一个与锁相关的计数器，如果拥有锁的线程再次得到锁，那么计数器递增，锁需要被释放两次才能获得真正释放锁。

对于同步程序，我们希望更少的资源用于锁的管理调度上，让更多的资源服务应用程序。在 JDK 1.5 中，ReentrantLock 带来了比内置锁更好的竞争性能，随着机器线程数量的增加，内置锁性能急剧下降，导致 ReentrantLock 吞吐量最高能达到内置锁吞吐量的 4 倍左右。在 JDK 1.6 中，内置锁改用了与 ReentrantLock 类似的算法，让二者的吞吐量非常接近

而且，ReentrantLock 等待可中断。doAcquireNanos 方法是个循环，不断尝试获取锁，如果超时了就会抛出 InterruptedException 异常，实现中断。

```java
public boolean tryLock(long timeout, TimeUnit unit)
        throws InterruptedException {
    return sync.tryAcquireNanos(1, unit.toNanos(timeout));
}

private boolean doAcquireNanos(int arg, long nanosTimeout)
        throws InterruptedException {
    if (nanosTimeout <= 0L)
        return false;
    final long deadline = System.nanoTime() + nanosTimeout;
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return true;
            }
            nanosTimeout = deadline - System.nanoTime();
            if (nanosTimeout <= 0L)
                return false;
            if (shouldParkAfterFailedAcquire(p, node) &&
                nanosTimeout > spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);
            if (Thread.interrupted())
                throw new InterruptedException();
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}
```

##### ReentrantReadWriteLock

ReentrantLock 实现了一种标准的互斥锁，但对于维护数据完整性来说，互斥是一种过于强硬的加锁规则，降低了并发能力。对于数据来说，读操作不会改变数据，多个线程同时读取同一块数据不会带来线程安全问题，自然而然地想到将读锁和写锁分离。ReadWriteLock 是读写锁的接口，它暴露了两个对象，一个用于读操作，一个用于写操作。

```java
public interface ReadWriteLock {
    Lock readLock();

    Lock writeLock();
}
```

ReentrantReadWriteLock 是 ReadWriteLock 的实现类，它的两个内部类 WriteLock 和 ReadLock 都是实现了 Lock 接口。在读写锁实现的加锁策略中，允许多个读操作同时进行，但每次只能允许一个写操作。

#### synchronized 和 Lock 对比

- 锁的实现

synchronized 是 JVM 实现的，而 ReentrantLock 是 JDK 实现的。

- 性能

新版本 Java 对 synchronized 进行了很多优化，例如自旋锁等，synchronized 与 ReentrantLock 大致相同。

- 等待可中断

当持有锁的线程长期不释放锁的时候，正在等待的线程可以选择放弃等待，改为处理其他事情。

ReentrantLock 可中断，而 synchronized 不行。

- 公平锁

公平锁是指多个线程在等待同一个锁时，必须按照申请锁的时间顺序来依次获得锁。

synchronized 中的锁是非公平的，ReentrantLock 默认情况下也是非公平的，但是也可以是公平的。

- 锁绑定多个条件

一个 ReentrantLock 可以同时绑定多个 Condition 对象。

### 非互斥同步

互斥同步最主要的问题就是线程阻塞和唤醒所带来的性能问题，因此这种同步也称为阻塞同步。

互斥同步属于一种悲观的并发策略，总是认为只要不去做正确的同步措施，那就肯定会出现问题。无论共享数据是否真的会出现竞争，它都要进行加锁（这里讨论的是概念模型，实际上虚拟机会优化掉很大一部分不必要的加锁）、用户态核心态转换、维护锁计数器和检查是否有被阻塞的线程需要唤醒等操作。

随着硬件指令集的发展，我们可以使用基于冲突检测的乐观并发策略：先进行操作，如果没有其它线程争用共享数据，那操作就成功了，否则采取补偿措施（不断地重试，直到成功为止）。这种乐观的并发策略的许多实现都不需要将线程阻塞，因此这种同步操作称为非阻塞同步。

#### CAS

乐观锁需要操作和冲突检测这两个步骤具备原子性，这里就不能再使用互斥同步来保证了，只能靠硬件来完成。硬件支持的原子性操作最典型的是：比较并交换（Compare-and-Swap，CAS）。CAS 指令需要有 3 个操作数，分别是内存地址 V、旧的预期值 A 和新值 B。当执行操作时，只有当 V 的值等于 A，才将 V 的值更新为 B。

##### Atomic 原子类

AtomicAtomicInteger 类是一组原子类，提供原子的运算操作。

```java
// AtomicInteger.java#incrementAndGet
public final int incrementAndGet() {
    return unsafe.getAndAddInt(this, valueOffset, 1) + 1;
}
```

实现 CAS 操作都是基于 Unsafe 类的 CAS 操作。

可以看到，Unsafe 类中 getAndAddInt 方法循环调用 compareAndSwapInt 判断 CAS 操作是否成功，如果不成功继续尝试。compareAndSwapInt 方法是一个 native 方法，这是本地函数库的方法，C 实现。

```java
// AtomicInteger.java$unsafe
private static final Unsafe unsafe = Unsafe.getUnsafe();

// Unsafe.java#getAndAddInt
public final int getAndAddInt(Object var1, long var2, int var4) {
    int var5;
    do {
        var5 = this.getIntVolatile(var1, var2);
    } while(!this.compareAndSwapInt(var1, var2, var5, var5 + var4));

    return var5;
}
```

##### CAS 问题

CAS 虽然很高效的解决了原子操作问题，但是 CAS 仍然存在三大问题：

- 循环时间长开销很大。
- 只能保证一个共享变量的原子操作。
- ABA 问题。

循环时间长开销很大：我们可以看到 getAndAddInt 方法执行时，如果 CAS 失败，会一直进行尝试。如果 CAS 长时间一直不成功，可能会给 CPU 带来很大的开销。

只能保证一个共享变量的原子操作：当对一个共享变量执行操作时，我们可以使用循环 CAS 的方式来保证原子操作，但是对多个共享变量操作时，循环 CAS 就无法保证操作的原子性，这个时候就可以用锁来保证原子性。

ABA 问题：如果一个变量初次读取的时候是 A 值，它的值被改成了 B，后来又被改回为 A，那 CAS 操作就会误认为它从来没有被改变过。J.U.C 包提供了一个带有标记的原子引用类 AtomicStampedReference 来解决这个问题，它可以通过控制变量值的版本来保证 CAS 的正确性。大部分情况（如：值类型）下 ABA 问题不会影响程序并发的正确性，如果需要解决 ABA 问题，改用传统的互斥同步可能会比原子类更高效。

### 源码分析

#### ReentrantLock 源码分析

类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/concurrent/ReentrantLock类图.png" width="600px">
</div>

ReentrantLock 公平锁和非公平锁的实现分别是内部类 FairSync 和 NonfairSync。它们实现了 Lock 接口并继承了 AbstractQueuedSynchronizer 抽象类。Lock 接口规范了 ReentrantLock 的行为，AbstractQueuedSynchronizer 为 Lock 接口的行为提供了实现。

分析 ReentrantLock 的关键在于 AbstractQueuedSynchronizer，简称 AQS，翻译一下是抽象队列同步器。AQS 内部维护了一个 CLH 同步队列和一个用 volatile 关键字修饰的同步状态标识state。

- CLH 同步队列是一个 FIFO （先进先出）双向队列，AQS 依赖它来完成同步状态的管理。在 CLH 同步队列中，一个节点表示一个线程，它保存着线程的引用、状态、前驱节点、后继节点。当前线程如果获取同步状态失败时，AQS 则会将当前线程构造成一个节点（Node）并将其加入到 CLH 同步队列，同时会阻塞当前线程，当同步状态释放时，会把首节点唤醒，使其再次尝试获取同步状态。
- state 表示 AQS 是否被某个工作线程占用，锁的获取是通过 CAS 算法来保证线程安全的。如果当前线程修改 state 状态成功表示当前线程获取锁成功，并且将所有者线程设置为当前线程，如果修改 state 状态失败了表示当前线程获取锁失败，会将当前线程作为一个节点加入到 CLH 同步队列。

ReentrantLock 提供了公平锁和非公平锁两种实现，它们的区别在于：公平锁会直接加入 CLH 同步队列，如果同步状态队列为空会立即执行；非公平锁会先尝试索取锁，如果获取失败再加入 CLH 同步队列等待锁。

```java
// fair locks
final void lock() {
    acquire(1);
}

// non-fair locks
final void lock() {
    if (compareAndSetState(0, 1))
        setExclusiveOwnerThread(Thread.currentThread());
    else
        acquire(1);
}
```

ReentrantLock 提供 acquire 方法封装线程作为节点加入 CLH 同步队列的行为。对于非公平锁，首先它会调用子类的 tryAcquire 方法尝试获取同步资源，如果获取同步资源成功则继续执行，如果获取失败尝试将当前线程封装成节点加入到 CLH 同步队列，然后执行 acquireQueued 方法，用于自旋获取同步资源。对于公平锁而言，和非公平锁的区别在于 tryAcquire 方法只有在队列为空的时候才会尝试获取同步资源。acquireQueued 方法中的 parkAndCheckInterrupt 方法用于检验当前线程是否被中断并且清除中断标准，所以当获取锁失败而且当前线程状态标识是中断状态才会调用 selfInterrupt 方法中断线程。

```java
// AbstractQueuedSynchronizer
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}

// FairSync
protected final boolean tryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        if (!hasQueuedPredecessors() &&
            compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0)
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}


// NonFairSync
final boolean nonfairTryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        if (compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0) // overflow
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}
```

#### ReentrantReadWriteLock 源码分析

类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/concurrent/ReentrantReadWriteLock类图.png" width="850px">
</div>

ReentrantReadWriteLock 将读锁和写锁分离，也提供了公平锁和非公平锁两种实现，实现了 ReadWriteLock 接口，它的两个内部类 WriteLock 和 ReadLock 都是实现了 Lock 接口。

内部类 Sync 中的 tryAcquire 方法是写锁获取的策略方法：如果写锁数量不为 0 或者所有者不是当前线程，获取失败；如果计数器饱和，获取失败；如果试图获取写锁成功就阻塞其他线程并且将当前线程设置成所有者线程。

```java
protected final boolean tryAcquire(int acquires) {
    /*
     * Walkthrough:
     * 1. If read count nonzero or write count nonzero
     *    and owner is a different thread, fail.
     * 2. If count would saturate, fail. (This can only
     *    happen if count is already nonzero.)
     * 3. Otherwise, this thread is eligible for lock if
     *    it is either a reentrant acquire or
     *    queue policy allows it. If so, update state
     *    and set owner.
     */
    Thread current = Thread.currentThread();
    int c = getState();
    int w = exclusiveCount(c);
    if (c != 0) {
        // (Note: if c != 0 and w == 0 then shared count != 0)
        if (w == 0 || current != getExclusiveOwnerThread())
            return false;
        if (w + exclusiveCount(acquires) > MAX_COUNT)
            throw new Error("Maximum lock count exceeded");
        // Reentrant acquire
        setState(c + acquires);
        return true;
    }
    if (writerShouldBlock() ||
        !compareAndSetState(c, c + acquires))
        return false;
    setExclusiveOwnerThread(current);
    return true;
}
```

tryAcquireShared 是读锁获取的策略方法：和获取写锁不同，获取读锁通过判断 state 后 16 位，如果有写锁才尝试加入 CLH 同步队列，如果无锁或者只有读锁则获取读锁，并将当前线程设置成所有者线程。

```java
protected final int tryAcquireShared(int unused) {
    /*
     * Walkthrough:
     * 1. If write lock held by another thread, fail.
     * 2. Otherwise, this thread is eligible for
     *    lock wrt state, so ask if it should block
     *    because of queue policy. If not, try
     *    to grant by CASing state and updating count.
     *    Note that step does not check for reentrant
     *    acquires, which is postponed to full version
     *    to avoid having to check hold count in
     *    the more typical non-reentrant case.
     * 3. If step 2 fails either because thread
     *    apparently not eligible or CAS fails or count
     *    saturated, chain to version with full retry loop.
     */
    Thread current = Thread.currentThread();
    int c = getState();
    if (exclusiveCount(c) != 0 &&
        getExclusiveOwnerThread() != current)
        return -1;
    int r = sharedCount(c);
    if (!readerShouldBlock() &&
        r < MAX_COUNT &&
        compareAndSetState(c, c + SHARED_UNIT)) {
        if (r == 0) {
            firstReader = current;
            firstReaderHoldCount = 1;
        } else if (firstReader == current) {
            firstReaderHoldCount++;
        } else {
            HoldCounter rh = cachedHoldCounter;
            if (rh == null || rh.tid != getThreadId(current))
                cachedHoldCounter = rh = readHolds.get();
            else if (rh.count == 0)
                readHolds.set(rh);
            rh.count++;
        }
        return 1;
    }
    return fullTryAcquireShared(current);
}
```