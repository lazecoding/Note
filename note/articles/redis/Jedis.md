# Jedis

- 目录
    - [JedisPool](#JedisPool)
    - [JedisPoolConfig](#JedisPoolConfig)
    - [minEvictableIdleTimeMillis 和 softMinEvictableIdleTimeMillis](#minEvictableIdleTimeMillis-和-softMinEvictableIdleTimeMillis)
    - [创建线程池](#创建线程池)
    - [优劣](#优劣)
      
Jedis 是基于 Java 的 Redis 客户端，集成了 Redis 命令操作，提供了连接池管理。

### JedisPool

Jedis 可以和 Redis 直连通信。直连是定义一个 TCP 连接，通过 socket 进行通信。每次操作会创建一个 Jedis 对象，命令执行完毕后关闭连接，这个过程对应的是一个 TCP 连接的创建和断开。

直连的方式简单便捷，但是每次执行命令操作都需要创建和关闭一个 TCP 连接，这会增加客户端和服务器的开销。而且当命令请求密集时，会创建大量连接，可能导致安全隐患。每组连接相互独立，这也导致指令执行不是线程安全的。

通常我们通过连接池（JedisPool）的方式使用 Jedis。即创建一组 Jedis 连接对象放入连接池中，当需要对 Redis 进行操作时从连接池中获取 Jedis 连接对象，操作完成后归还。复用连接，避免了频繁创建 socket 连接，节省了连接开销。 

### JedisPoolConfig

使用连接池需要配置一些参数进行资源管理，`JedisPoolConfig` 是 JedisPool 的配置类。

类图如下：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/JedisPoolConfig类图.png" width="400px">
</div>

JedisPoolConfig 继承了 GenericObjectPoolConfig，提供了一个无参构造函数，用于设置连接池的一些默认配置。

```java
public class JedisPoolConfig extends GenericObjectPoolConfig {
  public JedisPoolConfig() {
    // defaults to make your life with connection pool easier :)
    setTestWhileIdle(true);
    setMinEvictableIdleTimeMillis(60000);
    setTimeBetweenEvictionRunsMillis(30000);
    setNumTestsPerEvictionRun(-1);
  }
}
```

- `setTestWhileIdle`：空闲时进行连接测试，会启动异步 evict 线程进行失效检测。 
- `setMinEvictableIdleTimeMillis`：连接的空闲的最长时间，需要 testWhileIdle 为 true，默认 1 分钟。
- `setTimeBetweenEvictionRunsMillis`：失效检测时间，需要 testWhileIdle 为 true，默认 1 分钟。
- `setNumTestsPerEvictionRun`：每次检查连接的数量，需要 testWhileIdle 为 true，-1 为检查所有连接。

GenericObjectPoolConfig 继承了 BaseObjectPoolConfig，提供了三个配置参数：maxTotal、maxIdle、minIdle。

```java
public class GenericObjectPoolConfig<T> extends BaseObjectPoolConfig<T> {

    /**
     * The default value for the {@code maxTotal} configuration attribute.
     */
    public static final int DEFAULT_MAX_TOTAL = 8;

    /**
     * The default value for the {@code maxIdle} configuration attribute.
     */
    public static final int DEFAULT_MAX_IDLE = 8;

    /**
     * The default value for the {@code minIdle} configuration attribute.
     */
    public static final int DEFAULT_MIN_IDLE = 0;


    private int maxTotal = DEFAULT_MAX_TOTAL;

    private int maxIdle = DEFAULT_MAX_IDLE;

    private int minIdle = DEFAULT_MIN_IDLE;

    // setter、getter、toString、clone.
    
}
```

- `maxTotal`：最大连接数。
- `maxIdle`：最大空闲连接数。
- `minIdle`：最小空闲连接数。

> 建议配置 maxTotal 和 maxIdle 相同，以减少创建新连接的开销。

BaseObjectPoolConfig 是线程池配置的基类。

```java
public abstract class BaseObjectPoolConfig<T> extends BaseObject implements Cloneable {

    /**
     * The default value for the {@code lifo} configuration attribute.
     */
    public static final boolean DEFAULT_LIFO = true;

    /**
     * The default value for the {@code fairness} configuration attribute.
     */
    public static final boolean DEFAULT_FAIRNESS = false;

    /**
     * The default value for the {@code maxWait} configuration attribute.
     */
    public static final long DEFAULT_MAX_WAIT_MILLIS = -1L;

    /**
     * The default value for the {@code minEvictableIdleTimeMillis} configuration attribute.
     */
    public static final long DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS =
            1000L * 60L * 30L;

    /**
     * The default value for the {@code softMinEvictableIdleTimeMillis} configuration attribute.
     */
    public static final long DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS = -1;

    /**
     * The default value for {@code evictorShutdownTimeoutMillis} configuration attribute.
     */
    public static final long DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT_MILLIS =
            10L * 1000L;

    /**
     * The default value for the {@code numTestsPerEvictionRun} configuration attribute.
     */
    public static final int DEFAULT_NUM_TESTS_PER_EVICTION_RUN = 3;

    /**
     * The default value for the {@code testOnCreate} configuration attribute.
     */
    public static final boolean DEFAULT_TEST_ON_CREATE = false;

    /**
     * The default value for the {@code testOnBorrow} configuration attribute.
     */
    public static final boolean DEFAULT_TEST_ON_BORROW = false;

    /**
     * The default value for the {@code testOnReturn} configuration attribute.
     */
    public static final boolean DEFAULT_TEST_ON_RETURN = false;

    /**
     * The default value for the {@code testWhileIdle} configuration attribute.
     */
    public static final boolean DEFAULT_TEST_WHILE_IDLE = false;

    /**
     * The default value for the {@code timeBetweenEvictionRunsMillis} configuration attribute.
     */
    public static final long DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = -1L;

    /**
     * The default value for the {@code blockWhenExhausted} configuration attribute.
     */
    public static final boolean DEFAULT_BLOCK_WHEN_EXHAUSTED = true;

    /**
     * The default value for enabling JMX for pools created with a configuration instance.
     */
    public static final boolean DEFAULT_JMX_ENABLE = true;

    /**
     * The default value for the prefix used to name JMX enabled pools created with a configuration instance.
     */
    public static final String DEFAULT_JMX_NAME_PREFIX = "pool";

    /**
     * The default value for the base name to use to name JMX enabled pools created with a configuration instance. The default is <code>null</code>
     * which means the pool will provide the base name to use.
     */
    public static final String DEFAULT_JMX_NAME_BASE = null;

    /**
     * The default value for the {@code evictionPolicyClassName} configuration attribute.
     */
    public static final String DEFAULT_EVICTION_POLICY_CLASS_NAME = DefaultEvictionPolicy.class.getName();

    private boolean lifo = DEFAULT_LIFO;

    private boolean fairness = DEFAULT_FAIRNESS;

    private long maxWaitMillis = DEFAULT_MAX_WAIT_MILLIS;

    private long minEvictableIdleTimeMillis =
            DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    private long evictorShutdownTimeoutMillis =
            DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT_MILLIS;

    private long softMinEvictableIdleTimeMillis =
            DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    private int numTestsPerEvictionRun =
            DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    private EvictionPolicy<T> evictionPolicy = null; // Only 2.6.0 applications set this

    private String evictionPolicyClassName = DEFAULT_EVICTION_POLICY_CLASS_NAME;

    private boolean testOnCreate = DEFAULT_TEST_ON_CREATE;

    private boolean testOnBorrow = DEFAULT_TEST_ON_BORROW;

    private boolean testOnReturn = DEFAULT_TEST_ON_RETURN;

    private boolean testWhileIdle = DEFAULT_TEST_WHILE_IDLE;

    private long timeBetweenEvictionRunsMillis =
            DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    private boolean blockWhenExhausted = DEFAULT_BLOCK_WHEN_EXHAUSTED;

    private boolean jmxEnabled = DEFAULT_JMX_ENABLE;

    // TODO Consider changing this to a single property for 3.x
    private String jmxNamePrefix = DEFAULT_JMX_NAME_PREFIX;

    private String jmxNameBase = DEFAULT_JMX_NAME_BASE;

    // setter、getter、toString、clone.

}
```

- `lifo`：提供了后进先出(LIFO)与先进先出(FIFO)两种行为模式的池；默认 DEFAULT_LIFO = true，当池中有空闲可用的对象时，调用 borrowObject 方法会返回最近（后进）的实例。
- `fairness`：当从池中获取资源或者将资源还回池中时,是否使用 ReentrantLock 的公平锁机制；默认DEFAULT_FAIRNESS = false。
- `maxWaitMillis`：当连接池资源用尽后，调用者获取连接时的最大等待时间（单位 ：毫秒）；默认值 DEFAULT_MAX_WAIT_MILLIS = -1L， 永不超时。
- `minEvictableIdleTimeMillis`：连接的最小空闲时间，达到此值后该空闲连接可能会被移除（还需看是否已达最大空闲连接数）；默认值 DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS = 1000L * 60L * 30L
- `evictorShutdownTimeoutMillis`：关闭驱逐线程的超时时间；默认值 DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT_MILLIS = 10L * 1000L。
- `softMinEvictableIdleTimeMillis`：连接空闲的最小时间，达到此值后空闲链接将会被移除，且保留 minIdle 个空闲连接数；默认值 DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS = -1
- `numTestsPerEvictionRun`：检测空闲对象线程每次运行时检测的空闲对象的数量；如果 numTestsPerEvictionRun >= 0, 则取 numTestsPerEvictionRun 和池内的连接数的较小值作为每次检测的连接数；
如果 numTestsPerEvictionRun < 0，则每次检查的连接数是检查时池内连接的总数除以这个值的绝对值再向上取整的结果；默认值 DEFAULT_NUM_TESTS_PER_EVICTION_RUN = 3。
- `evictionPolicy`：驱逐策略的类名；默认值 DEFAULT_EVICTION_POLICY_CLASS_NAME = “org.apache.commons.pool2.impl.DefaultEvictionPolicy”。
- `testOnCreate`：在创建对象时检测对象是否有效, 配置 true 会降低性能；默认值 DEFAULT_TEST_ON_CREATE = false。
- `testOnBorrow`：在从对象池获取对象时是否检测对象有效, 配置 true 会降低性能；默认值 DEFAULT_TEST_ON_BORROW = false。
- `testOnReturn`：在向对象池中归还对象时是否检测对象有效 , 配置 true 会降低性能；默认值 DEFAULT_TEST_ON_RETURN = false。
- `testWhileIdle`：在检测空闲对象线程检测到对象不需要移除时，是否检测对象的有效性。建议配置为true，不影响性能，并且保证安全性；默认值 DEFAULT_TEST_WHILE_IDLE = false。
- `timeBetweenEvictionRunsMillis`：空闲连接检测的周期（单位毫秒）；如果为负值，表示不运行检测线程；默认值 DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = -1L。
- `blockWhenExhausted`：当对象池没有空闲对象时，新的获取对象的请求是否阻塞（true 阻塞，maxWaitMillis 才生效； false 连接池没有资源立马抛异常）；默认值 DEFAULT_BLOCK_WHEN_EXHAUSTED = true。
- `jmxEnabled`：是否注册 JMX；默认值 DEFAULT_JMX_ENABLE = true。
- `jmxNamePrefix`：JMX前缀；默认值 DEFAULT_JMX_NAME_PREFIX = “pool”。
- `jmxNameBase`：使用 base + jmxNamePrefix + i 来生成ObjectName；默认值 DEFAULT_JMX_NAME_BASE = null，GenericObjectPool 构造方法使用 ONAME_BASE 初始化。

> JMX（Java Management Extensions，即Java管理扩展）是一个为应用程序、设备、系统等植入管理功能的框架。JMX可以跨越一系列异构操作系统平台、系统体系结构和网络传输协议，灵活的开发无缝集成的系统、网络和服务管理应用。

<!--  区分 minEvictableIdleTimeMillis 和 softMinEvictableIdleTimeMillis -->

### minEvictableIdleTimeMillis 和 softMinEvictableIdleTimeMillis

minEvictableIdleTimeMillis 和 softMinEvictableIdleTimeMillis 都用于限制连接的空闲时间。

- `minEvictableIdleTimeMillis`：连接的最小空闲时间，达到此值后该空闲连接可能会被移除（还需看是否已达最大空闲连接数）；默认值 DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS = 1000L * 60L * 30L
- `softMinEvictableIdleTimeMillis`：连接空闲的最小时间，达到此值后空闲链接将会被移除，且保留 minIdle 个空闲连接数；默认值 DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS = -1

线程池存在一个叫驱逐器的对象，用于检测和驱逐空闲连接。

```java
public class DefaultEvictionPolicy<T> implements EvictionPolicy<T> {

    @Override
    public boolean evict(final EvictionConfig config, final PooledObject<T> underTest,
                         final int idleCount) {

        if ((config.getIdleSoftEvictTime() < underTest.getIdleTimeMillis() &&
                config.getMinIdle() < idleCount) ||
                config.getIdleEvictTime() < underTest.getIdleTimeMillis()) {
            return true;
        }
        return false;
    }
}
```

从上面代码可以看出，驱逐空闲线程由 minEvictableIdleTimeMillis 和 softMinEvictableIdleTimeMillis 决定。

### 创建线程池

Pool 是 Jedis 中线程池的基类，构造函数：

```java
// redis/clients/util/Pool.java#Pool
public Pool(final GenericObjectPoolConfig poolConfig, PooledObjectFactory<T> factory) {
    initPool(poolConfig, factory);
}
```

Pool 构造函数中调用了 initPool 用于初始化线程池。Jedis 创建线程池其实是创建了一个 GenericObjectPool。

```java
// redis/clients/util/Pool.java#Pool
public void initPool(final GenericObjectPoolConfig poolConfig, PooledObjectFactory<T> factory) {

    if (this.internalPool != null) {
      try {
        closeInternalPool();
      } catch (Exception e) {
      }
    }
    
    this.internalPool = new GenericObjectPool<T>(factory, poolConfig);
}
```

GenericObjectPool 构造函数：

```java
// org/apache/commons/pool2/impl/GenericObjectPool.java#GenericObjectPool
public GenericObjectPool(final PooledObjectFactory<T> factory,
            final GenericObjectPoolConfig<T> config) {

    super(config, ONAME_BASE, config.getJmxNamePrefix());

    if (factory == null) {
        jmxUnregister(); // tidy up
        throw new IllegalArgumentException("factory may not be null");
    }
    this.factory = factory;

    idleObjects = new LinkedBlockingDeque<>(config.getFairness());

    setConfig(config);
}
```

GenericObjectPool 构造函数中 setConfig 用于设置参数。

```java
// org/apache/commons/pool2/impl/BaseGenericObjectPool.java#setConfig
public void setConfig(final GenericObjectPoolConfig<T> conf) {
    super.setConfig(conf);
    setMaxIdle(conf.getMaxIdle());
    setMinIdle(conf.getMinIdle());
    setMaxTotal(conf.getMaxTotal());
}

// org/apache/commons/pool2/impl/BaseGenericObjectPool.java#setConfig
protected void setConfig(final BaseObjectPoolConfig<T> conf) {
    setLifo(conf.getLifo());
    setMaxWaitMillis(conf.getMaxWaitMillis());
    setBlockWhenExhausted(conf.getBlockWhenExhausted());
    setTestOnCreate(conf.getTestOnCreate());
    setTestOnBorrow(conf.getTestOnBorrow());
    setTestOnReturn(conf.getTestOnReturn());
    setTestWhileIdle(conf.getTestWhileIdle());
    setNumTestsPerEvictionRun(conf.getNumTestsPerEvictionRun());
    setMinEvictableIdleTimeMillis(conf.getMinEvictableIdleTimeMillis());
    setTimeBetweenEvictionRunsMillis(conf.getTimeBetweenEvictionRunsMillis());
    setSoftMinEvictableIdleTimeMillis(conf.getSoftMinEvictableIdleTimeMillis());
    final EvictionPolicy<T> policy = conf.getEvictionPolicy();
    if (policy == null) {
        // Use the class name (pre-2.6.0 compatible)
        setEvictionPolicyClassName(conf.getEvictionPolicyClassName());
    } else {
        // Otherwise, use the class (2.6.0 feature)
        setEvictionPolicy(policy);
    }
    setEvictorShutdownTimeoutMillis(conf.getEvictorShutdownTimeoutMillis());
}
```

setConfig 用于设置线程池属性，其中 setTimeBetweenEvictionRunsMillis 除了设置属性，还调用了 startEvictor 启动驱逐器。

```java
// org/apache/commons/pool2/impl/BaseGenericObjectPool.java#setTimeBetweenEvictionRunsMillis
public final void setTimeBetweenEvictionRunsMillis(
        final long timeBetweenEvictionRunsMillis) {
    this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    startEvictor(timeBetweenEvictionRunsMillis);
}
```

startEvictor 启动驱逐器，用于检测和驱逐空闲连接。evictorShutdownTimeoutMillis 是驱逐器运行的超时时间，超过这个时间会自动销毁。

```java
// org/apache/commons/pool2/impl/BaseGenericObjectPool.java#startEvictor
final void startEvictor(final long delay) {
    synchronized (evictionLock) {
        EvictionTimer.cancel(evictor, evictorShutdownTimeoutMillis, TimeUnit.MILLISECONDS);
        evictor = null;
        evictionIterator = null;
        if (delay > 0) {
            evictor = new Evictor();
            EvictionTimer.schedule(evictor, delay, delay);
        }
    }
}
```

EvictionPolicy 是驱逐器的抽象接口，它只有一个接口方法 evict，用于定义空闲连接驱除规则，DefaultEvictionPolicy 是它的默认实现。

```java
public class DefaultEvictionPolicy<T> implements EvictionPolicy<T> {

    @Override
    public boolean evict(final EvictionConfig config, final PooledObject<T> underTest,
            final int idleCount) {

        if ((config.getIdleSoftEvictTime() < underTest.getIdleTimeMillis() &&
                config.getMinIdle() < idleCount) ||
                config.getIdleEvictTime() < underTest.getIdleTimeMillis()) {
            return true;
        }
        return false;
    }
}
```

### 优劣

Jedis 是老牌的 Redis 的 Java 实现客户端，提供了比较全面的 Redis 命令的支持。

但是 Jedis 使用阻塞 I/O，且其方法调用都是同步的，程序流需要等到 sockets 处理完 I/O 才能执行，不支持异步；且 Jedis 客户端是多线程的，不能保证线程安全。