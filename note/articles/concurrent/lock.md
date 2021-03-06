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
    
### 互斥同步

Java 提供了两种锁机制来控制多个线程对共享资源的互斥访问，第一个是 JVM 实现的 synchronized，而另一个是 JDK 实现的 Lock。

#### synchronized

synchronized 关键字用于修饰代码块和方法，被 synchronized 关键字修饰的代码块被称为同步代码块，被 synchronized 关键字修饰的方法是横跨整个方法体的同步代码块，以该方法所在对象为锁，如果是静态的 synchronized 方法则以 Class 对象为锁。
线程进入同步代码块之前会自动获取锁，退出代码块会自动释放锁，Java 的内置锁是可重入的，每个锁关联着一个计数器和一个持有者线程。当计数器为 0 时，这个锁被认为未被任何线程持有，当一个线程请求一个未被持有的锁，JVM 将记下锁的持有者线程并将计数器置为1，如果同一个线程再次获取该锁，计数器递增，当线程退出同步代码块计数器将相应递减，当计数器为0表示这个锁被释放。

synchronized 相当于一种互斥体，因此由内置锁保护的同步代码块会以原子方式执行，保证多个线程的串行访问。此外, synchronized 还起到了同步的作用。我们不仅希望某个线程使用对象状态时其他线程无法修改，而且希望当一个线程修改了对象状态后其他线程能够看到状态变化，又称内存可见性。
synchronized 可以用于确保某个线程以一种可预测的方式查看另一个线程的执行结果。当一个线程执行由锁保护的同步代码块时，可以看到前一个线程在同步代码块中的操作结果，所以为了保证所有线程都能看到共享变量的最新值，所有执行读操作或写操作的线程都必须在同一个锁上同步。

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

未完待续 ...

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
必须在 try-catch-finally 块外加锁，在 finally 块中解锁。在 finally 块中解锁很容易理解，保证锁最终一定会被解除。
那么为什么必须在 try-catch-finally 块外加锁而不是在 try 块的第一行加锁呢？因为如果在 try 块第一行加锁，如果加锁异常了，执行 finally 块中解锁操作会抛出 IllegalMonitorStateException 异常，这对于我们并不是有效的异常。
而在 finally 块之外加锁异常了程序会直接抛出异常，这是可预见的异常处理。

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

ABA 问题：如果一个变量初次读取的时候是 A 值，它的值被改成了 B，后来又被改回为 A，那 CAS 操作就会误认为它从来没有被改变过。
J.U.C 包提供了一个带有标记的原子引用类 AtomicStampedReference 来解决这个问题，它可以通过控制变量值的版本来保证 CAS 的正确性。
大部分情况（如：值类型）下 ABA 问题不会影响程序并发的正确性，如果需要解决 ABA 问题，改用传统的互斥同步可能会比原子类更高效。

