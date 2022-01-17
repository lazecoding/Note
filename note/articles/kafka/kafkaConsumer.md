# 消费者

- 目录
    - [消费者和消费组](#消费者和消费组)
    - [消费流程](#消费流程)
      - [订阅主题与分区](#订阅主题与分区)
      - [反序列化](#反序列化)
      - [消息消费](#消息消费)
      - [位移消费](#位移消费)
      - [控制或关闭消费](#控制或关闭消费)
      - [指定位移消费](#指定位移消费)
      - [消费者拦截器](#消费者拦截器)
      - [再均衡](#再均衡)
    - [多线程设计](#多线程设计)
      - [一个消费线程对应一个 KafkaConsumer 实例](#一个消费线程对应一个-KafkaConsumer-实例)
      - [多个消费线程同时消费一个分区](#多个消费线程同时消费一个分区)
      - [单个消费者配合多线程的处理模块](#单个消费者配合多线程的处理模块)
    - [消费者参数](#消费者参数)
    - [消费者分区分配规则](#消费者分区分配规则)
      - [Range](#Range) 
      - [RoundRobin](#RoundRobin)
        - [消费者订阅的主题相同时](#消费者订阅的主题相同时)
        - [消费者订阅的主题不同时](#消费者订阅的主题不同时)
      - [Sticky](#Sticky)

与生产者对应的是消费者，应用程序可以通过 KafkaConsumer 来订阅主题，并从订阅的主题中拉取消息。

### 消费者和消费组

消费者（Consumer）负责订阅 Kafka 中的主题（Topic），并且从订阅的主题上拉取消息。与其他一些消息中间件不同的是：在 Kafka的 消费理念中还有一层消费组（Consumer Group）的概念，
每个消费者都有一个对应的消费组。当消息发布到主题后，只会被订阅它的每个消费组中的一个消费者所消费。

当一个主题被某个消费组中的多个消费者订阅，该主题中的分区只会被其中一个消费者绑定消费。当新增、减少消费者，主题会进入 `再均衡` 状态，分区与消费者的绑定会 `再分配`，整个过程消息是无法被消费的。

> 消费者与消费组这种模型可以让整体的消费能力具备横向伸缩性，我们可以增加（或减少）消费者的个数来提高（或降低）整体的消费能力。对于分区数固定的情况，一味地增加消费者并不会让消费能力一直得到提升，
> 如果消费者过多，出现了消费者的个数大于分区个数的情况，就会有消费者分配不到任何分区。

对于消息中间件而言，一般有两种模式：`点对点模式` 和 `发布订阅模式`。

- 点对点模式是基于队列的，消息生产者发送消息到队列，消息消费者从队列中接收消息。
- 发布订阅模式定义了如何向一个内容节点发布和订阅消息，这个内容节点称为主题（Topic），主题可以认为是消息传递的中介，消息发布者将消息发布到某个主题，而消息订阅者从主题中订阅消息。
  主题使得消息的订阅者和发布者互相保持独立，不需要进行接触即可保证消息的传递，发布/订阅模式在消息的一对多广播时采用。

Kafka 同时支持两种模式，而这正是得益于消费者与消费组模型的契合：

- 如果所有的消费者都隶属于同一个消费组，那么所有的消息都会被均衡地投递给每一个消费者，即每条消息只会被一个消费者处理，这就相当于点对点模式的应用。
- 如果所有的消费者都隶属于不同的消费组，那么所有的消息都会被广播给所有的消费者，即每条消息会被所有的消费者处理，这就相当于发布/订阅模式的应用。

消费组是一个逻辑上的概念，它将旗下的消费者归为一类，每一个消费者只隶属于一个消费组。每一个消费组都会有一个固定的名称，消费者在进行消费前需要指定其所属消费组的名称，
这个可以通过消费者客户端参数 `group.id` 来配置，默认值为空字符串。消费者并非逻辑上的概念，它是实际的应用实例，它可以是一个线程，也可以是一个进程。同一个消费组内的消费者既可以部署在同一台机器上，
也可以部署在不同的机器上。

### 消费流程

一个完整的消费流程需要具备以下几个步骤：

- 配置消费者客户端参数及创建响应的客户端实例。
- 订阅主题。
- 拉取消息并消费。
- 提交消费位移。
- 关闭消费者实例。

消费者客户端示例代码：

```java
public class Consumer {
    public static final String brokerList = "192.168.0.138:9092";
    public static final String topic = "topic-demo";
    public static final String group = "group-id";
    public static final String client = "client-id";

    public  static final AtomicBoolean isRunning = new AtomicBoolean(true);

    public static Properties initConfig(){

        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,brokerList);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG,group);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG,client);
        return  properties;
    }
    public static void main(String[] args) {

        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(initConfig());
        consumer.subscribe(Collections.singletonList(topic));
        try {
            while (isRunning.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                records.forEach(record->{
                    System.out.println("topic="+record.topic()+",  partition="+record.partition()+",  offset="+record.offset());
                    System.out.println("key="+record.key()+", value="+record.value());
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            consumer.close();
        }
    }
}
```

- `bootstrap.servers`：指定 Kafka 集群地址，可以设置一个或多个，用逗号隔开。注意这里不需要设置集群中全部的 broker地址，消费者会从现有的配置中查找全部的集群成员。
- `key.deserializer` 和 `value.deserializer`：与生产者客户端参数相对应。消费者从 Kafka获取到的消息格式都是字节数组(byte[])，所以需要执行相应的反序列化操作才能还原成原有的对象格式。
- `client.id`：客户端id，如果不设置，会自动生成一个非空字符串，内容形式为 consumer-1，consumer-2 这种格式。

#### 订阅主题与分区

我们需要为创建好的消费者订阅主题，一个消费者可以订阅一个或多个主题。可以通过 `subscribe()` 方法订阅主题，对于这个方法而言，既可以以集合的方式订阅多个主题，
也可以以正则表达式的形式订阅特定模式的主题，如下方法：

```java
void subscribe(Collection<String> var1);
void subscribe(Collection<String> var1, ConsumerRebalanceListener var2);
void assign(Collection<TopicPartition> var1);
void subscribe(Pattern var1, ConsumerRebalanceListener var2);
void subscribe(Pattern var1);
```

以集合的方式比较好理解，就是订阅一组主题。如果消费者采用的是正则表达式的方式订阅，在之后的创建过程中，如果有人又创建了新的主题，并且主题的名字与正则表达式相匹配，
那么这个消费者就可以消费到新添加的主题中的消息。如果应用程序需要消费多个主题，并且可以处理不同的类型，那么这种订阅方式就很有效。在kafka和其他系统之间进行数据赋值时，这种正则表达式的方式显得很常见,
正则表达式订阅的示例代码如下：

```java
consumer.subscribe(Pattern.compile("topic-.*"));
```

消费者不但可以订阅主题，还可以通过 `assign()` 方法直接订阅主题的特定分区。

```java
void assign(Collection<TopicPartition> partitions);
```

既然有订阅，那就有取消订阅，我们可以使用 `unsubscribe()` 方法取消订阅。

```java
consumer.unsubscribe();
```

> 通过 sbscribe() 方法订阅的主题具有消费者自动再均衡的功能，在多个消费者的情况下根据分区策略来自动分配各个消费者与分区的关系。当消费组内的消费者增加或减少时，分区分配关系会自动调整，
> 以实现消费负载均衡及故障自动转移。而通过 assign() 方法订阅分区时，是不具备消费者自动均衡的功能。

#### 反序列化

生产者需要用序列化器（Serializer）把对象转换成字节数组才能通过网络发送给 Kafka。而在对侧，消费者需要用反序列化器（Deserializer）把从 Kafka 中收到的字节数组转换成相应的对象。
客户端自带了 org.apache.kafka.common.serialization.StringSerializer，除了用于 String 类型的序列化器，还有 ByteArray、ByteBuffer、Bytes、Double、Integer、Long 这几种类型，
它们都实现了 org.apache.kafka.common.serialization.Serializer。

生产者使用的序列化器和消费者使用的反序列化器是需要一一对应的，如果生产者使用了某种序列化器，如 StringSerializer，而消费者使用了另一种序列化器，比如 IntegerSerializer，
那么是无法解析出想要的数据的。

但是，如无特殊需求，不建议使用自定义的序列化器和反序列化器，这样会增加消费者和生产者之间的耦合度。在实际应用中，在 Kafka 提供的序列化器和反序列化器满足不了应用需求的前提下，
推荐使用 Avro、JSON、Thrift、ProtoBuf 或 Protostuff 等通用的序列化工具来包装，以求尽可能实现得更加通用且前后兼容。使用通用的序列化工具也需要实现 Serializer 和 Deserializer 接口，
因为 Kafka 客户端的序列化和反序列化入口必须是这两个类型。

#### 消息消费

Kafka的消费是基于拉模式的。消息的消费一般有两种模式：push 模式和 pull 模式。推模式是服务器主动将消息推送给消费者，拉模式是消费者向服务端发送请求拉取消息。

kafka 遵循比较传统的设计，消费者从 broker pull 消息，一些日志中心的系统，比如 Scribe 和 Apache Flume ，采用非常不同的 push 模式（push 数据到下游）。
事实上，push 模式和 pull 模式各有优劣。push 模式很难适应消费速率不同的消费者，因为消息发送速率是由 broker 决定的。push 模式的目标是尽可能以最快速度传递消息，
但是这样很容易造成消费者来不及处理消息，典型的表现就是拒绝服务以及网络拥塞；而 pull 模式则可以根据消费者的消费能力以适当的速率消费消息。

基于 pull 模式的另一个优点是，它有助于积极的批处理的数据发送到消费者。基于 push 模式必须选择要么立即发送请求或者积累更多的数据，然后在不知道下游消费者是否能够立即处理它的情况下发送，
如果是低延迟，这将导致一次只发送一条消息，以便传输缓存，这是实在是一种浪费，基于 pull 的设计解决这个问题，消费者总是 pull 在日志的当前位置之后 pull 所有可用的消息（或配置一些大 size），
所以消费者可设置消费多大的量，也不会引起不必要的等待时间。

基于 pull 模式不足之处在于，如果 broker 没有数据，消费者会轮询，忙等待数据直到数据到达，为了避免这种情况，Kafka 允许消费者在 pull 请求时候使用 "long poll" 进行阻塞，
直到数据到达（并且设置等待时间的好处是可以积累消息，组成大数据块一并发送）。

kafka 将 topic 分为一组完全有序的分区，每个分区在任何给定的时间都由每个订阅消费者组中的一个消费者消费。这意味着消费者在每个分区中的位置只是一个整数，下一个消息消费的偏移量。
这使得关于已消费到哪里的状态变得非常的小，每个分区只有一个数字。可以定期检查此状态。这使得等同于消息应答并更轻量。

这么做有一个好处，消费者可以故意地回到旧的偏移量并重新消费数据。这违反了一个队列的共同契约，但这被证明是许多消费者的基本特征。 例如，如果消费者代码有 bug，并且在消费一些消息之后被发现，
消费者可以在修复错误后重新消费这些消息。

pull 方法具体定义如下：

```java
ConsumerRecords<K, V> poll(Duration timeout);
```

消费者消费到的每条消息类型为 ConsumerRecord，它的结构如下：

```java
public class ConsumerRecord<K, V> {
    public static final long NO_TIMESTAMP = -1L;
    public static final int NULL_SIZE = -1;
    public static final int NULL_CHECKSUM = -1;
    private final String topic; //主题
    private final int partition; //分区
    private final long offset; //所属分区偏移量
    private final long timestamp; //时间戳
    // 两种类型，CreateTime  和 LogAppendTime
    // 分别代表消息创建的时间，追加到日志的时间
    private final TimestampType timestampType;
    private final int serializedKeySize;// key 经过序列化后的大小，如果 key 为空，该值为 -1
    private final int serializedValueSize;// value 经过序列化后的大小，如果 value 为空，该值为 -1
    private final Headers headers;// 消息的头部内容
    private final K key;// 消息的键
    private final V value;// 消息的值
    private final Optional<Integer> leaderEpoch;
    private volatile Long checksum;// CRC32 的校验值
    // 部分省略
}
```

topic 和 partition 分别代表消息所属主题和所在分区，offset 表示消息在所属分区的偏移量。
timestamp 表示时间戳，与此对应的timestampType 表示时间戳的类型。timestampType 有两种类型：CreateTime 和LogAppendTime，分别代表消息创建的时间戳和消息追加到日志的时间戳。
headers 表示消息的头部内容。
key 和 value 分别表示消息的键和消息的值。
serializedKeySize 和 serializedValueSize 分别表示 key 和 value 经过序列化之后的大小，如果 key 为空，则 serializedKeySize 值为 -1。
同样，如果 value 为空，则 serializedValueSize 的值也会为 -1。
checksum 是 CRC32 的校验值。

`poll()` 方法的返回值类型是 ConsumerRecords，它用来表示一次拉取操作所获得的消息集合，内部包含了若干 ConsumerRecord，它提供了一个 `iterator()` 方法来循环遍历消息集合内部的消息，
我们使用这种方法来获取消息集中的每一个 ConsumerRecord。

```java
ConsumerRecords<K, V> poll(Duration timeout);
```

除此之外，我们还可以按照分区维度来进行消费，这一点很有用，在手动提交位移时尤为明显。ConsumerRecords 类提供了一个 `records(TopicPartition)` 方法来获取消息集合中指定分区的消息，
ConsumerRecords 类中并没提供与 `partitions()` 类似的 `topics()` 方法来查看拉取的消息集合中所包含的主题列表，如果要按照主题维度来进行消费，那么只能根据消费者订阅主题时的列表来进行逻辑处理。

```java
public List<ConsumerRecord<K, V>> records(TopicPartition partition) {
    List<ConsumerRecord<K, V>> recs = this.records.get(partition);
    if (recs == null)
        return Collections.emptyList();
    else
        return Collections.unmodifiableList(recs);
}
```

#### 位移消费

对于 Kafka 的分区而言，它的每条消息都有唯一的 offset，用来表示消息在分区中对应的位置。消费者使用 offset 来表示消费到分区中某个消息所在的位置。offset，顾名思义，偏移量，也可翻译为位移。
在每次调用 `poll()` 方法时，它返回的是还没有消费过的消息集，要做到这一点，就需要记录上一次消费过的位移。并且这个位移必须做持久化保存，而不是单单保存在内存中，
否则消费者重启之后就无法知道之前的消费位移了。

当加入新的消费者的时，必然会有 `再均衡` 的动作，对于同一分区而言，它可能在再均衡动作之后分配给新的消费者，如果不持久化保存消费位移，那么这个新的消费者也无法知道之前的消费位移。
消费者位移存储在 Kafka 内部的主题 _consumer_offsets 中。

这种把消费位移存储起来(持久化)的动作称为 `提交`，消费者在消费完消息之后需要执行消费位移的提交。

假设当前消费者已经消费了 x 位置的消息，那么我们就可以说消费者的消费位移为 x。不过，需要明确的是，当前消费者需要提交的消费位移并不是 x，而是 x+1，它表示下一条需要拉取的消息的位置。
在消费者中还有一个 commited offset 的概念，它表示已经提交过的消费位移。

KafkaConsumer 类提供了 `position(TopicPartition)` 和 `commited(TopicPartition)` 两个方法来分别获取上面所说的 position 和 commiited offset 的值。

在 kafka 消费的编程逻辑中位移是一大难点，自动提交消费位移的方式非常简便，它免去了复杂的位移提交逻辑，让代码更简洁。但随之而来的是重复消费和消费丢失的问题。假设刚提交完一次消费位移，
然后拉取一批消息进行消费，在下一次自动提交消费位移之前，消费者崩溃了，那么又得从上一次位移提交的地方重新开始消费。我们可以通过减少位移提交的时间间隔来减少重复消息的窗口大小，
但这样并不能避免重复消费的发送，而且也会使位移提交更加频繁。

自动位移提交的方式在正常情况下不会发生消息丢失和重复消费的现象，但是在编程的世界里异常不可避免。自动提交无法做到精确的位移管理。Kafka 提供了手动管理位移提交的操作，这样可以使开发人员对消费位移的管理控制更加灵活。
很多时候并不是说 poll 拉取到消息就算消费完成，而是需要将消息写入到数据库、写入本地缓存，或者是更加复杂的业务处理。在这些场景下，所有的业务处理完成才能认为消息被成功消费，
手动的提交方式让开发人员根据程序的逻辑在合适的地方进行位移提交。

手动提交需要将 `enable.auto.commit` 配置为 false，手动提交分为同步提交和异步提交，对应于 KafkaConsumer 中的 commitSync 和 commitAsync 两个方法。

commitSync 方法会根据 poll 拉取到的最新位移来进行提交，即 position 的位置，只要没有发生不可恢复的错误，它就会阻塞消费者线程直至位移提交完成。对于不可恢复的错误，
如 CommitFailedException/WakeupException/InterruptException/AuthenticationException/AuthorizationException 等，我们可以将其捕获并做针对性的处理。

与 commitSync 相反，异步提交的方式 commitAsync 在执行的时候，消费者线程不会阻塞，可能在提交消费位移的结果返回之前就开始了新一轮的拉取操作，消费者的性能增强。

#### 控制或关闭消费

KafkaConsumer 提供了对消费速度进行控制的方法，有些场景，需要我们暂停某些分区的消费而先消费其他分区，当达到一定条件时再恢复这些分区的消费。
`pause()` 和 `resume()` 方法来分别实现暂停某些分区在拉取操作时返回数据给客户端和恢复某些分区向客户端返回数据的操作。

```java
void pause(Collection<TopicPartition> var1);
void resume(Collection<TopicPartition> var1);
```

还有一个无参的 `paused()` 方法返回被暂停的分区集合。

```java
Set<TopicPartition> paused();
```

#### 指定位移消费

正是有了消费位移的持久化，才使消费者在关闭、崩溃或者遇到再均衡的时候，可以让接替的消费者能够根据存储的消费位移继续进行消费。

当一个新的消费组建立的时候，它根本没有可以查找的消费位移。或者消费组内的一个新消费者订阅了一个新的主题，它也没有可以查找的消费位移。

当消费者查找不到所记录的消费位移的时候，就会根据消费者客户端参数 `auto.offset.reset` 的配置来决定从何处开始进行消费，这个参数的默认值为 `latest`，表示从分区末尾开始消费。
如果将 `auto.offset.reset` 设置成 `earliest`，那么消费者会从起始处，也就是 0 开始消费。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/kafka/位移消费-reset.png" width="600px">
</div>

有些场景我们需要更细粒度的掌控消息，我们需要从特定的位移处开始拉取消息，`seek()` 方法提供了这个功能，让我们得以追前消费或回溯消费。

```java
void seek(TopicPartition var1, long offset);
```

`seek()` 方法中的参数 partition 表示分区，而 offset 参数用来指定从分区的哪个位置开始消费。`seek()` 方法只能重置消费者分配到的分区的消费位置，
而分区的分配是在 `poll()` 方法的调用过程中实现的。也就是说在执行 `seek()` 方法之前需要先执行一次 `poll()` 方法，等到分配到分区之后才可以重置消费位置。

```java
// consumer.seek(tp,2) 设置每个分区消费的位置是 2。
consumer.poll(Duration.ofMillis(10000));
Set<TopicPartition> assignment = consumer.assignment();
for(TopicPartition tp : assignment){
    consumer.seek(tp,2);
}
while (true){
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(Long.MAX_VALUE));
    //  .....
}
```

#### 消费者拦截器

消费者拦截器主要在消费到消息或在提交消费位移的时候进行一些定制化的工作。

消费者拦截器需要实现 ConsumerInterceptor 接口，该接口有三个方法:

```java
public interface ConsumerInterceptor<K, V> extends Configurable, AutoCloseable {
    ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> var1);
    void onCommit(Map<TopicPartition, OffsetAndMetadata> var1);
    void close();
}
```

KafkaConsumer 会在 `poll()` 方法返回之前调用拦截器的 `onConsume()` 方法来对消息进行相应的定制化操作，比如修改返回的内容、按照某种规则过滤消息。
如果 `onConsume()` 方法抛出异常，那么会被捕获并记录到日志，但是异常不会在向上传递。

KafkaConsumer 会在提交完消费位移之后调用调用拦截器的 `onCommit()` 方法，可以使用这个方法来记录跟踪所提交的位移信息，比如当消费者调用 `commitSync()` 的无参方法时，
我们不知道提交的具体细节，可以使用拦截器 `onCommit()` 方法做到这一点。

自定义拦截器实现后，需要在 KafkaConsumer 中配置该拦截器，通过参数 `interceptor.classes` 参数实现：

```java
properties.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, ConsumerInterceptorTTL.class);
```

#### 再均衡

再均衡是指分区的所属权从一个消费者转移到另一消费者的行为，它为消费组具备高可用性和伸缩性提供保障，使我们可以既方便又安全地删除消费组内的消费者或往消费组内添加消费者。
不过在再均衡发生期间，消费组内的消费者是无法读取消息的。也就是说，在再均衡发生期间的这一小段时间内，消费组会变得不可用。

另外，当一个分区被重新分配给另一个消费者时，消费者当前的状态也会丢失。比如消费者消费完某个分区中的一部分消息时还没有来得及提交消费位移就发生了再均衡操作，
之后这个分区又被分配给了消费组内的另一个消费者，原来被消费完的那部分消息又被重新消费一遍，也就是发生了重复消费。一般情况下，应尽量避免不必要的再均衡的发生。

讲述 `subscribe()` 方法时提及 ConsumerRebalanceListener（再均衡监听器），在 `subscribe(Collection topics, ConsumerRebalanceListener listener)`
和 `subscribe(Pattern pattern, ConsumerRebalanceListener listener)` 方法中都有它的身影，ConsumerRebalanceListener 用来设定发生再均衡动作前后的一些准备或收尾的动作。
ConsumerRebalanceListener 是一个接口，包含 2 个方法，具体的释义如下：

- `void onPartitionsRevoked(Collection partitions)` 这个方法会在再均衡开始之前和消费者停止读取消息之后被调用。
  可以通过这个回调方法来处理消费位移的提交，以此来避免一些不必要的重复消费现象的发生。参数 partitions 表示再均衡前所分配到的分区。
- `void onPartitionsAssigned(Collection partitions)` 这个方法会在重新分配分区之后和消费者开始读取消费之前被调用。参数 partitions 表示再均衡后所分配到的分区。

下面通过一个例子来演示 ConsumerRebalanceListener 的用法:

```java
// 再均衡监听器的用法
Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();
consumer.subscribe(Arrays.asList(topic), new ConsumerRebalanceListener() {
    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        consumer.commitSync(currentOffsets);
	        currentOffsets.clear();
    }
    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        //do nothing.
    }
});

try {
    while (isRunning.get()) {
        ConsumerRecords<String, String> records =
                consumer.poll(Duration.ofMillis(100));
        for (ConsumerRecord<String, String> record : records) {
            //process the record.
            currentOffsets.put(
                    new TopicPartition(record.topic(), record.partition()),
                    new OffsetAndMetadata(record.offset() + 1));
        }
        consumer.commitAsync(currentOffsets, null);
    }
} finally {
    consumer.close();
}
```

示例代码中，消费位移被暂存到一个局部变量 currentOffsets 中，这样在正常消费的时候可以通过 `commitAsync()` 方法来异步提交消费位移，
在发生再均衡动作之前可以通过再均衡监听器的 `onPartitionsRevoked()` 回调执行 `commitSync()` 方法同步提交消费位移，以尽量避免一些不必要的重复消费。

### 多线程设计

KafkaProducer 是线程安全的，然而 KafkaConsumer 是非线程安全的。KafkaConsumer 当中定义了一个 `acquire()` 方法，用来检测当前是否只有一个线程在操作，
若有其他线程正在操作则会抛出 Concurrentmodifcationexception 异常。KafkaConsumer中的每个公用方法在执行所要执行的动作之前都会调用这个方法，只有 wakeup() 方法是个例外。

```C
java.util.ConcurrentModificationException: KafkaConsumer is not safe for multi-threaded access.
```

KafkaConsumer 非线程安全并不意味着我们在消费消息的时候只能以单线程的方式运行。如果生产者发送消息的速度大于消费者处理消息的速度，那么就会有越来越多的消息得不到及时的处理，造成一定的时延。
除此之外，kafka 中存在消息保留机制，有些消息有可能在被消费之前就被清理了，从而造成消息的丢失。我们可以通过多线程的方式实现消息消费，多线程的目的就是提高整体的消费能力。

#### 一个消费线程对应一个 KafkaConsumer 实例

多线程的实现方式有多种，第一种也是最常见的方式：线程封闭，即为每个线程实例化一个 KafkaConsumer 对象，启动多个消费线程。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/kafka/一个线程对应一个KafkaConsumer实例.png" width="600px">
</div>

示例代码：

```java
/**
 * 一个消费线程对应一个 KafkaConsumer 实例
 * 
 * 创建多个消费线程
 */
public class FirstMultiConsumerThreadDemo {

    public static final String brokerList = "nas-cluster1:9092";
    public static final String topic = "test.topic";
    public static final String groupId = "group.demo";

    public static Properties initConfig() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        return properties;
    }

    public static void main(String[] args) {
        Properties props = initConfig();
        int consumerThreadNum = 4;
        for (int i = 0; i < consumerThreadNum; i++) {
            // 启动多个 KafkaConsumer 实例
            new KafkaConsumerThread(props, topic).start();
        }
    }

    public static class KafkaConsumerThread extends Thread {

        private KafkaConsumer<String, String> kafkaConsumer;

        public KafkaConsumerThread(Properties props, String topic) {
            this.kafkaConsumer = new KafkaConsumer<>(props);
            this.kafkaConsumer.subscribe(Arrays.asList(topic));
        }

        @Override
        public void run() {
            try {
                while (true) {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        //实现处理逻辑
                        System.out.println(record.value());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                kafkaConsumer.close();
            }
        }
    }
}
```

内部类 KafkaConsumerThread 代表消费线程,其内部包裹着一个独立的 KafkaConsumer 实例。通过外部类的 main 方法来启动多个消费线程,消费线程的数量由 consumerThreadNum 变量指定。
一般一个主题的分区数事先可以知晓,可以将 consumerThreadNum 设置成不大于分区数的值,如果不知道主题的分区数,那么也可以通过 KafkaConsumer 类的 `partitionsFor()` 方法来间接获取，
进而再设置合理的 consumerThreadNum 值。

####  多个消费线程同时消费一个分区

`多个消费线程同时消费一个分区` 和 `一个消费线程对应一个 KafkaConsumer 实例，启动多个消费线程` 没有本质上的区别，它的优点是每个线程可以按顺序消费各个分区中的消息。
缺点也很明显，每个消费线程都要维护一个独立的 TCP 连接，如果分区数和 consumerThreadNum 的值都很大,那么会造成不小的系统开销。

#### 单个消费者配合多线程的处理模块

通常，一个消息的消费是从 `poll()` 拉取到消息开始，到完成消息的业务处理结束，然后再 `poll()` 拉取新的消息，反复进行。我们可以把这个过程分为 `poll 阶段` 和 `hander 阶段`，
显而易见的是，如果`hander 阶段` 阶段处理迅速，那面 `poll 阶段` 就可以保证较高的消费水平；反之，如果 `hander 阶段` 阶段处理缓慢必然拉低 `poll 阶段` 的消费速度。

可以说 `hander 阶段` 的处理速度将称为 `poll 阶段` 的瓶颈。

`poll 阶段` 本身并没有什么业务，甚至和 `hander 阶段` 也没用业务关联，因此我们可以将 `poll 阶段` 和 `hander 阶段` 解耦：单线程的 KafkaConsumer 处理 `poll 阶段` 和消息分发，
HanderThreadPool 处理 `hander 阶段`。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/kafka/单个消费者配合多线程的处理模块.png" width="600px">
</div>

示例代码：

```java
/**
 * 单个消费者配合多线程的处理模块
 */
public class ThirdMultiConsumerThreadDemo {
    
    public static final String brokerList = "nas-cluster1:9092";
    public static final String topic = "test.topic";
    public static final String groupId = "group.demo";

    public static Properties initConfig() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        return properties;
    }

    public static void main(String[] args) {
        Properties properties = initConfig();
        KafkaConsumerThread consumerThread = new KafkaConsumerThread(properties, topic,
                Runtime.getRuntime().availableProcessors());
        consumerThread.start();
    }

    public static class KafkaConsumerThread extends Thread {
        private KafkaConsumer<String, String> kafkaConsumer;
        private ExecutorService executorService;
        private int threadNumber;

        public KafkaConsumerThread(Properties properties, String topic, int availableProcessors) {
            kafkaConsumer = new KafkaConsumer<String, String>(properties);
            kafkaConsumer.subscribe(Collections.singletonList(topic));
            this.threadNumber = availableProcessors;
            executorService = new ThreadPoolExecutor(threadNumber, threadNumber, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1000), new ThreadPoolExecutor.CallerRunsPolicy());
        }

        @Override
        public void run() {
            try {
                while (true) {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
                    if (!records.isEmpty()) {
                        executorService.submit(new RecordsHandler(records));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                kafkaConsumer.close();
            }
        }
    }

    public static class RecordsHandler extends Thread {
        public final ConsumerRecords<String, String> records;

        public RecordsHandler(ConsumerRecords<String, String> records) {
            this.records = records;
        }

        @Override
        public void run() {
            for (ConsumerRecord<String, String> record : records) {
                //实现处理逻辑
                System.out.println(record.value());
            }
        }
    }
}
```

代码中 RecordsHandler 类是用来处理消息的,而 KafkaConsumerThread 类对应的是一个消费线程,里面通过线程池的方式来调用 RecordsHandler 处理一批批的消息。
注意 KafkaConsumerThread 类中 ThreadPoolExecutor 里的最后一个参数设置的是拒绝策略, 这样可以防止线程池的总体消费能力跟不上 `poll()` 拉取的能力,从而导致异常现象的发生。

第三种实现方式不仅可以横向扩展，通过开启多个 KafkaConsumerthread 实例来进一步提升整体的消费能力，而且减少了 TCP 连接数量；缺点就是很难对消息进行顺序处理。

### 消费者参数

在 KafkaConsumer 中，大部分的参数都有合理的默认值，一般不需要修改它们。不过了解这些参数可以更合理地使用消费者客户端，其中还有一些重要的参数涉及程序的可用性和性能，如果能够熟练掌握它们，也可以让我们在编写相关的程序时能够更好地进行性能调优与故障排除。

#### fetch.min.bytes

该属性指定了消费者从服务器获取记录的最小字节数。broker 在收到消费者的数据请求时，如果可用的数据量小于 `fetch.min.bytes` 指定的大小，那么它会等到有足够的可用数据时才把它返回给消费者。
这样可以降低消费者和 broker 的工作负载，因为它们在主题不是很活跃的时候(或者一天里的低谷时段)就不需要来来回回地处理消息。如果没有很多可用数据，但消费者的 CPU 使用率却很高，
那么就需要把该属性的值设得比默认值大。如果消费者的数量比较多，把该属性的值设置得大一点可以降低 broker 的工作负载。

#### fetch.max.wait.ms

我们通过 `fetch.min.bytes` 告诉 Kafka，等到有足够的数据时才把它返回给消费者。而 `fetch.max.wait.ms` 则用于指定 broker 的等待时间，默认是 500ms。如果没有足够的数据流入 Kafka，
消费者获取最小数据量的要求就得不到满足，最终导致 500ms 的延迟。 如果要降低潜在的延迟(为了满足 SLA)，可以把该参数值设置得小一些。如果 `fetch.max.wait.ms` 被设为 100ms，
并且 `fetch.min.bytes` 被设为 1MB，那么 Kafka 在收到消费者的请求后，要么返回 1MB 数据，要么在 100ms 后返回所有可用的数据，就看哪个条件先得到满足。

#### max.parition.fetch.bytes

该属性指定了服务器从每个分区里返回给消费者的最大字节数。它的默认值是 1MB，也就是说，`KafkaConsumer.poll()` 方法从每个分区里返回的记录最多不超过 `max.parition.fetch.bytes` 指定的字节。
如果一个主题有 20 个分区和 5 个消费者，那么每个消费者需要至少 4MB 的可用内存来接收记录。在为消费者分配内存时，可以给它们多分配一些，因为如果群组里有消费者发生崩溃，剩下的消费者需要处理更多的分区。
`max.parition.fetch.bytes` 的值必须比 broker 能够接收的最大消息的字节数(通过 `max.message.size` 属性配置)大，否则消费者可能无法读取这些消息，导致消费者一直挂起重试。
在设置该属性时，另一个需要考虑的因素是消费者处理数据的时间。消费者需要频繁调用 poll() 方法来避免会话过期和发生分区再均衡，如果单次调用 poll() 返回的数据太多，消费者需要更多的时间来处理，
可能无法及时进行下一个轮询来避免会话过期。如果出现这种情况，可以把 `max.parition.fetch.bytes` 值改小，或者延长会话过期时间。

#### session.timeout.ms

该属性指定了消费者在被认为死亡之前可以与服务器断开连接的时间，默认是 3s。如果消费者没有在 `session.timeout.ms` 指定的时间内发送心跳给群组协调器，就被认为已经死亡，协调器就会触发再均衡，
把它的分区分配给群组里的其他消费者。该属性与 `heartbeat.interval.ms` 紧密相关。`heartbeat.interval.ms` 指定了 poll() 方法向协调器发送心跳的频率，
`session.timeout.ms` 则指定了消费者可以多久不发送心跳。所以，一般需要同时修改这两个属性，`heartbeat.interval.ms` 必须比 `session.timeout.ms` 小，
一般是 `session.timeout.ms` 的三分之一。如果 `session.timeout.ms` 是 3s，那么 `heartbeat.interval.ms` 应该是 ls。把 `session.timeout.ms` 值设得比默认值小，
可以更快地检测和恢 复崩溃的节点，不过长时间的轮询或垃圾收集可能导致非预期的再均衡。把该属性的值设置得大一些，可以减少意外的再均衡 ，不过检测节点崩溃需要更长的时间。

#### auto.offset.reset

该属性指定了消费者在读取一个没有偏移量的分区或者偏移量无效的情况下(因消费者长时间失效，包含偏移量的记录已经过时井被删除)该作何处理。它的默认值是 latest，意思是说，在偏移量无效的情况下，
消费者将从最新的记录开始读取数据(在消费者启动之后生成的记录)。另一个值是 earliest，意思是说，在偏移量无效的情况下，消费者将从 起始位置读取分区的记录。

#### enable.auto.commit

该属性指定了消费者是否自动提交偏移量，默认值是 true。为了尽量避免出现重复数据和数据丢失，可以把它设为 false，由自己控制何时提交偏移量。如果把它设为 true，
还可以通过配置 `auto.commit.interval.mls` 属性来控制提交的频率。

#### partition.assignment.strategy

我们知道，分区会被分配给群组里的消费者。 PartitionAssignor 根据给定的消费者和主题，决定哪些分区应该被分配给哪个消费者。

Kafka 有两个默认的分配策略：

- Range：该策略会把主题的若干个连续的分区分配给消费者。假设消费者 C1 和消费者 C2 同时 订阅了主题 T1 和主题 T2，井且每个主题有 3 个分区。
那么消费者 C1 有可能分配到这两个主题的分区 0 和 分区 1，而消费者 C2 分配到这两个主题 的分区 2。因为每个主题 拥有奇数个分区，而分配是在主题内独立完成的，
第一个消费者最后分配到比第二个消费者更多的分区。只要使用了 Range 策略，而且分区数量无法被消费者数量整除，就会出现这种情况。
- RoundRobin：该策略把主题的所有分区逐个分配给消费者。如果使用 RoundRobin 策略来给消费者 C1 和消费者 C2分配分区，那么消费者 C1 将分到主题 T1 的分区 0和分区 2以及主题 T2 的分区 1，
消费者 C2 将分配到主题 T1 的分区 l 以及主题T2 的分区 0 和分区 2。一般 来说，如果所有消费者都订阅相同的主题(这种情况很常见), 
RoundRobin 策略会给所有消费者分配相同数量的分区(或最多就差一个分区)。

可以通过设置 `partition.assignment.strategy` 来选择分区策略。默认使用的是 `org. apache.kafka.clients.consumer.RangeAssignor`，这个类实现了 Range 策略，
也可以把它改成 `org.apache.kafka.clients.consumer.RoundRobinAssignor`，当然我们还可以使用自定义策略，在这种情况下 ，`partition.assignment.strategy` 属性的值就是自定义类的名字。

#### client.id

该属性可以是任意字符串，broker 用它来标识从客户端发送过来的消息，通常被用在日志、度量指标和配额里。

#### max.poll.records

该属性用于控制单次调用 call() 方法能够返回的记录数量，可以帮你控制在轮询里需要处理的数据量。

#### receive.buffer.bytes 和 send.buffer.bytes

Socket 在读写数据时用到的 TCP 缓冲区也可以设置大小。如果它们被设为 -1，就使用操作系统的默认值。如果生产者或消费者与 broker 处于不同的数据中心内，可以适当增大这些值，
因为跨数据中心的网络一般都有比较高的延迟和比较低的带宽。


### 消费者分区分配规则

Kafka 有两个默认的分配策略：Range、RoundRobin 和 Sticky。顾名思义：Range 是范围，RoundRobin 是轮询，Sticky 是粘性？

我们可以通过设置 `partition.assignment.strategy` 来选择分区策略。默认使用的是 `org. apache.kafka.clients.consumer.RangeAssignor`，这个类实现了 Range 策略，
也可以把它改成 `org.apache.kafka.clients.consumer.RoundRobinAssignor` 和 `org.apache.kafka.clients.consumer.StickyAssignor`，当然我们还可以使用自定义策略，在这种情况下 ，`partition.assignment.strategy` 属性的值就是自定义类的名字。

当出现以下几种情况时，kafka 会进行一次分区分配操作:

- 同一个 Consumer Group 内新增了消费者。
- 消费者离开当前所属的 Consumer Group，比如主动停机或者宕机。
- topic 新增了分区（也就是分区数量发生了变化）。

#### Range

Range 策略是针对 topic 而言的，在进行分区分配时，为了尽可能保证所有 Consumer 均匀的消费分区，会对同一个 topic 中的 partition 按照序号排序，并对 Consumer 按照字典顺序排序。
然后为每个 Consumer 划分固定的分区范围，如果不够平均分配，那么排序靠前的消费者会被多分配分区。具体就是将 partition 的个数除于 Consumer 线程数来决定每个 Consumer 线程消费几个分区。
如果除不尽，那么前面几个消费者线程将会多分配分区。

通过下面公式更直观：

> 假设n = 分区数 / 消费者数量，m = 分区数 % 消费者线程数量，那么前m个消费者每个分配n+1个分区，后面的（消费者线程数量 - m）个消费者每个分配n个分区。

RangeAssignor 源码：

```java

/**
 * <p>The range assignor works on a per-topic basis. For each topic, we lay out the available partitions in numeric order
 * and the consumers in lexicographic order. We then divide the number of partitions by the total number of
 * consumers to determine the number of partitions to assign to each consumer. If it does not evenly
 * divide, then the first few consumers will have one extra partition.
 *
 * <p>For example, suppose there are two consumers <code>C0</code> and <code>C1</code>, two topics <code>t0</code> and
 * <code>t1</code>, and each topic has 3 partitions, resulting in partitions <code>t0p0</code>, <code>t0p1</code>,
 * <code>t0p2</code>, <code>t1p0</code>, <code>t1p1</code>, and <code>t1p2</code>.
 *
 * <p>The assignment will be:
 * <ul>
 * <li><code>C0: [t0p0, t0p1, t1p0, t1p1]</code></li>
 * <li><code>C1: [t0p2, t1p2]</code></li>
 * </ul>
 */
public class RangeAssignor extends AbstractPartitionAssignor {

    @Override
    public String name() {
        return "range";
    }

    private Map<String, List<String>> consumersPerTopic(Map<String, Subscription> consumerMetadata) {
        Map<String, List<String>> res = new HashMap<>();
        for (Map.Entry<String, Subscription> subscriptionEntry : consumerMetadata.entrySet()) {
            String consumerId = subscriptionEntry.getKey();
            for (String topic : subscriptionEntry.getValue().topics())
                put(res, topic, consumerId);
        }
        return res;
    }

    @Override
    public Map<String, List<TopicPartition>> assign(Map<String, Integer> partitionsPerTopic,
                                                    Map<String, Subscription> subscriptions) {
        Map<String, List<String>> consumersPerTopic = consumersPerTopic(subscriptions);
        Map<String, List<TopicPartition>> assignment = new HashMap<>();
        for (String memberId : subscriptions.keySet())
            assignment.put(memberId, new ArrayList<>());

        for (Map.Entry<String, List<String>> topicEntry : consumersPerTopic.entrySet()) {
            String topic = topicEntry.getKey();
            List<String> consumersForTopic = topicEntry.getValue();

            Integer numPartitionsForTopic = partitionsPerTopic.get(topic);
            if (numPartitionsForTopic == null)
                continue;

            Collections.sort(consumersForTopic);

            int numPartitionsPerConsumer = numPartitionsForTopic / consumersForTopic.size();
            int consumersWithExtraPartition = numPartitionsForTopic % consumersForTopic.size();

            List<TopicPartition> partitions = AbstractPartitionAssignor.partitions(topic, numPartitionsForTopic);
            for (int i = 0, n = consumersForTopic.size(); i < n; i++) {
                int start = numPartitionsPerConsumer * i + Math.min(i, consumersWithExtraPartition);
                int length = numPartitionsPerConsumer + (i + 1 > consumersWithExtraPartition ? 0 : 1);
                assignment.get(consumersForTopic.get(i)).addAll(partitions.subList(start, start + length));
            }
        }
        return assignment;
    }

}
```

例如，假设有两个消费者 C0 和 C1，两个主题 t0 和 t1，每个主题有 3 个分区，导致分区 t0p0、t0p1、t0p2、t1p0、t1p1 和 t1p2。

分配结果可能是：

| C0: [t0p0, t0p1, t1p0, t1p1]  |
| ----------------------------- |
| C1: [t0p2, t1p2]              |

可以明显的看到这样的分配并不均匀，如果将类似的情形扩大，有可能会出现部分消费者过载的情况，这就是 Range 分区策略的一个很明显的弊端。

#### RoundRobin

RoundRobin 策略的工作原理：RoundRobinAssignor 会列出所有可用分区和消费者，以轮询的方式给消费者分配分区。

RoundRobinAssignor 源码：

```java
/**
 * The round robin assignor lays out all the available partitions and all the available consumers. It
 * then proceeds to do a round robin assignment from partition to consumer. If the subscriptions of all consumer
 * instances are identical, then the partitions will be uniformly distributed. (i.e., the partition ownership counts
 * will be within a delta of exactly one across all consumers.)
 *
 * For example, suppose there are two consumers C0 and C1, two topics t0 and t1, and each topic has 3 partitions,
 * resulting in partitions t0p0, t0p1, t0p2, t1p0, t1p1, and t1p2.
 *
 * The assignment will be:
 * C0: [t0p0, t0p2, t1p1]
 * C1: [t0p1, t1p0, t1p2]
 *
 * When subscriptions differ across consumer instances, the assignment process still considers each
 * consumer instance in round robin fashion but skips over an instance if it is not subscribed to
 * the topic. Unlike the case when subscriptions are identical, this can result in imbalanced
 * assignments. For example, we have three consumers C0, C1, C2, and three topics t0, t1, t2,
 * with 1, 2, and 3 partitions, respectively. Therefore, the partitions are t0p0, t1p0, t1p1, t2p0,
 * t2p1, t2p2. C0 is subscribed to t0; C1 is subscribed to t0, t1; and C2 is subscribed to t0, t1, t2.
 *
 * That assignment will be:
 * C0: [t0p0]
 * C1: [t1p0]
 * C2: [t1p1, t2p0, t2p1, t2p2]
 */
public class RoundRobinAssignor extends AbstractPartitionAssignor {

    @Override
    public Map<String, List<TopicPartition>> assign(Map<String, Integer> partitionsPerTopic,
                                                    Map<String, Subscription> subscriptions) {
        Map<String, List<TopicPartition>> assignment = new HashMap<>();
        for (String memberId : subscriptions.keySet())
            assignment.put(memberId, new ArrayList<>());

        CircularIterator<String> assigner = new CircularIterator<>(Utils.sorted(subscriptions.keySet()));
        for (TopicPartition partition : allPartitionsSorted(partitionsPerTopic, subscriptions)) {
            final String topic = partition.topic();
            while (!subscriptions.get(assigner.peek()).topics().contains(topic))
                assigner.next();
            assignment.get(assigner.next()).add(partition);
        }
        return assignment;
    }

    public List<TopicPartition> allPartitionsSorted(Map<String, Integer> partitionsPerTopic,
                                                    Map<String, Subscription> subscriptions) {
        SortedSet<String> topics = new TreeSet<>();
        for (Subscription subscription : subscriptions.values())
            topics.addAll(subscription.topics());

        List<TopicPartition> allPartitions = new ArrayList<>();
        for (String topic : topics) {
            Integer numPartitionsForTopic = partitionsPerTopic.get(topic);
            if (numPartitionsForTopic != null)
                allPartitions.addAll(AbstractPartitionAssignor.partitions(topic, numPartitionsForTopic));
        }
        return allPartitions;
    }

    @Override
    public String name() {
        return "roundrobin";
    }

}
```

使用 RoundRobin 策略一般满足以下条件：

- 同一个 Consumer Group 里面的所有 Consumer 的 num.streams 必须相等。
- 每个 Consumer 订阅的 topic 必须相同。

##### 消费者订阅的主题相同时

当 Consumer Group 里面的所有 Consumer 订阅相同时，分配可以均匀分配，最多差异 1 个。

假设有两个消费者 C0 和 C1，两个主题 t0 和 t1，每个主题有 3 个分区，导致分区 t0p0、t0p1、t0p2、t1p0、t1p1 和 t1p2。

分配结果可能是：

| C0: [t0p0, t0p2, t1p1]  |
| ----------------------- |
| C1: [t0p1, t1p0, t1p2]  |

##### 消费者订阅的主题不同时

当 Consumer Group 里面的 Consumer 订阅存在差异，分配过程仍然以轮询方式考虑每个使用者实例，但如果某个实例没有订阅该主题，则会跳过该实例。

例如，我们有三个消费者 C0、C1、C2 和三个主题 t0、t1、t2，分别有 1、2 和 3 个分区。因此，分区为 t0p0、t1p0、t1p1、t2p0、t2p1、t2p2。
`C0 订阅了 t0`;`C1 订阅了 t0、t1`;`C2 订阅了 t0、t1、t2`。

分配结果可能是：

| C0: [t0p0]                    |
| ----------------------------- |
| C1: [t1p0]                    |
| C2: [t1p1, t2p0, t2p1, t2p2]  |

#### Sticky

Sticky 有两个目的。

- 首先，`它保证分配尽可能平衡`，这意味着:分配给消费者的主题分区的数量最多相差一个；或每个消费者的主题分区比其他消费者少 2，就不能将这些主题分区转移到它身上。
- 其次，当发生重新赋值时，`它尽可能多地保留现有的赋值`，即分区的分配尽可能的与上次分配的保持相同。当主题分区从一个消费者转移到另一个消费者时，这有助于节省一些处理开销。

当两者发生冲突时，第一个目标优先于第二个目标。鉴于这两个目标，StickyAssignor 分配策略的具体实现要比 RangeAssignor 和 RoundRobinAssignor 这两种分配策略要复杂得多，
但从结果上看 StickyAssignor 策略比另外两者分配策略而言显得更加优异。

StickyAssignor 源码太长，自己看去...
