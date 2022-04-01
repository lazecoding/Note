# 入门

WebFlux 是 Spring Framework 5.0 中引入的一种新的响应式 Web 框架。通过 Reactor 项目实现 Reactive Streams 规范，完全异步和非阻塞框架。本身不会加快程序执行速度，但在高并发情况下借助异步 IO 能够以少量而稳定的线程处理更高的吞吐，规避文件 IO / 网络 IO 阻塞带来的线程堆积。

> 响应式编程（reactive programming）是一种基于数据流（data stream）和变化传递（propagation of change）的声明式（declarative）的编程范式。

WebFlux 具有以下特性：

- `异步非阻塞`：举个例子。相对于 Spring MVC 是同步阻塞 IO 模型，Spring WebFlux 这样处理：线程发现文件数据没传输好，就先做其他事情，当文件准备好时通知线程来处理（这里就是输入非阻塞方式），当接收完并写入磁盘（该步骤也可以采用异步非阻塞方式）完毕后再通知线程来处理响应（这里就是输出非阻塞方式）。
- `响应式函数编程`：相对于Java 8 Stream 同步、阻塞的 Pull 模式，Spring Flux 采用 Reactor Stream 异步、非阻塞 Push 模式。书写采用 Java lambda 方式,接近自然语言形式且容易理解。
- `不拘束于 Servlet`：可以运行在传统的 Servlet 容器（3.1 + 版本），还能运行在 Netty、Undertow 等 NIO 容器中。

WebFlux 的设计目标：

- 适用高并发。
- 高吞吐量。
- 可伸缩性。

### 组件介绍

#### HTTPHandler

一个简单的处理请求和响应的抽象，用来适配不同 HTTP 服务容器的 API。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/webflux/HTTPHandler实现.png" width="600px">
</div>

#### WebHandler

一个用于处理业务请求抽象接口，定义了一系列处理行为。相关核心实现类如下；

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/webflux/WebHandler实现.png" width="600px">
</div>

#### DispatcherHandler

请求处理的总控制器，实际工作是由多个可配置的组件来处理。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/webflux/DispatcherHandler组件.png" width="600px">
</div>

WebFlux 是兼容 Spring MVC 基于 @Controller，@RequestMapping 等注解的编程开发方式的，可以做到平滑切换。

#### Functional Endpoints

这是一个轻量级函数编程模型。是基于 @Controller，@RequestMapping 等注解的编程模型的替代方案，提供一套函数式 API 用于创建 Router,Handler 和 Filter。调用处理组件如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/webflux/FunctionalEndpoints组件.png" width="600px">
</div>

简单的 RouterFuntion 路由注册和业务处理过程：

```C
@Bean
public RouterFunction<ServerResponse> initRouterFunction() {
    return RouterFunctions.route()
        .GET("/hello/{name}", serverRequest -> {
            String name = serverRequest.pathVariable("name");
            return ServerResponse.ok().bodyValue(name);
        }).build();
}
```

请求转发处理过程：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/webflux/FunctionalEndpoints执行流程.png" width="600px">
</div>

#### Reactive Stream

这是一个重要的组件，WebFlux 就是利用 Reactor 来重写了传统 Spring MVC 逻辑。其中 Flux 和 Mono 是 Reactor中两个关键概念。

Flux 和 Mono 都实现了 Reactor 的 Publisher 接口,属于事件发布者，对消费者提供订阅接口，当有事件发生的时候，Flux 或者 Mono 会通过回调消费者的相应的方法来通知消费者相应的事件。其中，Mono 代表 0 到 1 个元素的响应式序列，Flux 代表 0 到 N 个元素的结果。