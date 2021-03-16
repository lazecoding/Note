# AQS

- 目录
  - [原理](#原理)
  - [共享模式](#共享模式)
    - [独占式](#独占式)
    - [共享式](#共享式)
  - [应用](#应用)

AQS 的全称为（AbstractQueuedSynchronizer），这个类在 java.util.concurrent.locks 包下。

AQS 是一个用来构建锁和同步器的框架，使用 AQS 能简单且高效地构造出应用广泛的大量的同步器，比如 ReentrantLock、ReentrantReadWriteLock、SynchronousQueue 等皆是基于 AQS 的。

### 原理


AQS 核心思想是，如果被请求的共享资源空闲，则将当前请求资源的线程设置为有效的工作线程，并且将共享资源设置为锁定状态。如果被请求的共享资源被占用，那么就需要一套线程阻塞等待以及被唤醒时锁分配的机制，这个机制 AQS 是用 CLH 队列锁实现的，即将暂时获取不到锁的线程加入到队列中。

> CLH(Craig,Landin,and Hagersten)队列是一个虚拟的双向队列（虚拟的双向队列即不存在队列实例，仅存在结点之间的关联关系）。AQS 是将每条请求共享资源的线程封装成一个 CLH 锁队列的一个结点（Node）来实现锁的分配。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/concurrent/AQS原理.png" width="600px">
</div>

AQS 使用一个被 volatile 关键字修修饰的 int 成员变量 state 来表示同步状态，通过内置的 FIFO 队列来完成获取资源线程的排队工作。

```java
// 共享变量，使用 volatile 关键字修饰保证线程可见性
private volatile int state;
```

AQS 使用 CAS 对该同步状态进行原子操作实现对其值的修改，即：通过 protected 类型的 getState、setState、compareAndSetState 进行操作。

```java
// 返回同步状态的当前值
protected final int getState() {
    return state;
}
// 设置同步状态的值
protected final void setState(int newState) {
    state = newState;
}
// 原子地（CAS 操作）将同步状态值设置为给定值 update 如果当前同步状态的值等于 expect（期望值）
protected final boolean compareAndSetState(int expect, int update) {
    return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
}
```

只有成功修改 state 状态的线程才能成为占用线程，否则就进入 CLH 队列等待执行。

### 共享模式

AQS 提供了两种资源共享方式：独占式和共享式。

#### 独占式

独占式：只有一个线程能执行，如 ReentrantLock。又可分为公平锁和非公平锁,ReentrantLock 同时支持两种锁,下面以 ReentrantLock 对这两种锁的定义做介绍：

- 公平锁：按照线程在队列中的排队顺序，先到者先拿到锁
- 非公平锁：当线程要获取锁时，先通过两次 CAS 操作去抢锁，如果没抢到，当前线程再加入到队列中等待唤醒。

```java
tryAcquire(int) //独占方式。尝试获取资源，成功则返回 true，失败则返回 false。
tryRelease(int) //独占方式。尝试释放资源，成功则返回 true，失败则返回 false。
```
#### 共享式

共享式：多个线程可同时执行，如 Semaphore、CountDownLatCh、 CyclicBarrier、ReadWriteLock。

ReentrantReadWriteLock 可以看成是组合式，因为 ReentrantReadWriteLock 也就是读写锁允许多个线程同时对某一资源进行读。

不同的自定义同步器争用共享资源的方式也不同。自定义同步器在实现时只需要实现共享资源 state 的获取与释放方式即可，至于具体线程等待队列的维护（如获取资源失败入队/唤醒出队等），AQS 已经在上层已经帮我们实现好了。

```java
tryAcquireShared(int) //共享方式。尝试获取资源。负数表示失败；0 表示成功，但没有剩余可用资源；正数表示成功，且有剩余资源。
tryReleaseShared(int) //共享方式。尝试释放资源，成功则返回 true，失败则返回 false。
```

### 应用

ReentrantLock、ReentrantReadWriteLock、Semaphore、CountDownLatCh、 CyclicBarrier。