# 源码环境构建 

本文基于 ElasticSearch 7.5.2 构建源码环境。

### 环境准备

- 系统：Windows 10
- IDE：IDEA 2021.3
- JDK：JDK 12.0.2
- ElasticSearch 版本：7.5.2
- Gradle 版本：5.6.2

### 构建步骤

- 解压从 https://github.com/elastic/elasticsearch 下载的 Source Code ZIP。

- 使用 `IDEA` Open 刚刚解压的源码 `elasticsearch-7.5.2\build.gradle`，选择 `Open As Project`。

- 修改 Gradle 设置，打开 `Settings>Build,Execution,Deployment>Build Tools>Gradle`。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/Gradle配置.png" width="600px">
</div>

- 配置 JVM 参数：

```C
# 配置应用目录
-Des.path.conf=D:/Users/elasticsearch-7.5.2/config 
-Des.path.home=D:/Users/elasticsearch-7.5.2 
# 禁用 jmx
-Dlog4j2.disable.jmx=true
# 处理报错，同时要在应用目录的下创建 java.policty 文件 
-Djava.security.policy=D:/Users/elasticsearch-7.5.2/config/java.policy 
# 最大堆和最小堆大小一致
-Xms1g 
-Xmx1g
```

- 处理 AccessControlException

为了处理 `java.security.AccessControlException: access denied ("java.lang.RuntimePermission" "createClassLoader")` 异常，创建 java.policy 文件。

java.policy 内容：

```C
grant {
    permission java.lang.RuntimePermission "createClassLoader";
    permission javax.management.MBeanTrustPermission "register";
}; 
```

- 处理 java.lang.NoClassDefFoundError: org/elasticsearch/plugins/ExtendedPluginsClassLoader

修改 `server/build.gradle` 中 `compileOnly project(':libs:elasticsearch-plugin-classloader')` 为 `compile project(':libs:elasticsearch-plugin-classloader')`。

将 `Settings>Build,Execution,Deployment>Build Tools>Gradle` 中 `Build and run using` 的 `Gradle` 改成 `IntelliJ IDEA`。

- 将发行版中的 config 和 modules 目录拷贝到 `-Des.path.home` 配置的目录下，并做必要配置。

elasticsearch.yml 简单配置：

```yaml
cluster.name: elastic-application
node.name: node-one
# IP绑定
network.host: 127.0.0.1,localhost
# 主节点
node.master: true
cluster.initial_master_nodes: "node-one"
# 端口绑定
http.port: 9200
transport.tcp.port: 9300
# discovery.seed_hosts: ["127.0.0.1:9200"]
discovery.seed_hosts: ["127.0.0.1", "[::1]"]
# 开启跨域访问
http.cors.enabled: true
http.cors.allow-origin: "*"
```

- 在 IDEA 中刷新依赖，可能会出现一些依赖下载过慢，可以配置阿里镜像。

- 完成上述步骤，编译运行，入口位于 `org/elasticsearch/bootstrap/Elasticsearch.java#main`。


