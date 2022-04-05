# Reactor

- 目录
    - [Reactive Streams](#Reactive-Streams)

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