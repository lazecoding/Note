# 生产者

- 目录
    - [生产流程](#生产流程)
      - [配置生产者](#配置生产者)
      - [消息的发送](#消息的发送)
      - [序列化](#序列化)
      - [分区器](#分区器)
      - [拦截器](#拦截器)
    - [原理分析](#原理分析)
      - [整体架构](#整体架构)
      - [元数据更新](#元数据更新)
      - [生产者参数](#生产者参数)
    - [消息保障](#消息保障)
      - [ACK 机制](#ACK-机制)
      - [幂等](#幂等)
        - [幂等实现](#幂等实现)
      - [事务](#事务)
 
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

### 原理分析

#### 整体架构

消息在真正发往 Kafka 之前，有可能需要经历拦截器（Interceptor）、序列化器（Serializer）和分区器（Partitioner）等一系列的作用，下图是生产者客户端的整体架构：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/kafka/生产者客户端的整体架构.png" width="600px">
</div>

整个生产者客户端由两个线程协调运行，这两个线程分别为主线程和 Sender 线程（发送线程）。在主线程中由 KafkaProducer 创建线程，
然后通过可能的拦截器、序列化器和分区器的作用之后缓存到消息累加器（RecordAccumulator，也称为消息收集器）中。Sender线程负责从 RecordAccumulator 中获取消息并将其发送到 Kafka 中。

RecordAccumulator 主要用来缓存消息以便 Sender 线程可以批量发送，进而减少网络传输的资源消耗以提升性能。RecordAccumulator 缓存的大小通过生产者客户端参数 `buffer.memory` 配置，
默认值为 33554432B，即 32MB。如果生产者发送消息的速度超过发送到服务器的速度，则会导致生产者空间不足，这个时候 KafkaProducer 的 send() 方法调用要么被阻塞，
要么抛出异常，这个取决于参数 `max.block.ms` 的配置，此参数的默认值为 60000，即 60 秒。

主线程中发送过来的消息都会被追加到 RecordAccumulator 的某个双端队列（Deque）中，在 RecordAccumulator 的内部为每个分区都维护了一个双端队列，
队列中的内容就是 ProducerBatch，即 Depue<ProducerBatch>。消息写入缓存时，追加到双端队列的尾部；Sender 读取消息时，从双端队列的头部读取。
注意 ProducerBatch 不是 ProducerRecord，ProducerBatch 中可以包含一至多个 ProducerRecord。通俗地说，ProducerRecord 是生产者中创建的消息，而 ProducerBatch 是一个消息批次，
ProducerRecord 会被包含在 ProducerBatch 中，这样可以使字节的使用更加紧凑。与此同时，将较小的 ProducerRecord 拼凑成一个较大的 ProducerBatch，也可以减少网络请求的慈湖以提升整体的吞吐量。
ProducerBatch 和消息的具体格式有关。如果生产者客户端需要向很多分区发送消息，则可以将 `buffer.memory` 参数适当调大以增加整体的吞吐量。

RecordAccumulator 的内部还有一个 BufferPool，它用来复用 ByteBuffer，以保证缓存的高效利用。不过 BufferPool 只针对特定大小的 ByteBuffer 进行管理，
而其他大小的 ByteBuffer 不会缓存进 BufferPool 中，这个特定的大小由 `batch.size` 参数来指定，默认值为 16384B，即 16KB。我们可以适当地调大 `batch.size` 参数以便多缓存一些消息。

Sender 从 RecordAccumulator 中获取缓存的消息之后，会进一步将原本<分区, Deque< ProducerBatch>> 的保存形式转变成 <Node, List< ProducerBatch> 的形式，
其中 Node 表示 Kafka 集群的 broker 节点。对于网络连接来说，生产者客户端是与具体的 broker 节点建立的连接，也就是向具体的 broker 节点发送消息，而并不关心消息属于哪一个分区；
而对于 KafkaProducer 的应用逻辑而言，我们只关注向哪个分区中发送哪些消息，所以在这里需要做一个应用逻辑层面到网络 I/O 层面的转换。

请求在从 Sender 线程发往 Kafka 之前还会保存到 InFlightRequests 中，保存对象的具体形式为 Map<NodeId, Deque>，
它的主要作用是缓存了已经发出去但还没有收到响应的请求（NodeId 是一个 String 类型，表示节点的 id 编号）。与此同时，InFlightRequests 还提供了许多管理类的方法，
并且通过配置参数还可以限制每个连接（也就是客户端与 Node 之间的连接）最多缓存的请求数。这个配置参数为 `max.in.flight.requests.per.connection`，默认值为 5，
即每个连接最多只能缓存 5 个未响应的请求，超过该数值之后就不能再向这个连接发送更多的请求了，除非有缓存的请求收到了响应（Response）。
通过比较 Deque<Request> 的 size 与这个参数的大小来判断对应的Node中是否已经堆积了很多未响应的消息，如果真是如此，那么说明这个 Node 节点负载较大或网络连接有问题，
再继续向其发送请求会增加请求超时的可能。

InFlightRequests 还可以获得 leastLoadedNode，即所有 Node 中负载最小的那一个。这里的负载最小是通过每个 Node 在 InFlightRequests 中还未确认的请求决定的，未确认的请求越多则认为负载越大。

#### 元数据更新

使用如下方式创建一条消息 ProducerRecord：

```java
ProducerRecord<String, String> record = new ProducerRecord<>(topic, "hello,Kakfa!");
```

这里只有主题的名称，对于其他一些必要的信息都没有。KafkaProducer 要将此信息追加到指定主题的某个分区所对应的 leader 副本之前，首先需要知道主题的分区数量，然后经过计算得出（或者直接指定）目标分区，之后 KafkaProducer 要将次信息追加到指定主题的某个分区所对应的 leader 副本之前，首先需要知道主题的分区数量，然后经过计算得出（或者直接指出）目标分区，之后 KafkaProducer 需要知道目标分区的 leader 副本所在的 broker 节点的地址、端口等信息才能建立连接，最终才能将消息发送到 Kafka，在这一过程中所需要的信息都属于元数据信息。

元数据是指 Kafka 集群的元数据，这些元数据具体记录了集群中有哪些主题，这些主题有哪些分区，每个分区的 leader 副本分配在哪个节点上，follower 副本分配在哪些节点上，哪些副本在 AR、ISR 等集合中，集群中有哪些节点，控制器节点又是哪一个等信息。

当客户端中没有需要使用的元数据信息时，比如没有指定的主题信息，或者超过 `metadata.max.age.ms` 时间没有更新元数据都会引起元数据的更新操作。客户端参数 `metadata.max.age.ms` 的默认值为 3000000，即 5 分钟。元数据的更新操作是在客户端内部进行的，对客户端的外部使用者是不可见。当需要更新元数据时，会先挑选出 leastLoadedNode，然后向这个 Node 发送 MetadataRequest 请求来获取具体的元数据信息。这个更新操作是由 Sender 线程发起的，在创建完 MetadataRequest 之后同样会存入 InFlightRequests，周的步骤和发送消息时类似。元数据虽然由 Sender 线程负责更新，但是主线程也需要读取这些信息，这里的数据同步通过 synchronized 和 final 关键字来保障。

#### 生产者参数

在 KafkaProducer 中，大部分的参数都有合理的默认值，一般不需要修改它们。不过了解这些参数可以更合理地使用生产者客户端，其中还有一些重要的参数涉及程序的可用性和性能，如果能够熟练掌握它们，也可以让我们在编写相关的程序时能够更好地进行性能调优与故障排除。

##### acks

这个参数用来指定分区中必须要有多少个副本收到这条消息，之后生产者才会认为这条消息是成功写入的。acks 是生产者客户端中的一个非常重要的参数，它涉及消息的可靠性和吞吐量之间的权衡。acks 参数有三种类型的值（都是字符串类型）。

- `acks = 1`。默认值即为 1。生产者发送消息之后，只要分区的 leader 副本成功写入消息，那么它就会收到来自服务端的成功响应。如果消息无法写入 leader 副本，比如在 leader 副本崩溃、重新选举新的 leader 副本的过程中，那么生产者就会收到一个错误的响应，为了避免消息丢失，生产者可以选择重发消息。如果消息写入 leader 副本并返回成功响应给生产者，且在被其他 follower 副本拉取之前 leader 副本崩溃，那么此时消息还是会丢失，因为新选举的 leader 副本中并没有这条对应的消息。`acks` 设置为 1，是消息可靠性和吞吐量之间的折中方案。
- `acks = 0`。生产者发送消息之后不需要等待任何服务器端的响应。如果在消息从发送到写入 Kafka 的过程中出现了某些异常，导致 Kafka 并没有收到这条消息，那么生产者也无从得知，消息也就丢失了。在其他配置环境相同的情况下，`acks` 设置为 0 可以达到最大的吞吐量。
- `acks = -1 或 acks = all`。生产者在消息发送之后，需要等待 ISR 中的所有副本都成功写入消息之后才能够收到来自服务端的成功响应。在其他配置环境相同的情况下，`acks` 设置为 -1（all）可以达到最强的可靠性。但这并不意味着消息就一定可靠，因为 ISR 中可能只有 leader 副本，这样就退化成了 `acks = 1` 的情况。要获得更高的消息可靠性需要配合 `min.insync.replicas` 等参数的联动。另外，它还可能产生消息重复的问题，当所有 follower 副本完成同步并由 leader 副本汇聚结果响应客户端的时候，leader 副本挂了，生产者就会认为消息发送失败，重试而导致消息重复。

##### max.request.size

这个用来限制生产者客户端能发送的消息的最大值，默认值为 1048576B，即 1MB。一般情况下，这个默认值就可以满足大多数的应用场景了。这里不建议盲目的增大这个参数的配置值，尤其是在对 Kafka 整体脉络没有没有足够把控的时候。因为这个参数还涉及一些其他参数的联动。比如 broker 端的 `message.max.bytes` 参数配置为 10，而 `max.request.size` 参数配置为 20，那么当我们发送一条大小为 15B 的消息时，生产者客户端就会报出如下的异常：

```C
org.apache.kafka.commom.errors.RecordTooLargeException: The request included a message larger than the max message size the server will accept.
```

##### retries 和 retry.backoff.ms

`retries` 参数用来配置生产者重试的次数，默认值为 0，即在发生异常的时候不进行任何重试动作。消息在从生产者发出到成功写入服务器之前可能发生一些临时性的异常，比如网络抖动、leader 副本的选举等，这种异常往往是可以自行恢复的，生产者可以通过配置 `retries` 大于 0 的值，以此通过内部重试来恢复而不是一味地将异常抛给生产者的应用程序。如果重试次数达到设定的次数，那么生产者就会放弃重试并返回异常。不过并不是所有的异常都是可以通过重试来解决的，比如消息太大，超过 `max.request.size` 参数配置的值时，这种方式就不可行了。

重试还和另一个参数 `retry.backoff.ms` 有关，这个参数的默认值为 100，他用来设定两次重试之间的时间间隔，避免无效的频繁重试。在配置 `retries` 和 `retry.backoff.ms` 之前，最好先估算一下可能的异常恢复时间，这样可以设定总的重试时间大于这个异常恢复时间，以此来避免生产者过早地放弃重试。

Kafka 可以保证同一个分区中的消息是有序的。如果生产者按照一定的顺序发送消息，那么这些消息也会顺序地写入分区，进而消费者也可以按照同样的顺序消费它们。对于某些应用来说，顺序性非常重要，比如 MySQL 的 binlog 传输，如果出现错误就会造成非常严重的后果。如果将 `acks` 参数配置为非零值，并且 `max.in.flight.requests.per.connection` 参数配置为大于 1 的值，那么就会出现错序的现象：如果第一批次消息写入失败，而第二批次消息写入成功，那么生产者就会重试发送第一批次的消息，此时如果第一批次的消息写入成功，那么这两个批次的消息就出现了错序。一般而言，在需要保证消息顺序的场合建议把参数 `max.in.flight.requests.per.connection` 配置为 1，而不是把 `acks` 配置为 0，不过这样也会影响整体的吞吐。

##### compression.type

这个参数用来指定消息的压缩方式，默认值为 "none"，即默认情况下，消息不会被压缩。该参数还可以配置 "gzip"、"snappy" 和 "lz4"。对消息进行压缩可以极大地减少网络传输量、降低网络 I/O，从而提高整体的性能。消息压缩是一种使用时间换空间的优化方式，如果对时延有一定的要求，则不推荐对消息进行压缩。

##### connections.max.idle.ms

这个参数用来指定在多久之后关闭限制的连接，默认值为 540000（ms），即 9 分钟。

##### linger.mx

这个参数用来指定生产者发送 producerBatch 之前等待更多消息（ProducerRecord）加入 ProducerBatch 的时间，默认值为 0.生产者客户端会在 ProducerBatch 被填满或等待时间超过 `linger.ms` 值时发送出去。增大这个参数的值会增加消息的延迟，但是同时能提升一定的吞吐量。这个 `linger.ms` 参数与 TCP 协议中的 Nagle 算法有异曲同工之妙。

##### receive.buffer.bytes

这个参数用来设置 Socket 接收消息缓冲区（SO_RECBUF）的大小，默认值为 32768(B)，即 32KB。如果设置为 -1，则使用操作系统的默认值。如果 Producer 和 Kafka 处于不同的机房，则可以适当调大这个参数值。

##### send.buffer.bytes

这个参数用来设置 Socket 发送消息发送消息缓冲区（SO_SNDBUF）的大小，默认值为 131072(B)，即 128KB。与 `receive.buffer.bytes` 参数一样，如果设置为 -1，则使用操作系统的默认值。

##### request.timeout.ms

这个参数用来配置 Producer 等待请求响应的最长时间，默认值为 30000（ms）。请求超时之后可以选择进行重试。注意这个参数需要比 broker 端参数 `replica.lag.time.max.ms` 的值要大，这样可以减少因客户端重试而引起的消息重复的概率。

##### 更多参数

| 参数名称               | 默认值   | 释义                                         |
| -------------------  | ------- | ------------------------------------------ |
| bootstrap.servers    | ""      | 指定连接 Kafka 集群所需的 broker 地址清单。                                                      |
| key.serializer       | ""      |消息中 key 对应的序列化类，需要实现 org.apache.kafka.common.serialization.Serializer 接口。         |
| value.serializer     | ""      | 消息中 value 对应的序列化类，需要实现 org.apache.kafka.common.serialization.Serializer 接口。      |
| buffer.memory        | 33554432(32MB)   | 生产者客户端中用于缓存消息的缓冲区大小。                                                   |
| batch.size           | 16384(16KB)      | 用于指定 ProducerBatch 可以复用内存区域的大小。                                           |
| client.id            | ""      | 用来设定 KafkaProducer 对应的客户端 id。                                                         |
| max.block.ms         | 60000   | 用来控制 KafkaProducer 中 send() 方法和 partitionsFor() 方法的阻塞时间。当生产者的发送缓冲区已满，或者没有可用的元数据时，这些方法就会阻塞。            |
| partitioner.class    | org.apache.kafka.clients.producer.internals.DefaultPartitioner  | 用来指定分区器，需要实现 org.apache.kakfa.clients.producer.Partitioner 接口。     |
| enable.idempotence   | false   | 是否开启幂等性功能                                                                              |
| interceptor.class    | ""      | 用来设定生产者拦截器，需要实现 org.apache.kafka.clients.producer.ProducerInterceptor 接口。         |
| max.in.flight.requests.per.connection    | 5  | 限制每个连接（也就是客户端与Node之间的链接）最多缓存的请求数。                           |
| metadata.max.age.ms  | 300000(5分钟)      | 如果在这个时间内元数据没有更新的话会被强制更新。                                            |
| transactional.id     | null     |  设置事务 id，必须唯一。 |
	
### 消息保障

Kafka 提供了多种消息保障机制。

#### ACK 机制

Kafka 像大多数消息中间件一样，提供了 ACK 机制，通过 acks 参数控制。
acks 是生产者客户端中的一个非常重要的参数，它用来指定分区中必须要有多少个副本收到这条消息，之后生产者才会认为这条消息是成功写入的。它涉及消息的可靠性和吞吐量之间的权衡。

acks 参数有三种类型的值（都是字符串类型）。

- `acks = 1`。默认值即为 1。生产者发送消息之后，只要分区的 leader 副本成功写入消息，那么它就会收到来自服务端的成功响应。如果消息无法写入 leader 副本，比如在 leader 副本崩溃、重新选举新的 leader 副本的过程中，那么生产者就会收到一个错误的响应，为了避免消息丢失，生产者可以选择重发消息。如果消息写入 leader 副本并返回成功响应给生产者，且在被其他 follower 副本拉取之前 leader 副本崩溃，那么此时消息还是会丢失，因为新选举的 leader 副本中并没有这条对应的消息。`acks` 设置为 1，是消息可靠性和吞吐量之间的折中方案。
- `acks = 0`。生产者发送消息之后不需要等待任何服务器端的响应。如果在消息从发送到写入 Kafka 的过程中出现了某些异常，导致 Kafka 并没有收到这条消息，那么生产者也无从得知，消息也就丢失了。在其他配置环境相同的情况下，`acks` 设置为 0 可以达到最大的吞吐量。
- `acks = -1 或 acks = all`。生产者在消息发送之后，需要等待 ISR 中的所有副本都成功写入消息之后才能够收到来自服务端的成功响应。在其他配置环境相同的情况下，`acks` 设置为 -1（all）可以达到最强的可靠性。但这并不意味着消息就一定可靠，因为 ISR 中可能只有 leader 副本，这样就退化成了 `acks = 1` 的情况。要获得更高的消息可靠性需要配合 `min.insync.replicas` 等参数的联动。另外，它还可能产生消息重复的问题，当所有 follower 副本完成同步并由 leader 副本汇聚结果响应客户端的时候，leader 副本挂了，生产者就会认为消息发送失败，重试而导致消息重复。

#### 幂等

为了保证消息的可靠性，我们一个保证消息送入且只送入一次主题分区中。

Kafka 0.11.0.0 版本引入了幂等语义。一个幂等性的操作就是一种被执行多次造成的影响和只执行一次造成的影响一样的操作。如果出现导致生产者重试的错误，同样的消息，仍由同样的生产者发送多次，
将只被写到 Kafka broker 的日志中一次。

对于单个分区，幂等生产者不会因为生产者或 broker 故障而产生多条重复消息。想要开启这个特性，获得每个分区内的精确一次语义，也就是说没有重复，没有丢失，并且有序的语义，
只需要 producer 配置 `enable.idempotence=true`。

当我们启用 `enable.idempotence=true`，`retries` 将默认为 Integer.MAX_VALUE，`acks` 将默认为 "all"。

##### 幂等实现

为了实现 Producer 的幂等性，Kafka 引入了 Producer ID（即 PID）和 Sequence Number。

- `PID`：每个新的 Producer 在初始化的时候会被分配一个唯一的 PID，这个 PID 对用户是不可见的。
- `Sequence Numbler`：（对于每个PID，该 Producer 发送数据的每个 <Topic, Partition> 都对应一个从 0 开始单调递增的 Sequence Number。

Kafka可能存在多个生产者，会同时产生消息，但对 Kafka 来说，只需要保证每个生产者内部的消息幂等就可以了，所有引入了 PID 来标识不同的生产者。

Kafka 通过为每条消息增加一个 Sequence Numbler，通过 Sequence Numbler 来区分每条消息。每条消息对应一个分区，不同的分区产生的消息不可能重复。所有 Sequence Numbler 对应每个分区 Broker 端在缓存中保存了这 Sequence Numbler，
对于接收的每条消息，如果其序号比 Broker 缓存中序号大于 1 则接受它，否则将其丢弃。这样就可以实现了消息重复提交了。但是，只能保证单个 Producer 对于同一个 <Topic, Partition> 的 Exactly Once 语义。
不能保证同一个 Producer 一个 topic 不同的 partion 的幂等。

#### 事务

对于多分区保证幂等的场景，则需要事务特性来处理了。kafka 的事务跟我们常见数据库事务概念差不多，也是提供经典的 ACID，即原子性（Atomicity）、一致性 (Consistency)、隔离性 (Isolation) 和持久性 (Durability)。

事务保证消息写入分区的原子性，即这批消息要么全部写入成功，要么全失败。此外，Producer 重启回来后，kafka 依然保证它们发送消息的精确一次处理。事务特性的配置也很简单：

- 和幂等一样，开启 `enable.idempotence = true`。
- 设置 Producer 端参数 `transctional.id`。

启用事务的 Producer 的代码稍微也有点不一样，需要调一些事务处理的 API。数据的发送需要放在 beginTransaction 和 commitTransaction 之间。Consumer 端的代码也需要加上 `isolation.level`参数，
用以处理事务提交的数据。示例代码:

```java
producer.initTransactions();
try {
     producer.beginTransaction();
     producer.send(record1);
     producer.send(record2);
     producer.commitTransaction();
} catch (KafkaException e) {
     producer.abortTransaction();
}
```

事务的更多内容，后续讲解（