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

