# 编写 Starter

- 目录
  - [创建 pom.xml](#创建-pom.xml)
  - [编写属性配置类](#编写属性配置类)
  - [编写自动配置类](#编写自动配置类)
  - [添加 spring.factories](#添加-spring.factories)
  - [使用](#使用)

SpringBoot 自动配置依赖于 starter，我们可以自己实现一个 starter。

下面展示实现流程。

### 创建 pom.xml

pom.xml：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.6.7</version>
        <relativePath/>
    </parent>
    <groupId>lazecoding</groupId>
    <artifactId>unique-client</artifactId>
    <name>Unique-Client</name>
    <version>release-2.0.0</version>
    <properties>
        <java.version>11</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
    </dependencies>
</project>
```

- 首先这里，需要继承 `spring-boot-starter-parent`，这十分重要。
- 然后引入依赖 `spring-boot-starter-web` 和 `spring-boot-autoconfigure`，用于解析配置类。

### 编写属性配置类

配置类：

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 配置类
 */
@Component
@ConfigurationProperties("unique.client")
public class UniqueClientConfig {

    private String url = "";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "UniqueClientConfig{" +
                "url='" + url + '\'' +
                '}';
    }
}
```

### 编写自动配置类

自动配置类：

```java
import lazecoding.config.UniqueClientConfig;
import lazecoding.api.OpenApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * 自动配置类
 */
@Configuration
@EnableConfigurationProperties(UniqueClientConfig.class)
public class UniqueClientStarter {

    @Resource
    private UniqueClientConfig uniqueClientConfig;

    @Bean
    public OpenApi openApi(UniqueClientConfig uniqueClientConfig) {
        OpenApi openApi = new OpenApi();
        openApi.init(uniqueClientConfig);
        return openApi;
    }

}
```

OpenApi 类：

```java
/**
 * OpenApi：对客户端开放的接口
 **/
public class OpenApi {

    public static UniqueClientConfig UNIQUE_CLIENT_CONFIG;

    public OpenApi() {
    }

    public void init(UniqueClientConfig uniqueClientConfig) {
        OpenApi.UNIQUE_CLIENT_CONFIG = uniqueClientConfig;
    }
    
    // dothings ...
}
```

至此，还没结束，我们需要自动配置类运作。

### 添加 spring.factories

最后一步，在 `resources/META-INF/` 下创建 `spring.factories` 文件：

```C
org.springframework.boot.autoconfigure.EnableAutoConfiguration=lazecoding.starter.UniqueClientStarter
```

工程启动时，Spring 会到 `spring.factories` 文件中寻找各个接口的实现类。

### 使用

能用了，配置依赖，引入即可。

```xml
<dependency>
    <groupId>lazecoding</groupId>
    <artifactId>unique-client</artifactId>
    <version>release-2.0.0</version>
</dependency>
```

修改 `application.yml` 配置文件，添加如下内容：

```yaml
unique:
  client:
    url: http://localhost:8090
```

