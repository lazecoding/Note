# 源码环境构建

本文构建 Dubbo 2.7.15 版本。

### 源码下载

下载地址：https://github.com/apache/dubbo/tree/dubbo-2.7.15

**releases-info**

Bugfix：

- dubbo-spring-boot-actuator compatible with Spring Boot Actuator 2.6.x
- Check before use to avoid possible NPE in MetadataInfo
- Fix DubboConfigEarlyInitializationPostProcessor registered twice in Spring Framework
- Fix issue where dead connections would not be reconnected
- Fix netty server ssl context file leak
- Fix potential NPE in URLBuilder.java
- Make the warm-up process smoother
- Reset the client value of LazyConnectExchangeClient after close
- Fix StringIndexOutOfBoundsException at addParam
- Change default step to FORCE_INTERFACE

Dependency Upgrade：

- Upgrade log4j2 version: 2.11.1 -> 2.17.0
- Upgrade Hessian Lite version: 3.2.11 -> 3.2.12
- Upgrade to jedis: 3.6.0 -> 3.7.0
- Upgrade jetcd: 0.5.3 -> 0.5.7
- Upgrade xstream version: 1.4.10 -> 1.4.12
- Upgrade curator version: 4.0.1 -> 4.2.0

### 本地环境构建

JDK：我本地的是 JDK 12，JDK 8 及以上即可。

`IDEA` > `选择根节点 pom.xml Open As Project` > `Trust Project`。

配置 maven 本地仓库：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/dubbo/本地maven仓库配置.png" width="600px">
</div>

拉取依赖：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/dubbo/拉取依赖构建完成.png" width="600px">
</div>

### 注册中心

使用 Dubbo 需要注册中心，Dubbo 支持多种注册中心，如：Zookeeper、Nacos。