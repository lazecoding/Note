# Redisson

- 目录
    - [分布式对象](#分布式对象)
        - [Object holder](#Object-holder)
        - [BitSet](#BitSet)
        - [AtomicLong](#AtomicLong)
        - [LongAdder](#LongAdder)
        - [Bloom filter](#Bloom-filter)
        - [RateLimiter](#RateLimiter)
    - [分布式容器](#分布式容器)
        - [Map](#Map)
        - [Set](#Set)
        - [SortedSet](#SortedSet)
        - [ScoredSortedSet](#ScoredSortedSet)
        - [List](#List)
        - [Queue](#Queue)
        - [Deque](#Deque)
        - [Blocking Queue](#Blocking-Queue)
        - [Bounded Blocking Queue](#Bounded-Blocking-Queue)
        - [Delayed Queue](#Delayed-Queue)
        - [Priority Queue](#Priority-Queue)
    - [锁](#锁)
        - [RedissonLock](#RedissonLock)
        - [RedissonLock 子类](#RedissonLock-子类)
        - [RedissonMultiLock](#RedissonMultiLock)
        - [RedissonRedLock](#RedissonRedLock)
        - [同步工具](#同步工具)
    - [分布式服务](#分布式服务)

Redisson 是一个在 Redis 的基础上实现的 Java 驻内存数据网格（In-Memory Data Grid）。它提供了一系列常用的分布式的 Java 对象和许多分布式服务。

Redisson 的宗旨是促进使用者对 Redis 的关注分离，从而让使用者能够将精力更集中地放在处理业务逻辑上。Redisson 基于 Redis 实现了很多分布式对象、容器、锁、服务。

Redisson 官方的 [使用说明](https://github.com/redisson/redisson/wiki/Table-of-Content) 十分完善，包含中英双版的说明和示例。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/Redisson使用说明.png" width="800px">
</div>

### 分布式对象

Redisson 基于 Redis 封装了很多分布式对象，如 Object、BitSet、AtomicLong、Bloom filters、RateLimiter。

#### Object holder

Redisson 分布式的 RBucket  对象可用作任意类型对象的通用容器。

通常我们使用 RBucket 操作字符串，如果操作对象，被操作对象需要实现序列化接口。

```java
RBucket<AnyObject> bucket = redisson.getBucket("anyObject");
bucket.set(new AnyObject(1));
AnyObject obj = bucket.get();

bucket.trySet(new AnyObject(3));
bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
bucket.getAndSet(new AnyObject(6));
```

#### BitSet

Redisson 的分布式 RBitSet  对象采用了与 java.util.BiteSet 类似结构的设计风格。它可以理解为是一个分布式的可伸缩式位向量。需要注意的是 RBitSet 的大小受 Redis 限制，最大长度为 4 294 967 295。

```java
RBitSet set = redisson.getBitSet("simpleBitset");
set.set(0, true);
set.set(1812, false);
set.clear(0);
set.addAsync("e");
set.xor("anotherBitset");
```

#### AtomicLong

Redisson 的分布式原子 RAtomicLong 对象和 Java 中的 java.util.concurrent.atomic.AtomicLong 对象类似，通常用于原子数值操作

```java
RAtomicLong atomicLong = redisson.getAtomicLong("myAtomicLong");
atomicLong.set(3);
atomicLong.incrementAndGet();
atomicLong.get();
```

Redisson 还提供了分布式原子双精度浮 点RAtomicDouble。

#### LongAdder

Redisson 提供了分布式累加器 LongAdder 采用了与 java.util.concurrent.atomic.LongAdder 类似的接口。通过利用客户端内置的 LongAdder 对象，为分布式环境下递增和递减操作提供了很高得性能。据统计其性能最高比分布式 AtomicLong 对象快 12000 倍。

```java
RLongAdder atomicLong = redisson.getLongAdder("myLongAdder");
atomicLong.add(12);
atomicLong.increment();
atomicLong.decrement();
atomicLong.sum();
```

当不再使用整长型累加器对象的时候应该自行手动销毁，如果 Redisson 对象被关闭（shutdown）了，则不用手动销毁。

```java
RLongAdder atomicLong = ...
atomicLong.destroy();
```

Redisson 也提供了 DoubleAdder。

#### Bloom filter

Redisson 利用 Redis 实现了 Java 分布式布隆过滤器（Bloom Filter），所含最大比特数量为 2^32。

```java
RBloomFilter<SomeObject> bloomFilter = redisson.getBloomFilter("sample");
// 初始化布隆过滤器，预计统计元素数量为55000000，期望误差率为0.03
bloomFilter.tryInit(55000000L, 0.03);
bloomFilter.add(new SomeObject("field1Value", "field2Value"));
bloomFilter.add(new SomeObject("field5Value", "field8Value"));
bloomFilter.contains(new SomeObject("field1Value", "field8Value"));
```

#### RateLimiter

Redisson 提供的分布式限流器（RateLimiter）可以用来在分布式环境下现在请求方的调用频率（该算法不保证公平性）。

```java
RRateLimiter rateLimiter = redisson.getRateLimiter("myRateLimiter");
// 初始化
// 每 1 秒钟产 生10 个令牌
rateLimiter.trySetRate(RateType.OVERALL, 10, 1, RateIntervalUnit.SECONDS);

// 获取令牌
limiter.acquire(3);
```

### 分布式容器

Redisson 基于 Redis 实现了一套分布式容器。

#### Map

Redisson 的分布式映射结构的 RMap 对象实现了 java.util.concurrent.ConcurrentMap 接口和 java.util.Map 接口。与 HashMap 不同的是，RMap 保持了元素的插入顺序。该对象的最大容量受 Redis 限制，最大元素数量是 4 294 967 295 个。

```java
RMap<String, SomeObject> map = redisson.getMap("anyMap");
SomeObject prevObject = map.put("123", new SomeObject());
SomeObject currentObject = map.putIfAbsent("323", new SomeObject());
SomeObject obj = map.remove("123");

map.fastPut("321", new SomeObject());
map.fastRemove("321");

RFuture<SomeObject> putAsyncFuture = map.putAsync("321");
RFuture<Void> fastPutAsyncFuture = map.fastPutAsync("321");

map.fastPutAsync("321", new SomeObject());
map.fastRemoveAsync("321");
```

#### Set

Redisson 的分布式 Set 结构的 RSet 对象实现了 java.util.Set 接口。通过元素的相互状态比较保证了每个元素的唯一性。该对象的最大容量受 Redis 限制，最大元素数量是 4 294 967 295 个。

```java
RSet<SomeObject> set = redisson.getSet("anySet");
set.add(new SomeObject());
set.remove(new SomeObject());
```

#### SortedSet

Redisson 的分布式 RSortedSet 对象实现了 java.util.SortedSet 接口。在保证元素唯一性的前提下，通过比较器（Comparator）接口实现了对元素的排序。

```java
RSortedSet<Integer> set = redisson.getSortedSet("anySet");
set.trySetComparator(new MyComparator()); // 配置元素比较器
set.add(3);
set.add(1);
set.add(2);

set.removeAsync(0);
set.addAsync(5);
```

#### ScoredSortedSet

Redisson 的分布式 RScoredSortedSet 对象是一个可以按插入时指定的元素评分排序的集合，它同时保证了元素的唯一性。

```java
RScoredSortedSet<SomeObject> set = redisson.getScoredSortedSet("simple");

set.add(0.13, new SomeObject(a, b));
set.addAsync(0.251, new SomeObject(c, d));
set.add(0.302, new SomeObject(g, d));

set.pollFirst();
set.pollLast();

int index = set.rank(new SomeObject(g, d)); // 获取元素在集合中的位置
Double score = set.getScore(new SomeObject(g, d)); // 获取元素的评分
```

#### List

Redisson 的分布式 RList 对象在实现了 java.util.List 接口的同时，确保了元素插入时的顺序。该对象的最大容量受 Redis 限制，最大元素数量是 4 294 967 295 个。

```java
RList<SomeObject> list = redisson.getList("anyList");
list.add(new SomeObject());
list.get(0);
list.remove(new SomeObject());
```

#### Queue

Redisson 的分布式无界队列 RQueue 对象实现了 java.util.Queue 接口。尽管 RQueue 对象无初始大小（边界）限制，但对象的最大容量受 Redis 限制，最大元素数量是 4 294 967 295 个。

```java
RQueue<SomeObject> queue = redisson.getQueue("anyQueue");
queue.add(new SomeObject());
SomeObject obj = queue.peek();
SomeObject someObj = queue.poll();
```

#### Deque

Redisson 的分布式无界双端队列 RDeque 对象实现了 java.util.Deque 接口。尽管 RDeque 对象无初始大小（边界）限制，但对象的最大容量受 Redis 限制，最大元素数量是 4 294 967 295 个。

```java
RDeque<SomeObject> queue = redisson.getDeque("anyDeque");
queue.addFirst(new SomeObject());
queue.addLast(new SomeObject());
SomeObject obj = queue.removeFirst();
SomeObject someObj = queue.removeLast();
```

#### Blocking Queue

Redisson 的分布式无界阻塞队列 RBlockingQueue 对象实现了java.util.concurrent.BlockingQueue接口。

```java
RBlockingQueue<SomeObject> queue = redisson.getBlockingQueue("anyQueue");
queue.offer(new SomeObject());

SomeObject obj = queue.peek();
SomeObject someObj = queue.poll();
SomeObject ob = queue.poll(10, TimeUnit.MINUTES);
```

#### Bounded Blocking Queue

Redisson 的分布式有界阻塞队列 RBoundedBlockingQueue 对象实现了 java.util.concurrent.BlockingQueue 接口，队列的初始容量（边界）必须在使用前设定好。

```java
RBoundedBlockingQueue<SomeObject> queue = redisson.getBoundedBlockingQueue("anyQueue");
// 如果初始容量（边界）设定成功则返回`真（true）`，
// 如果初始容量（边界）已近存在则返回`假（false）`。
queue.trySetCapacity(2);

queue.offer(new SomeObject(1));
queue.offer(new SomeObject(2));
// 此时容量已满，下面代码将会被阻塞，直到有空闲为止。
queue.put(new SomeObject());

SomeObject obj = queue.peek();
SomeObject someObj = queue.poll();
SomeObject ob = queue.poll(10, TimeUnit.MINUTES);
```

#### Delayed Queue

Redisson 的分布式延迟队列 RDelayedQueue 对象在实现了 RQueue 接口的基础上提供了向队列按要求延迟添加项目的功能。该功能可以用来实现消息传送延迟按几何增长或几何衰减的发送策略。

```java
RQueue<String> distinationQueue = ...
RDelayedQueue<String> delayedQueue = getDelayedQueue(distinationQueue);
// 10秒钟以后将消息发送到指定队列
delayedQueue.offer("msg1", 10, TimeUnit.SECONDS);
// 一分钟以后将消息发送到指定队列
delayedQueue.offer("msg2", 1, TimeUnit.MINUTES);
```

在该对象不再需要的情况下，应该主动销毁。仅在相关的 Redisson 对象也需要关闭的时候可以不用主动销毁。

```java
RDelayedQueue<String> delayedQueue = ...
delayedQueue.destroy();
```

#### Priority Queue

Redisson 的分布式优先双端队列 RPriorityDeque 对象实现了 java.util.Deque 的接口，可以通过比较器（Comparator）接口来对元素排序。

```java
RPriorityDeque<Integer> queue = redisson.getPriorityDeque("anyQueue");
queue.trySetComparator(new MyComparator()); // 指定对象比较器
queue.addLast(3);
queue.addFirst(1);
queue.add(2);

queue.removeAsync(0);
queue.addAsync(5);

queue.pollFirst();
queue.pollLast();
```

> 类似的双端队列不再赘述

### 锁

RLock 是 Redisson 分布式锁的接口，它的主要实现有 RedissonLock、RedissonFairLock、RedissonReadLock、RedissonWriteLock、RedissonMultiLock、RedissonRedLock。

类图如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/RLock类图.png" width="600px">
</div>

RLock 接口类（主要方法）：

```java
public interface RLock extends Lock, RLockAsync {
    /**
     * 锁的键名
     */
    String getName();

    /**
     * 获取锁（阻塞式）
     */
    void lock();

    /**
     * 获取锁，提供 TTL（阻塞式）
     */
    void lock(long leaseTime, TimeUnit unit);

    /**
     * 中断锁，表示该锁可以被中断
     * 假如 A、B 同时调某个方法，A 获取锁，B 未获取锁，那么B线程可以通过
     * Thread.currentThread().interrupt(); 方法真正中断该线程
     */
    void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * 尝试获取锁并返回结果（只有在锁空闲时才能获取到锁）
     */
    boolean tryLock();

    /**
     * 尝试获取锁并返回结果，提供 TTL（只有在锁空闲时才能获取到锁）
     */
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * 释放锁
     */
    void unlock();
    
    /**
     * 获取锁剩余存活时间
     */
    long remainTimeToLive();
}
```

> RLock 和 Java 并发包中的 Lock 接口主要区别在于添加了 leaseTime 属性字段，用来设置锁的过期时间，避免死锁。

#### RedissonLock

RedissonLock 是一个可重入的分布式锁，它是 RLock 接口最基础、最核心的实现。

使用示例：

```java
RLock lock = redisson.getLock("lock_key");

// 获取锁
lock.lock();

try {
    // ....
} catch (Exception e) {
    e.printStackTrace();
} finally {
    // 解锁
    lock.unlock();
}
```

redisson.getLock("lock_key"); 获取的是 RedissonLock 实例；lock() 调用的是阻塞式获取锁，如果没获取到锁便会一直阻塞，直到成功获取锁；unlock() 是释放当前线程持有的锁。

lock：

```java
// org/redisson/RedissonLock.java#lock
@Override
public void lock() {
    try {
        lock(-1, null, false);
    } catch (InterruptedException e) {
        throw new IllegalStateException();
    }
}
```

RedissonLock.lock() 是不具备 TTL 的，即没有超时时间，所以 leaseTime 传参 -1。

```java
// org/redisson/RedissonLock.java#lock
private void lock(long leaseTime, TimeUnit unit, boolean interruptibly) throws InterruptedException {
    long threadId = Thread.currentThread().getId();
    Long ttl = tryAcquire(leaseTime, unit, threadId);
    // lock acquired
    if (ttl == null) {
        return;
    }

    RFuture<RedissonLockEntry> future = subscribe(threadId);
    if (interruptibly) {
        commandExecutor.syncSubscriptionInterrupted(future);
    } else {
        commandExecutor.syncSubscription(future);
    }

    try {
        while (true) {
            ttl = tryAcquire(leaseTime, unit, threadId);
            // lock acquired
            if (ttl == null) {
                break;
            }

            // waiting for message
            if (ttl >= 0) {
                try {
                    future.getNow().getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (interruptibly) {
                        throw e;
                    }
                    future.getNow().getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                }
            } else {
                if (interruptibly) {
                    future.getNow().getLatch().acquire();
                } else {
                    future.getNow().getLatch().acquireUninterruptibly();
                }
            }
        }
    } finally {
        unsubscribe(future, threadId);
    }
}
```

`Long ttl = tryAcquire(leaseTime, unit, threadId);` 方法尝试获取锁。返回值 ttl 指的是锁的存活时间，需要注意的是，从下面的代码来看，如果当前线程持有锁返回值是 null，只有未获取到锁返回值才会有值，即这个返回值是值还需要等待多久才可能获取到锁。

如果 ttl 不为空，即当前线程未获取到锁，下面会循环获取锁。

```java
// org/redisson/RedissonLock.java#tryAcquire
private Long tryAcquire(long leaseTime, TimeUnit unit, long threadId) {
    return get(tryAcquireAsync(leaseTime, unit, threadId));
}

// org/redisson/RedissonLock.java#tryAcquireAsync
private <T> RFuture<Long> tryAcquireAsync(long leaseTime, TimeUnit unit, long threadId) {
    if (leaseTime != -1) {
        return tryLockInnerAsync(leaseTime, unit, threadId, RedisCommands.EVAL_LONG);
    }
    RFuture<Long> ttlRemainingFuture = tryLockInnerAsync(commandExecutor.getConnectionManager().getCfg().getLockWatchdogTimeout(), TimeUnit.MILLISECONDS, threadId, RedisCommands.EVAL_LONG);
    ttlRemainingFuture.onComplete((ttlRemaining, e) -> {
        if (e != null) {
            return;
        }

        // lock acquired
        if (ttlRemaining == null) {
            scheduleExpirationRenewal(threadId);
        }
    });
    return ttlRemainingFuture;
}

// org/redisson/RedissonLock.java#tryLockInnerAsync
<T> RFuture<T> tryLockInnerAsync(long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
    internalLockLeaseTime = unit.toMillis(leaseTime);

    return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
              "if (redis.call('exists', KEYS[1]) == 0) then " +
                  "redis.call('hset', KEYS[1], ARGV[2], 1); " +
                  "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                  "return nil; " +
              "end; " +
              "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                  "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                  "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                  "return nil; " +
              "end; " +
              "return redis.call('pttl', KEYS[1]);",
                Collections.<Object>singletonList(getName()), internalLockLeaseTime, getLockName(threadId));
}
```

`tryLockInnerAsync(commandExecutor.getConnectionManager().getCfg().getLockWatchdogTimeout(), TimeUnit.MILLISECONDS, threadId, RedisCommands.EVAL_LONG);` 为锁设置了默认的有效时间 30S。该方法通过 Lua 脚本访问 Redis，保证了操作的原子性。

成功获取锁之后， `scheduleExpirationRenewal` 用于创建 TTL 刷新任务。

```lua
-- keys = {"lock_key"}
-- params = {30000, "1cd16e08-4c61-49d8-9085-a00beeaa3499:1"} 
if (redis.call('exists', KEYS[1]) == 0) then
    redis.call('hset', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end
if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end
return redis.call('pttl', KEYS[1]);
```

Lua 脚本中 KEYS[N] 表示 keys 数组中第 N 个元素，ARGV[N] 表示 params 数组中第 N 个元素。

上述脚本的意思是：
- 如果不存在以该 key 创建一个 hash 对象，hash 中添加一个以 1cd16e08-4c61-49d8-9085-a00beeaa3499:1 为键，1 为值的元素，之后再设置该 hash 对象的 TTL 为 3000 毫秒，并直接返回 nil 。
- 接着，判断以 lock_key 为键的 hash 对象中是否包含以 1cd16e08-4c61-49d8-9085-a00beeaa3499:1 为键的元素，如果存在就将其对应的值 +1，表示当前线程对该锁的持有个数，然后设置 hash 对象的 TTL，并直接返回 nil 。
- 如果前面的判断都没走，就直接调用 pttl 获取该 hash 对象的 TTL 并返回该值。（这意味着锁被其他线程持有）

当加锁成功后，由于 Redisson 的锁都是包含 TTL 的，所以还需要一个机制用于刷新 TTL。

```java
// org/redisson/RedissonLock.java#scheduleExpirationRenewal
private void scheduleExpirationRenewal(long threadId) {
    ExpirationEntry entry = new ExpirationEntry();
    ExpirationEntry oldEntry = EXPIRATION_RENEWAL_MAP.putIfAbsent(getEntryName(), entry);
    if (oldEntry != null) {
        oldEntry.addThreadId(threadId);
    } else {
        entry.addThreadId(threadId);
        renewExpiration();
    }
}

// org/redisson/RedissonLock.java#renewExpirationAsync
protected RFuture<Boolean> renewExpirationAsync(long threadId) {
    return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
            "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                "return 1; " +
            "end; " +
            "return 0;",
        Collections.<Object>singletonList(getName()), 
        internalLockLeaseTime, getLockName(threadId));
}
```

scheduleExpirationRenewal 会注册一个定时任务，每执行一次任务会刷新 TTL，并重新注册一个定时任务，反复。

tryLock：

```java
// org/redisson/tryLock.java#lock
@Override
public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
    long time = unit.toMillis(waitTime);
    long current = System.currentTimeMillis();
    long threadId = Thread.currentThread().getId();
    Long ttl = tryAcquire(leaseTime, unit, threadId);
    // lock acquired
    if (ttl == null) {
        return true;
    }
    
    time -= System.currentTimeMillis() - current;
    if (time <= 0) {
        acquireFailed(threadId);
        return false;
    }
    
    current = System.currentTimeMillis();
    RFuture<RedissonLockEntry> subscribeFuture = subscribe(threadId);
    if (!subscribeFuture.await(time, TimeUnit.MILLISECONDS)) {
        if (!subscribeFuture.cancel(false)) {
            subscribeFuture.onComplete((res, e) -> {
                if (e == null) {
                    unsubscribe(subscribeFuture, threadId);
                }
            });
        }
        acquireFailed(threadId);
        return false;
    }

    try {
        time -= System.currentTimeMillis() - current;
        if (time <= 0) {
            acquireFailed(threadId);
            return false;
        }
    
        while (true) {
            long currentTime = System.currentTimeMillis();
            ttl = tryAcquire(leaseTime, unit, threadId);
            // lock acquired
            if (ttl == null) {
                return true;
            }

            time -= System.currentTimeMillis() - currentTime;
            if (time <= 0) {
                acquireFailed(threadId);
                return false;
            }

            // waiting for message
            currentTime = System.currentTimeMillis();
            if (ttl >= 0 && ttl < time) {
                subscribeFuture.getNow().getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
            } else {
                subscribeFuture.getNow().getLatch().tryAcquire(time, TimeUnit.MILLISECONDS);
            }

            time -= System.currentTimeMillis() - currentTime;
            if (time <= 0) {
                acquireFailed(threadId);
                return false;
            }
        }
    } finally {
        unsubscribe(subscribeFuture, threadId);
    }
}
```

tryLock 和 lock 是否相似，如果成功获取锁它们是没有区别的，当未成功获取锁时 tryLock 相较 lock 多一个 waitTime 属性，用于控制等待时间，超出这个时间则不再尝试获取锁。

unlock：

unlock 逻辑很简单，就是解锁。

```java
// org/redisson/RedissonLock.java#unlock
@Override
public void unlock() {
    try {
        get(unlockAsync(Thread.currentThread().getId()));
    } catch (RedisException e) {
        if (e.getCause() instanceof IllegalMonitorStateException) {
            throw (IllegalMonitorStateException) e.getCause();
        } else {
            throw e;
        }
    }
}

// org/redisson/RedissonLock.java#unlockAsync
@Override
public RFuture<Void> unlockAsync(long threadId) {
    RPromise<Void> result = new RedissonPromise<Void>();
    RFuture<Boolean> future = unlockInnerAsync(threadId);

    future.onComplete((opStatus, e) -> {
        cancelExpirationRenewal(threadId);

        if (e != null) {
            result.tryFailure(e);
            return;
        }

        if (opStatus == null) {
            IllegalMonitorStateException cause = new IllegalMonitorStateException("attempt to unlock lock, not locked by current thread by node id: "
                    + id + " thread-id: " + threadId);
            result.tryFailure(cause);
            return;
        }

        result.trySuccess(null);
    });

    return result;
}
```

unlockInnerAsync 负责执行解锁操作；`opStatus == null` 表示非持有者释放锁，会抛出 IllegalMonitorStateException 异常。

cancelExpirationRenewal 用于移除 TTL 刷新任务。

```java
// org/redisson/RedissonLock.java#unlockInnerAsync
protected RFuture<Boolean> unlockInnerAsync(long threadId) {
    return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
            "if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then " +
                "return nil;" +
            "end; " +
            "local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); " +
            "if (counter > 0) then " +
                "redis.call('pexpire', KEYS[1], ARGV[2]); " +
                "return 0; " +
            "else " +
                "redis.call('del', KEYS[1]); " +
                "redis.call('publish', KEYS[2], ARGV[1]); " +
                "return 1; "+
            "end; " +
            "return nil;",
            Arrays.<Object>asList(getName(), getChannelName()), LockPubSub.UNLOCK_MESSAGE, internalLockLeaseTime, getLockName(threadId));

}
```

unlockInnerAsync 通过 Lua 脚本访问 Redis，保证了操作的原子性。

```lua
-- keys = {"lock_key","redisson_lock__channel:{lock_key}"}
-- params = {0, 30000, "27ef269e-288d-41c4-b11c-6afc7c747e94:1"}
if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then
    return nil;
end
local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1);
if (counter > 0) then
    redis.call('pexpire', KEYS[1], ARGV[2]);
    return 0;
else
    redis.call('del', KEYS[1]);
    redis.call('publish', KEYS[2], ARGV[1]);
    return 1;
end
return nil;
```

上述脚本的意思是：
- 如果以 lock_key 为键的 hash 对象中不存在以 27ef269e-288d-41c4-b11c-6afc7c747e94:1 为键的元素，直接返回 nil
- （因为锁的可重入性）释放锁操作是将 27ef269e-288d-41c4-b11c-6afc7c747e94:1 对应元素的值 -1 并获取新值
- 如果新值 > 0，意味着锁仍被持有，不能彻底删除，因此需要更新 TTL 为 30000 毫秒，返回 0；反之，意味着刚刚释放的锁是最后一把，直接执行 del 命令删除锁，并发布锁释放消息，返回 1。

#### RedissonLock 子类

RedissonLock 子类有 RedissonFairLock、RedissonReadLock、RedissonWriteLock。

RedissonFairLock：

```java
RLock lock = redisson.getFairLock("fairLock_key");
```

RedissonFairLock 通过 getFairLock 获取，它和普通锁的区别在于使用了 ReentrantLock 中公平锁的特性，保证获取锁的顺序性。

RedissonReadLock 和 RedissonWriteLock：

```java
RReadWriteLock rReadWriteLock = redisson.getReadWriteLock("readWriteLock_key");
RLock readLock = rReadWriteLock.readLock();
RLock writeLock = rReadWriteLock.writeLock();
```

RedissonReadLock 和 RedissonWriteLock 对应的是 Java 并发包中的 ReentrantReadWriteLock。它的特性是读锁共享，写锁互斥。

#### RedissonMultiLock

RedissonMultiLock 可以将多个 RLock 关联为一个联锁，每个 RLock 对象实例可以来自于不同的 Redisson 实例，只有所有的锁都上锁成功才算成功。

```java
RLock lock1 = redisson.getLock("lockformulti1");
RLock lock2 = redisson.getLock("lockformulti2");
RLock lock3 = redisson.getLock("lockformulti3");
RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);
// locks: lock1 lock2 lock3
boolean hasLock = lock.tryLock();
if (hasLock) {
    try {
       // ...
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        lock.unlock();
    }
}
```

RedissonMultiLock 内部维护了一个 `final List<RLock> locks = new ArrayList<>();` 用于存储该 RedissonMultiLock 关联的 RLock 实例。加锁操作就是遍历整个 locks，逐个加锁，全部成功才加锁成功。释放锁操作同理。

#### RedissonRedLock

RedissonRedLock 是 RedissonMultiLock 的子类，该对象也可以用来将多个 RLock 关联为一个红锁，每个 RLock 实例可以来自于不同的 Redisson 实例。和 RedissonMultiLock 不同的是，RedissonRedLock 只需要在大部分节点上加锁成功就算成功。

```java
RLock lock1 = redisson.getLock("lockforred1");
RLock lock2 = redisson.getLock("lockforred2");
RLock lock3 = redisson.getLock("lockforred3");
RedissonRedLock lock = new RedissonRedLock(lock1, lock2, lock3);
// locks: lock1 lock2 lock3
boolean hasLock = lock.tryLock();
if (hasLock) {
    try {
        // ...
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        lock.unlock();
    }
}
```

#### 同步工具

Redisson 还提供了 RSemaphore 和 RCountDownLatch，作为 Semaphore 和 CountDownLatch 的分布式实现。

### 分布式服务

简单看了一下，Redisson 提供了如注册中心、任务调度中心 之类的分布式服务。

并不建议用，太过依赖 Redis 可能给 Redis 带来了过大的压力。毕竟 Redis 指令执行是单线程的，任务太多太重也可能让 Redis 产生瓶颈。