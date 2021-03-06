# 并发编程基础

- 目录
  - [进程和线程](#进程和线程)
  - [进程和线程](#进程和线程)
  - [为什么要使用多线程](#为什么要使用多线程)
  - [使用多线程带来的问题](#使用多线程带来的问题)
  - [线程的生命周期](#线程的生命周期)
  - [sleep 和 wait 的区别](#sleep-和-wait-的区别)
  - [上下文切换](#上下文切换)
  - [死锁](#死锁)
    - [什么是死锁](#什么是死锁)
    - [如何避免死锁](#如何避免死锁)

所谓并发编程是指在一台处理器上 "同时" 处理多个任务。并发是在同一实体上的多个事件。多个事件在同一时间间隔发生。

本文在讲解并发编程之前先补充一些基础知识。

### 进程和线程

进程是程序的一次执行过程，是系统运行程序的基本单位，系统运行一个程序即是一个进程从创建，运行到消亡的过程。
在 Java 中，当我们启动 main 函数时其实就是启动了一个 JVM 的进程，而 main 函数所在的线程就是这个进程中的一个线程，也称主线程。

线程作为资源调度的基本单位，是程序的执行单元，是程序使用 CPU 的最基本单位。

### 并行和并发

并行：

- 并行性是指同一时刻内发生两个或多个事件。
- 并行是在不同实体上的多个事件

并发：

- 并发性是指同一时间间隔内发生两个或多个事件。
- 并发是在同一实体上的多个事件

### 为什么要使用多线程

从计算机底层来说：线程可以比作是轻量级的进程，是程序执行的最小单位,线程间的切换和调度的成本远远小于进程。另外，多核 CPU 时代意味着多个线程可以同时运行，这减少了线程上下文切换的开销。

从应用程序角度：现在的系统动不动就要求百万级甚至千万级的并发量，而多线程并发编程正是开发高并发系统的基础，利用好多线程机制可以大大提高系统整体的并发能力以及性能。

更深层次的原因：CPU 运算速度很快，内存 IO 和磁盘 IO 也存储几个数量级的差距，而且现代 CPU 采用的时间片技术和各种调度算法，可以保证每个程序都能得到执行，时延非常短，这即是目的，也是可行性分析。

### 使用多线程带来的问题 

并发编程的目的就是为了能提高 CPU 的利用率，但是并发编程并不总是能提高程序运行速度的，而且并发编程可能会遇到很多问题，比如：内存泄漏、死锁、线程不安全等等。

### 线程的生命周期

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/concurrent/线程状态.png" width="600px">
</div>

由上图可以看出：线程创建之后它将处于 NEW（新建） 状态，调用 start() 方法后开始运行，线程这时候处于 READY（可运行） 状态。可运行状态的线程获得了 CPU 时间片（timeslice）后就处于 RUNNING（运行） 状态。

当线程执行 wait() 方法之后，线程进入 WAITING（等待） 状态。进入等待状态的线程需要依靠其他线程的通知才能够返回到运行状态，而 TIME_WAITING(超时等待) 状态相当于在等待状态的基础上增加了超时限制，比如通过 sleep（long millis）方法或 wait（long millis）方法可以将 Java 线程置于 TIMED WAITING 状态。当超时时间到达后 Java 线程将会返回到 RUNNABLE 状态。当线程调用同步方法时，在没有获取到锁的情况下，线程将会进入到 BLOCKED（阻塞） 状态。线程在执行 Runnable 的run()方法之后将会进入到 TERMINATED（终止） 状态。

#### sleep 和 wait 的区别

sleep()：

- 属于 Thread 类，表示让一个线程进入睡眠状态，等待一定的时间之后，自动醒来进入到可运行状态，不会马上进入运行状态。
- sleep() 没有释放锁。
- sleep() 必须捕获异常。
- sleep() 可以在任何地方使用。

wait()：

- 属于 Object 类，一旦一个对象调用了wait方法，必须要采用notify()和notifyAll()方法唤醒该进程。
- wait() 释放了锁。
- wait() 不需要捕获异常。
- wait()、notify() 和 notifyAll() 只能在同步控制方法或者同步控制块里面使用。

总的说：sleep() 是线程内行为；而 wait()、notify() 和 notifyAll() 是用于线程间通信的方法，所以是 Object 类的方法。

### 上下文切换

多线程编程中一般线程的个数都大于 CPU 核心的个数，而一个 CPU 核心在任意时刻只能被一个线程使用，为了让这些线程都能得到有效执行，CPU 采取的策略是为每个线程分配时间片并轮转的形式。当一个线程的时间片用完的时候就会重新处于就绪状态让给其他线程使用，这个过程就属于一次上下文切换。

概括来说就是：当前任务在执行完 CPU 时间片切换到另一个任务之前会先保存自己的状态，以便下次再切换回这个任务时，可以再加载这个任务的状态。任务从保存到再加载的过程就是一次上下文切换。

上下文切换通常是计算密集型的。也就是说，它需要相当可观的处理器时间，在每秒几十上百次的切换中，每次切换都需要纳秒量级的时间。所以，上下文切换对系统来说意味着消耗大量的 CPU 时间，事实上，可能是操作系统中时间消耗最大的操作。

Linux 相比与其他操作系统（包括其他类 Unix 系统）有很多的优点，其中有一项就是，其上下文切换和模式切换的时间消耗非常少。

### 死锁

#### 什么是死锁

线程死锁描述的是这样一种情况：多个线程同时被阻塞，它们中的一个或者全部都在等待某个资源被释放。由于线程被无限期地阻塞，因此程序不可能正常终止。

如下图所示，线程 A 持有资源 2，线程 B 持有资源 1，他们同时都想申请对方的资源，所以这两个线程就会互相等待而进入死锁状态。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/concurrent/死锁.png" width="600px">
</div>

产生死锁的 4 个条件必要条件：

- 互斥条件：该资源任意一个时刻只由一个线程占用。
- 请求与保持条件：一个进程因请求资源而阻塞时，对已获得的资源保持不放。
- 不剥夺条件:线程已获得的资源在未使用完之前不能被其他线程强行剥夺，只有自己使用完毕后才释放资源。
- 循环等待条件:若干进程之间形成一种头尾相接的循环等待资源关系。

#### 如何避免死锁

对于产生死锁的四个必要条件，为了避免死锁，我们只要破坏产生死锁的四个条件中的其中一个就可以了。

- 破坏互斥条件 ：这个条件我们没有办法破坏，因为我们用锁本来就是想让他们互斥的（临界资源需要互斥访问）。
- 破坏请求与保持条件 ：一次性申请所有的资源。
- 破坏不剥夺条件 ：占用部分资源的线程进一步申请其他资源时，如果申请不到，可以主动释放它占有的资源。
- 破坏循环等待条件 ：靠按序申请资源来预防。按某一顺序申请资源，释放资源则反序释放。破坏循环等待条件。

