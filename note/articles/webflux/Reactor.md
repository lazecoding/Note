# Reactor

- 目录
  - [Reactive Streams](#Reactive-Streams)
  - [Reactor](#Reactor)
    - [Flux](#Flux)
    - [Mono](#Mono)

之前我们了解过，Reactor 是事件驱动模型，当服务端接收到一个或多个请求，由专门的线程处理传入的多路请求，并将它们同步分派给请求对应的处理线程。Reactor 通过调度适当的处理程序来响应 I/O 事件，处理程序执行非阻塞操作。

点击 [Reactor](https://github.com/lazecoding/Note/blob/main/note/articles/architecturemodel/Reactor.md) 了解相关内容。

这里我们想说的是 Reactive Streams（响应式流），此次的 Reactor 也非彼 Reactor。

响应式编程，本质上是对数据流或某种变化所作出的反应，但是这个变化什么时候发生是未知的，所以他是一种基于 `异步、回调` 的方式在处理问题。

### Reactive Streams

Reactive Streams 规范早已提出，规范实际上就是定义了四个接口：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/webflux/ReactiveStreams规范.png" width="600px">
</div>

Reactive Streams 的主要目标有两个：

- 管理跨异步边界的流数据交换，也就是将元素传递到另一个线程或线程池。
- 确保接收方不会强制缓冲任意数量的数据，为了使线程之间的队列有界，引入了 `背压（Back Pressure）`。

> Back Pressure 说白了就是消费者能告诉生产者自己需要多少量的数据。

Java 直到 JDK 9 提供了对于 Reactive Streams 的完整支持。

`java/util/concurrent/Flow.java 类图`：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/webflux/Java-Flow类图.png" width="600px">
</div>

下面我们来看看 JDK 9 接口的方法：

```java
// 发布者(生产者)
public interface Publisher<T> {
    public void subscribe(Subscriber<? super T> s);
}
// 订阅者(消费者)
public interface Subscriber<T> {
    public void onSubscribe(Subscription s);
    public void onNext(T t);
    public void onError(Throwable t);
    public void onComplete();
}
// 用于发布者与订阅者之间的通信(实现背压：订阅者能够告诉生产者需要多少数据)
public interface Subscription {
    public void request(long n);
    public void cancel();
}
// 用于处理发布者 发布消息后，对消息进行处理，再交由消费者消费
public interface Processor<T,R> extends Subscriber<T>, Publisher<R> {
}
```

- Publisher 只有一个方法，用来接受 Subscriber 进行订阅（subscribe）。T 代表 Publisher 和 Subscriber 之间传输的数据类型。

- Subscriber 有 4 个事件方法，分别在开启订阅、接收数据、发生错误和数据传输结束时被调用。

- Subscription 是 Processor 和 Subscriber 之间交互的操作对象，在 Publisher 通过 subscribe 方法加入 Subscriber 时，会通过调用 Subscriber 的 onSubscribe 把 Subscription 传给 Subscriber。Subscriber 拿到 Subscription 后，通过调用 Subscription 的 request 方法，根据自身消费能力请求 n 条数据，或者调用 cancel 方法来停止接收数据。Subscription 的 request 方法被调用时，会通过 Subscriber 的 onNext 事件方法，把数据传输给 Subscriber。如果数据全部传输完成，则触发 Subscriber 的 onComplete 事件方法。如果数据传输发生错误，则触发 Subscriber 的 onError 事件方法。

- Processor 既是 Publisher，又是 Subscriber。它可用于在 Publisher 和 Subscriber 之间转换数据格式，把 Publisher 的 T 类型数据转换为 Subscriber 接受的 R 类型数据。Processor 作为数据转换的中介不是必须的。

### Reactor

Reactive Streams 不要求必须使用 Java 8，Reactive Streams 也不是 Java API 的一部分。但是使用 Java 8 中 lambda 表达式的存在，可以发挥 Reactive Streams 规范的强大特性，比如 Reactive Streams 的实现 Project Reactor 项目，就要求最低使用 Java 8。当使用 Java 9 时， Reactive Streams 已成为官方 Java 9 API 的一部分，Java 9 中 Flow 类下的内容与 Reactive Streams 完全一致。

Reactive Streams 的实现很多，Reactor 是 Pivotal 提供的 Java 实现，它作为 Spring Framework 5 的重要组成部分，是 WebFlux 采用的默认反应式框架。

> WebFlux 底层使用的是 reactor-netty，而 reactor-netty 依赖 Reactor。

project-pom.xml:

```xml
<!-- webflux -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

org/springframework/boot/spring-boot-starter-webflux/2.3.5.RELEASE/spring-boot-starter-webflux-2.3.5.RELEASE.pom.xml:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-reactor-netty</artifactId>
  <version>2.3.5.RELEASE</version>
  <scope>compile</scope>
</dependency>
```

org/springframework/boot/spring-boot-starter-reactor-netty/2.3.5.RELEASE/spring-boot-starter-reactor-netty-2.3.5.RELEASE.pom.xml:

```xml
<dependency>
  <groupId>io.projectreactor.netty</groupId>
  <artifactId>reactor-netty</artifactId>
  <version>0.9.13.RELEASE</version>
  <scope>compile</scope>
</dependency>
```

io/projectreactor/netty/reactor-netty/0.9.13.RELEASE/reactor-netty-0.9.13.RELEASE.pom.xml:

```xml
<dependency>
  <groupId>io.projectreactor</groupId>
  <artifactId>reactor-core</artifactId>
  <version>3.3.11.RELEASE</version>
  <scope>compile</scope>
  <exclusions>
    <exclusion>
      <artifactId>commons-logging</artifactId>
      <groupId>commons-logging</groupId>
    </exclusion>
  </exclusions>
</dependency>
```

Reactor 提供了两个非常有用的操作，他们是 Flux 和 Mono。其中 Flux 代表的是 0 to N 个响应式序列，而 Mono 代表的是 0 或者 1 个响应式序列。

Flux 和 Mono 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/webflux/Reactor-Flux和Mono类图.png" width="300px">
</div>

Flux 和 Mono 一样，都是 Publisher 的实现类。

#### Flux

Flux 是一个发出 0 - N 个元素组成的异步序列的 Publisher<T>,可以被 onComplete 信号或者 onError 信号所终止。在响应流规范中存在三种给下游消费者调用的方法 onNext、onComplete 和 onError。

下图是 Flux 的抽象模型：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/webflux/Reactor-Flux-抽象模型.png" width="600px">
</div>

#### Mono

Mono 和 Flux 类似，区别在于它表示的是一个发出 0 - 1 个元素组成的异步序列的 Publisher<T>。

下图是 Mono 的抽象模型：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/webflux/Reactor-Mono-抽象模型.png" width="600px">
</div>