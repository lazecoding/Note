# 生产者

- 目录
    - [生产流程](#生产流程)
      - [配置生产者](#配置生产者)
      - [消息的发送](#消息的发送)
      - [序列化](#序列化)
      - [分区器](#分区器)
      - [拦截器](#拦截器)
  
从编程的角度而言，生产者就是负责向 Kafka 发送消息的应用程序。在 Kafka 的历史变迁中，一共有两个大版本的生产者客户端：第一个是于 Kafka 开源之初使用 Scala 语言编写的客户端，我们可以称之为旧生产者客户端（Old Producer）或 Scala 版生产者客户端；第二个是从 Kafka 0.9x 版本开始推出的使用 Java 语言编写的客户端，我们可以称之为新生产者客户端（New Producer）或 Java 版生产者客户端，它弥补了旧版客户端中存在的诸多设计缺陷。

### 生产流程

一个完整的生产流程需要具备以下几个步骤：

- 配置生产者客户端参数及创建相应的生产者实例。
- 构件待发送的消息。
- 发送消息。
- 关闭生产者实例。

生产者客户端示例代码：

```java
public class KafkaProducerAnalysis {
    public static final String BROKER_LIST = "localhost:9092";
    public static final String TOPIC_NAME = "topic-demo";

    public static Properties initConfig(){
        Properties properties = new Properties();
        properties.put("bootstrap.servers", BROKER_LIST);
        properties.put("key.serializer",
                "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer",
                "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("client.id", "producer.client.id.demo");

        return properties;
    }

    public static void main(String[] args){
        Properties properties = initConfig();
        //配置生产者客户端参数并创建KafkaProducer实例
        KafkaProducer<String, String> producer = new KafkaProducer(properties);
        //构件所需要发送的消息
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_NAME, "hello,Kafka!");
        //发送消息
        try{
            producer.send(record);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            producer.close();
        }
    }
}
```

构件的消息对象ProducerRecord，它并不是单纯意义上的消息，它包含了多个属性，原来需要发送的与业务相关的消息体只是其中的一个value属性，比如“hello，Kafka！”只是ProducerRecord对象中的一个属性。ProducerRecord类的定义如下（只截取成员变量）：

```java
public class ProducerRecord<K, V> {
    private final String topic;//主题
    private final Integer partition;//分区号
    private final Headers headers;//消息头部
    private final K key;//键
    private final V value;//值
    private final Long timestamp;//消息的时间戳
    // ...
}
```

其中 topic 和 partition 字段分别代表消息要发往主题和分区号。headers 字段是消息的头部，Kafka  0.11.x 版本才引入这个属性，它大多用来设定一些与应用相关的信息，如无需要也可以不用设置。key 是用来指定消息的键，它不仅是消息的附加信息，还可以用来计算分区号进而可以让消息发往特定的分区。前面提及消息以主题为单位进行归类，而这个 key 可以让消息再进行二次归类，同一个 key 的消息会被划分到同一个分区中。有 key 的消息可以支持日志压缩的功能。value 是指消息体，一般不为空，如果为空则表示特定的消息--墓碑消息。timestamp 是指消息的时间戳，它有 CreateTime 和 LogAppendTime 两种类型，前者表示消息创建的时间，后者表示消息追加到日志文件的时间。

#### 配置生产者

在创建真正的生产者实例前需要配置相应的参数，比如需要连接的 Kafka 集群地址。在 Kafka 生产者客户端 KafkaProducer 有3个参数是必填的。

- `bootstrap.servers`：该参数用来指定生产者客户端连接 Kafka 集群所需的 broker 地址清单，具体的内容格式为 host1:port1,host2:port2，可以设置一个或多个地址，中间以逗号隔开，此参数的默认值为 ""。注意这里并非需要所有的 broker 地址，因为生产者会从给定的 broker 里查找到其他 broker 的信息。
- `key.serializer` 和 `value.serializer`：broker 端接收的消息必须以字节数组（byte[]）的形式存在。生产者使用的 KafkaProducer<String, String> 和 ProducerRecord<String, String> 中的泛型 <String, String> 对应的就是消息中 key 和 value 的类型，生产者客户端使用这种方式可以让代码具有良好的可读性，不过在发往 broker 之前需要将消息中对应的 key 和 value 做相应的序列化操作来转换成字节数组。key.serializer 和 value.serializer 这两个参数分别用来指定 key 和 value 序列化操作的序列化器，这两个参数无默认值。需要注意的是，这里必须填写序列化器的全限定名。

```java
properties.put("key.serializer", StringSerializer.class.getName());
properties.put("value.serializer", StringSerializer.class.getName());
```

KafkaProducer 是线程安全的，可以在多个线程中共享单个 KafkaProducer 实例，也可以将 KafkaProducer 实例进行池化供其他线程调用。

#### 消息的发送

在创建完生产者实例之后，接下来的工作就是构建消息，即创建 ProducerRecord 对象，其中 topic 属性和 value 属性是必填项，其余都是选填项。

发送消息主要有三种模式：发后即忘（fire-and-forget）、同步（sync）及异步（async）。

前面示例代码的发送方式就是发后即忘，它只管往Kafka中发送消息而并不关心消息是否正确到达。在大多数情况下，这种发送方式没有什么问题，不过在某些时候（比如发生不可重试异常时）会造成消息的丢失。这种发送方式的性能最高，可靠性也最差。

KafkaProducer 的 send() 方法并非是 void 类型，而是 Future<RecordMetadata> 类型，send() 方法有两个重载方法，具体定义如下：

```java
public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
    return this.send(record, (Callback)null);
}

public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback) {
    ProducerRecord<K, V> interceptedRecord = this.interceptors.onSend(record);
    return this.doSend(interceptedRecord, callback);
}
```

要实现 `同步` 的发送方式，可以利用返回的 Future 对象实现，示例如下：

```java
try{
    producer.send(record).get();
}catch (Exception e){
    e.printStackTrace();
}finally {
    producer.close();
}
```

实际上 send() 方法本身就是异步的，send() 方法返回的 Future 对象可以使调用方稍后获得发送的结果。示例中执行 send() 方法之后直接链式调用了 get() 方法来阻塞等待 Kafka 的响应，知道消息发送成功，或者发生异常，如果发生异常，那么就需要捕获异常并交由外层逻辑处理。

此外，也可以在执行完 send() 方法之后不直接调用 get() 方法，比如下面的一种同步发送方式的实现：

```java
try{
    Future<RecordMetadata> future = producer.send(record);
    RecordMetadata metadata = future.get();
    System.out.println(metadata.topic() + "-" + metadata.partition() + ":" + metadata.offset());
}catch (Exception e){
    e.printStackTrace();
}finally {
    producer.close();
}
```

这样可以获取一个 RecordMetadata 对象，在 RecordMetadata 对象里包含了消息的一些元数据信息，比如当前消息的主题、分区号、分区中的偏移量（offset）、时间戳等。如果在应用代码中需要这些信息，则可以使用这个方式。如果不需要，则直接采用 `producer.send(record).get()` 的方式更省事。

Future 表示一个任务的生命周期，并提供了相应的方法来判断任务是否已经完成或取消，以及获取任务的结果和取消任务等。既然 `KafkaProducer.send()` 方法的返回值是一个 Future 类型的对象，那么完全可以用 Java 语言层面的技巧来丰富应用的实现，比如使用 Future 中的 `get(long timeout, TimeUnit unit)` 方法实现可超时的阻塞。

KafkaProducer 中一般会发生两种类型的异常：可重试的异常和不可重试的异常。对于可以重试的异常，如果配置了 retries 参数，那么只要在规定的重试次数内自行恢复了，就不会抛出异常。

同步发送的方式可靠性高，要么消息被发送成功，要么发生异常。如果发生异常，则可以捕获并进行相应的处理，而不会像 "发后即忘" 的方式直接造成消息的丢失。不过同步发送的方式的性能会差很多，需要阻塞等待一条消息发送完之后才能发送下一条。

异步发送的方式，一般是在 send() 方法里指定一个 Callback 的回调函数，Kafka 在返回响应时调用该函数来实现异步的发送确认。虽然 send() 方法的返回值类型就是 Future，而 Future 本身就可以用作异步的逻辑处理，但是 Future 里的 get() 方法在何时调用，以及怎么调用都是需要面对的问题，消息不停地发送，那么诸多消息对应的 Future 对象的处理难免会引起代码处理逻辑的混乱。使用 Callback 的方式非常简洁明了，Kafka有响应时就会回调，要么发送成功，要么抛出异常。异步发送方式的示例如下：

```java
producer.send(record, new Callback() {
    @Override
    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
        if(e != null){
            e.printStackTrace();
        }else{
            System.out.println(metadata.topic()
                    + "-"
                    + metadata.partition()
                    + ":"
                    + metadata.offset());
        }
    }
});
```

对于同一个分区而言，如果消息 record1 于 record2 之前先发送，那么 KafkaProducer 就保证对应的 callback1 在 callback2 之前调用，也就是说，回调函数的调用也可以保证分区有序。

KafkaProducer 在发送完这些消息之后，需要调用 KafkaProducer 的 close() 方法来回收资源。close() 方法会阻塞等待之前所有的发送请求完成之后再关闭 KafkaProducer。与此同时，KafkaProducer 还提供了一个带超时时间的 close() 方法。具体定义如下：

```java
public void close(long timeout, TimeUnit timeUnit);
```

如果调用了带超时时间 timeout 的 close() 方法，那么只会在等待 timeout 时间内来弯沉所有尚未完成的请求处理，然后强行退出。在实际应用中，一般使用的都是无参的 close() 方法。

#### 序列化

生产者需要用序列化器（Serializer）把对象转换成字节数组才能通过网络发送给 Kafka。而在对侧，消费者需要用反序列化器（Deserializer）把从 Kafka 中收到的字节数组转换成相应的对象。客户端自带了 `org.apache.kafka.common.serialization.StringSerializer`，除了用于 String 类型的序列化器，还有 ByteArray、ByteBuffer、Bytes、Double、Integer、Long 这几种类型，它们都实现了 `org.apache.kafka.common.serialization.Serializer`。

生产者使用的序列化器和消费者使用的反序列化器是需要一一对应的，如果生产者使用了某种序列化器，如 StringSerializer，而消费者使用了另一种序列化器，比如 IntegerSerializer，那么是无法解析出想要的数据的。

#### 分区器

消息在通过 send() 方法发往 broker 的过程中，有可能需要经过拦截器（Interceptor）、序列化器（Serializer）和分区器（Partitioner）的一系列作用之后才能真正地发往 broker。拦截器一般不是必需的，而序列化时必需的。消息经过序列化之后就需要确定它发往的分区，如果消息 ProducerRecord 中指定了 partition 字段，那么就不需要分区器的作用，因为 partition 代表的就是所要发往的分区号。
如果消息 ProducerRecord 中没有指定 partition 字段，那么就需要依赖分区器，根据 key 这个字段来计算 partition 的值。分区器的作用就是为消息分配分区。

Kafka 中提供的默认分区器是 `org.apache.kafka.clients.producer.internals.DefaultPartitioner`，它实现了 `org.apache.kafka.clients.producer.Partitioner` 接口，这个接口定义了两个方法：

```java
int partition(String var1, Object var2, byte[] var3, Object var4, byte[] var5, Cluster var6);

void close();
```

#### 拦截器

拦截器（Interceptor）是早在 Kafka  0.10.0.0 中就已经引入的一个功能，Kafka一共有两种拦截器：生产者拦截器和消费者拦截器。

生产者拦截器既可以用来在消息发送钱做一些准备工作，比如按照某个规则过滤不符合要求的消息、修改消息的内容等，也可以用来在发送回调逻辑钱做一些定制化的需求，比如统计类工作。
生产者拦截器的使用也很方便，主要是自定义实现 `org.apache.kafka.clients.producer.ProducerInterceptor` 接口。ProducerInterceptor 接口中包含三个方法：

```java
ProducerRecord<K, V> onSend(ProducerRecord<K, V> var1);

void onAcknowledgement(RecordMetadata var1, Exception var2);

void close();
```

KafkaProducer 在将消息序列化和计算分区之前会调用生产者拦截器的 onSend() 方法来对消息进行相应的定制化操作。