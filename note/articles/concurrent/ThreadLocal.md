# ThreadLocal

ThreadLocal 是维持线程封闭性的一种方法，这个类使线程中某个值与保存值的对象关联起来。ThreadLocal 提供了 get 与 set 等访问接口或方法，这些方法为每个使用该变量的线程都存有一份独立的副本。

### 使用示例

下面是 ThreadLocal 在切面编程中的应用：

```java
@Aspect
@Component
public class IdempotentAspect {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private ThreadLocal<Map<String, Object>> threadLocal = new ThreadLocal();

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private Application application;

    @Pointcut("@annotation(****.Idempotent)")
    public void pointCut() {
    }

    @Before("pointCut()")
    public void beforePointCut(JoinPoint joinPoint) throws Exception {
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = requestAttributes.getRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (!method.isAnnotationPresent(Idempotent.class)) {
            return;
        }
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        boolean isIdempotent = idempotent.isIdempotent();
        if (!isIdempotent) {
            return;
        }
        // cache key
        String appName = application.getName();
        String url = request.getRequestURL().toString();
        String argString = Arrays.asList(joinPoint.getArgs()).toString();
        String key = appName + ":" + url + argString;
        // 过期时间
        long expireTime = idempotent.expireTime();
        TimeUnit timeUnit = idempotent.timeUnit();
        // 异常信息
        String info = idempotent.info();
        boolean flag = redisTemplate.opsForValue()
                .setIfAbsent(key, CacheContant.IDEMPOTENTANNOTATION.getDefaultValue(), expireTime, timeUnit);
        if (!flag) {
            logger.warn(info + ":" + key);
            throw new IdempotentException(info);
        }
        // 业务执行完毕是否删除key
        boolean delKey = idempotent.delKey();
        Map<String, Object> map = new HashMap<>();
        map.put("key", key);
        map.put("delKey", delKey);
        threadLocal.set(map);
    }

    @After("pointCut()")
    public void afterPointCut(JoinPoint joinPoint) {
        // afterPointCut 根据设置，决定要不要删除缓存
        Map<String, Object> map = threadLocal.get();
        if (map != null && map.size() > 0) {
            if (map.get("delKey") != null && (Boolean) map.get("delKey")) {
                redisTemplate.delete(StringUtil.getString(map.get("key")));
            }
        }
        // 移除该key 防止内存泄漏
        threadLocal.remove();
    }
}
```

ThreadLocal 可以提供线程局部变量，每个线程 Thread 拥有一份自己的副本变量，多个线程互不干扰。

### 数据结构

ThreadLocal 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/concurrent/ThreadLoacl类图.png" width="600px">
</div>

ThreadLocal 包含一个内部类 ThreadLocalMap，ThreadLocalMap 用于存储元素，每个元素都对应这一个内部类 Entry，这是一个弱引用。弱引用是一旦发生 GC 就会被回收。

ThreadLocalMap 和 HashMap 并不类似，ThreadLocalMap 有自己的独立实现，它是通过数组实现的。可以简单地将它的 key 视作 ThreadLocal，value 为代码中放入的值（实际上 key 并不是 ThreadLocal 本身，而是它的一个弱引用）。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/concurrent/ThreadLocalMap结构.png" width="600px">
</div>

```java
static class Entry extends WeakReference<ThreadLocal<?>> {
    /** The value associated with this ThreadLocal. */
    Object value;

    Entry(ThreadLocal<?> k, Object v) {
        super(k);
        value = v;
    }
}
```

每个线程在往 ThreadLocal 里放值的时候，都会往自己的 ThreadLocalMap 里存，读也是以 ThreadLocal 作为引用，在自己的 map 里找对应的 key，从而实现了线程隔离。

Thread 类有一个类型为 ThreadLocal。ThreadLocalMap 的实例变量 threadLocals，也就是说每个线程有一个自己的 ThreadLocalMap。

```java
public class Thread implements Runnable {
    // ...
    ThreadLocal.ThreadLocalMap threadLocals = null;
    // ...
}
```

### 源码分析

#### set

ThreadLocal 中的 set 方法原理如上图所示，很简单，主要是判断 ThreadLocalMap 是否存在，然后使用 ThreadLocal 中的 set 方法进行数据处理。

```java
public void set(T value) {
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null)
        map.set(this, value);
    else
        createMap(t, value);
}


private void set(ThreadLocal<?> key, Object value) {
    Entry[] tab = table;
    int len = tab.length;
    int i = key.threadLocalHashCode & (len-1);

    for (Entry e = tab[i];
         e != null;
         e = tab[i = nextIndex(i, len)]) {
        ThreadLocal<?> k = e.get();

        if (k == key) {
            e.value = value;
            return;
        }

        if (k == null) {
            replaceStaleEntry(key, value, i);
            return;
        }
    }

    tab[i] = new Entry(key, value);
    int sz = ++size;
    if (!cleanSomeSlots(i, sz) && sz >= threshold)
        rehash();
}

void createMap(Thread t, T firstValue) {
    t.threadLocals = new ThreadLocalMap(this, firstValue);
}
```