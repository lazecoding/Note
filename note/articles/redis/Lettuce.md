# Lettuce

从 Spring Boot 2.x 开始 Lettuce 已取代 Jedis 成为首选 Redis 的客户端。

> Lettuce 是一个可伸缩的线程安全的 Redis 客户端，支持同步、异步和响应式模式。多个线程可以共享一个连接实例，而不必担心多线程并发问题。
> 它基于优秀 Netty NIO 框架构建，支持 Redis 的高级功能，如 Sentinel、集群、流水线、自动重新连接和 Redis 数据模型。

### 连接池

Lettuce 和 Jedis 一样，底层也使用了 Apache Commons-pool2 封装连接池，参数配置和含义基本一致。但是和 Jedis 不同的是，Jedis 连接池中每个线程都创建了一条连接，而 Lettuce 的连接池中多个线程可以共享一个连接。

Lettuce 的连接是基于 Netty 的，连接实例 (StatefulRedisConnection) 可以在多个线程间并发访问，因为 StatefulRedisConnection 是线程安全的，所以一个连接实例 (StatefulRedisConnection) 就可以满足多线程环境下的并发访问。
同时这是一个可伸缩的设计，一个连接实例不够的情况也可以按需增加连接实例。

这意味着，Lettuce 的 API 是线程安全的，如果不是执行阻塞和事务操作，如 BLPOP 和 MULTI/EXEC，多个线程可以共享一个连接。

### 优点

和 Jedis 相比，Lettuce 具有更好的性能和更低的资源消耗，Lettuce 的 API 是线程安全的，通过共享连接减少客户端和服务端的连接也降低了服务端的资源消耗。此外，Lettuce 还支持同步、异步和反应式 API。



