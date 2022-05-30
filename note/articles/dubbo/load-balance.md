# 负载均衡

- 目录
  - [LoadBalance](#LoadBalance)
    - [RandomLoadBalance](#RandomLoadBalance)
    - [LeastActiveLoadBalance](#LeastActiveLoadBalance)
    - [RoundRobinLoadBalance](#RoundRobinLoadBalance)
    - [ConsistentHashLoadBalance](#ConsistentHashLoadBalance)
    - [ShortestResponseLoadBalance](#ShortestResponseLoadBalance)
  - [外链](#外链)
 
在分布式系统中，负载均衡是必不可少的一个模块，Dubbo 中提供了五种负载均衡的实现，如下：

| 类型 | 描述 | 是否默认 | 是否加权 |
| ---- | ---- | -------- | -------- |
| RandomLoadBalance | 随机 | 是 | 是，默认权重相同 |
| RoundRobinLoadBalance | 轮询 | 否 | 是，默认权重相同 |
| LeastActiveLoadBalance | 最少活跃数调用 | 否 | 不完全是，默认权重相同，仅在活跃数相同时按照权重比随机 |
| ConsistentHashLoadBalance | 一致性 Hash | 否 | 否 |
| ShortestResponseLoadBalance | 最短时间调用 | 否 | 不完全是，默认权重相同，仅在预估调用相同时按照权重比随机 |

> 大部分算法都是在权重比的基础上进行负载均衡，RandomLoadBalance 是默认的算法。

### LoadBalance

在 Dubbo 中，所有负载均衡实现类均继承自 AbstractLoadBalance，该类实现了 LoadBalance 接口，并封装了一些公共的逻辑。

LoadBalance 接口：

```java
/**
 * LoadBalance. (SPI, Singleton, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Load_balancing_(computing)">Load-Balancing</a>
 *
 * @see org.apache.dubbo.rpc.cluster.Cluster#join(Directory)
 */
@SPI(RandomLoadBalance.NAME)
public interface LoadBalance {

    /**
     * select one invoker in list.
     *
     * @param invokers   invokers.
     * @param url        refer url
     * @param invocation invocation.
     * @return selected invoker.
     */
    @Adaptive("loadbalance")
    <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException;

}
```

LoadBalance 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/dubbo/LoadBalance类图.png" width="600px">
</div>

AbstractLoadBalance：

```java
/**
 * AbstractLoadBalance
 */
public abstract class AbstractLoadBalance implements LoadBalance {
    /**
     * Calculate the weight according to the uptime proportion of warmup time
     * the new weight will be within 1(inclusive) to weight(inclusive)
     *
     * @param uptime the uptime in milliseconds
     * @param warmup the warmup time in milliseconds
     * @param weight the weight of an invoker
     * @return weight which takes warmup into account
     */
    static int calculateWarmupWeight(int uptime, int warmup, int weight) {
        int ww = (int) (Math.round(Math.pow((uptime / (double) warmup), 2) * weight));
        return ww < 1 ? 1 : (Math.min(ww, weight));
    }

    @Override
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        if (CollectionUtils.isEmpty(invokers)) {
            return null;
        }
        // 如果 invokers 列表中仅有一个 Invoker，直接返回即可，无需进行负载均衡
        if (invokers.size() == 1) {
            return invokers.get(0);
        }
        // 调用 doSelect 方法进行负载均衡，该方法为抽象方法，由子类实现
        return doSelect(invokers, url, invocation);
    }

    protected abstract <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation);

    /**
     * Get the weight of the invoker's invocation which takes warmup time into account
     * if the uptime is within the warmup time, the weight will be reduce proportionally
     *
     * @param invoker    the invoker
     * @param invocation the invocation of this invoker
     * @return weight
     */
    int getWeight(Invoker<?> invoker, Invocation invocation) {
        int weight;
        URL url = invoker.getUrl();
        // Multiple registry scenario, load balance among multiple registries.
        // 从 url 中获取权重 weight 配置值
        if (REGISTRY_SERVICE_REFERENCE_PATH.equals(url.getServiceInterface())) {
            weight = url.getParameter(REGISTRY_KEY + "." + WEIGHT_KEY, DEFAULT_WEIGHT);
        } else {
            weight = url.getMethodParameter(invocation.getMethodName(), WEIGHT_KEY, DEFAULT_WEIGHT);
            if (weight > 0) {
                // 获取服务提供者启动时间戳
                long timestamp = invoker.getUrl().getParameter(TIMESTAMP_KEY, 0L);
                if (timestamp > 0L) {
                    // 计算服务提供者运行时长
                    long uptime = System.currentTimeMillis() - timestamp;
                    if (uptime < 0) {
                        return 1;
                    }
                    // 获取服务预热时间，默认为 10 分钟
                    int warmup = invoker.getUrl().getParameter(WARMUP_KEY, DEFAULT_WARMUP);
                    // 如果服务运行时间小于预热时间，则重新计算服务权重，即降权
                    if (uptime > 0 && uptime < warmup) {
                        // 重新计算服务权重
                        weight = calculateWarmupWeight((int) uptime, warmup, weight);
                    }
                }
            }
        }
        return Math.max(weight, 0);
    }
}
```

select 方法的逻辑比较简单，首先会检测 invokers 集合的合法性，然后再检测 invokers 集合元素数量。如果只包含一个 Invoker，直接返回该 Inovker 即可。如果包含多个 Invoker，此时需要通过负载均衡算法选择一个 Invoker。

AbstractLoadBalance 除了实现了 LoadBalance 接口方法，还封装了一些公共逻辑，比如服务提供者权重计算逻辑。getWeight 封装了权重的计算过程，该过程主要用于保证当服务运行时长小于服务预热时间时，对服务进行降权，避免让服务在启动之初就处于高负载状态。服务预热是一个优化手段，与此类似的还有 JVM 预热。主要目的是让服务启动后“低功率”运行一段时间，使其效率慢慢提升至最佳状态。

#### RandomLoadBalance

加权随机算法负载均衡策略（RandomLoadBalance）是 dubbo 负载均衡的默认实现方式，根据权重分配各个 Invoker 随机选中的比例。这里的意思是：将到达负载均衡流程的 Invoker 列表中的 权重进行求和，然后求出单个 Invoker 权重在总权重中的占比，随机数就在总权重值的范围内生成。

代码摘要：

```java
public class RandomLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "random";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Invoker 数量
        int length = invokers.size();
        // 标识所有 Invoker 的权重是否都一样
        boolean sameWeight = true;
        // 用一个数组保存每个 Invoker 的权重
        int[] weights = new int[length];
        // 第一个 Invoker 的权重
        int firstWeight = getWeight(invokers.get(0), invocation);
        weights[0] = firstWeight;
        // 求和总权重
        int totalWeight = firstWeight;
        for (int i = 1; i < length; i++) {
            int weight = getWeight(invokers.get(i), invocation);
            // 保存每个 Invoker 的权重到数组总
            weights[i] = weight;
            // 累加求和总权重
            totalWeight += weight;
            // 如果不是所有 Invoker 的权重都一样，就给标记上 sameWeight = false
            if (sameWeight && weight != firstWeight) {
                sameWeight = false;
            }
        }
        // 计算随机数取到的 Invoker，条件是必须总权重大于0，并且每个 Invoker 的权重都不一样
        if (totalWeight > 0 && !sameWeight) {
            // 基于 0~总数 范围内生成随机数
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);
            // 计算随机数对应的 Invoker
            for (int i = 0; i < length; i++) {
                offset -= weights[i];
                if (offset < 0) {
                    return invokers.get(i);
                }
            }
        }
        // 如果所有 Invoker 的权重都一样则随机从 Invoker 列表中返回一个
        return invokers.get(ThreadLocalRandom.current().nextInt(length));
    }
}
```

#### LeastActiveLoadBalance

最小活跃数负载均衡策略（LeastActiveLoadBalance）是从最小活跃数的 Invoker 中进行选择。什么是活跃数呢？活跃数是一个 Invoker 正在处理的请求的数量，当 Invoker 开始处理请求时，会将活跃数加 1，完成请求处理后，将相应 Invoker 的活跃数减 1。找出最小活跃数后，最后根据权重进行选择最终的 Invoker。如果最后找出的最小活跃数相同，则随机从中选中一个 Invoker。

代码摘要：

```java
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "leastactive";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Invoker 数量
        int length = invokers.size();
        // 所有 Invoker 中的最小活跃值都是 -1
        int leastActive = -1;
        // 最小活跃值 Invoker 的数量
        int leastCount = 0;
        // 最小活跃值 Invoker 在 Invokers 列表中对应的下标位置
        int[] leastIndexes = new int[length];
        // 保存每个 Invoker 的权重
        int[] weights = new int[length];
        // 总权重
        int totalWeight = 0;
        // 第一个最小活跃数的权重
        int firstWeight = 0;
        // 最小活跃数 Invoker 列表的权重是否一样
        boolean sameWeight = true;

        // 找出最小活跃数 Invoker 的下标
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            // 获取最小活跃数
            int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive();
            // 获取权重
            int afterWarmup = getWeight(invoker, invocation);
            // 保存权重
            weights[i] = afterWarmup;
            // 如果当前最小活跃数为-1（-1为最小值）或小于leastActive
            if (leastActive == -1 || active < leastActive) {
                // 重置最小活跃数
                leastActive = active;
                // 重置最小活跃数 Invoker 的数量
                leastCount = 1;
                // 保存当前 Invoker 在 Invokers 列表中的索引至leastIndexes数组中
                leastIndexes[0] = i;
                // 重置最小活跃数 invoker 的总权重值
                totalWeight = afterWarmup;
                // 记录当前 Invoker 权重为第一个最小活跃数 Invoker 的权重
                firstWeight = afterWarmup;
                // 因为当前 Invoker 重置为第一个最小活跃数 Invoker ，所以标识所有最小活跃数 Invoker 权重都一样的值为 true
                sameWeight = true;
            // 如果当前最小活跃数和已声明的最小活跃数相等
            } else if (active == leastActive) {
                // 记录当前 Invoker 的位置
                leastIndexes[leastCount++] = i;
                // 累加当前 Invoker 权重到总权重中
                totalWeight += afterWarmup;
                // 如果当前权重与firstWeight不相等，则将 sameWeight 改为 false
                if (sameWeight && i > 0
                        && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        // 如果最小活跃数 Invoker 只有一个，直接返回该 Invoker
        if (leastCount == 1) {
            return invokers.get(leastIndexes[0]);
        }
        if (!sameWeight && totalWeight > 0) {
            // 根据权重随机从最小活跃数 Invoker 列表中选择一个
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            for (int i = 0; i < leastCount; i++) {
                int leastIndex = leastIndexes[i];
                offsetWeight -= weights[leastIndex];
                if (offsetWeight < 0) {
                    return invokers.get(leastIndex);
                }
            }
        }
        // 如果所有 Invoker 的权重都一样则随机从 Invoker 列表中返回一个
        return invokers.get(leastIndexes[ThreadLocalRandom.current().nextInt(leastCount)]);
    }
}
```

> 这段代码的整个逻辑就是，从 Invokers 列表中筛选出最小活跃数的 Invoker，然后类似加权随机算法策略方式选择最终的 Invoker 服务。

#### RoundRobinLoadBalance

加权轮询负载均衡策略（RoundRobinLoadBalance）是基于权重来决定轮询的比例。普通轮询会将请求均匀的分布在每个节点，但不能很好调节不同性能服务器的请求处理，所以加权负载均衡来根据权重在轮询机制中分配相对应的请求比例给每台服务器。

代码摘要：

```java
public class RoundRobinLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "roundrobin";

    private static final int RECYCLE_PERIOD = 60000;

    protected static class WeightedRoundRobin {
        private int weight;
        private AtomicLong current = new AtomicLong(0);
        private long lastUpdate;
        public int getWeight() {
            return weight;
        }
        public void setWeight(int weight) {
            this.weight = weight;
            current.set(0);
        }
        public long increaseCurrent() {
            return current.addAndGet(weight);
        }
        public void sel(int total) {
            current.addAndGet(-1 * total);
        }
        public long getLastUpdate() {
            return lastUpdate;
        }
        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }

    private ConcurrentMap<String, ConcurrentMap<String, WeightedRoundRobin>> methodWeightMap = new ConcurrentHashMap<String, ConcurrentMap<String, WeightedRoundRobin>>();
    private AtomicBoolean updateLock = new AtomicBoolean();

    /**
     * get invoker addr list cached for specified invocation
     * <p>
     * <b>for unit test only</b>
     *
     * @param invokers
     * @param invocation
     * @return
     */
    protected <T> Collection<String> getInvokerAddrList(List<Invoker<T>> invokers, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        Map<String, WeightedRoundRobin> map = methodWeightMap.get(key);
        if (map != null) {
            return map.keySet();
        }
        return null;
    }

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // key 为 接口名+方法名
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        // 查看缓存中是否存在相应服务接口的信息，如果没有则新添加一个元素到缓存中
        ConcurrentMap<String, WeightedRoundRobin> map = methodWeightMap.get(key);
        if (map == null) {
            methodWeightMap.putIfAbsent(key, new ConcurrentHashMap<String, WeightedRoundRobin>());
            map = methodWeightMap.get(key);
        }
        // 总权重
        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        // 当前时间戳
        long now = System.currentTimeMillis();
        // 最大 current 的 Invoker
        Invoker<T> selectedInvoker = null;
        // 保存选中的 WeightedRoundRobin 对象
        WeightedRoundRobin selectedWRR = null;
        // 遍历 Invokers 列表
        for (Invoker<T> invoker : invokers) {
            // 从缓存中获取 WeightedRoundRobin 对象
            String identifyString = invoker.getUrl().toIdentityString();
            WeightedRoundRobin weightedRoundRobin = map.get(identifyString);
            // 获取当前 Invoker 对象
            int weight = getWeight(invoker, invocation);

            // 如果当前 Invoker 没有对应的 WeightedRoundRobin 对象，则新增一个
            if (weightedRoundRobin == null) {
                weightedRoundRobin = new WeightedRoundRobin();
                weightedRoundRobin.setWeight(weight);
                map.putIfAbsent(identifyString, weightedRoundRobin);
            }
            // 如果当前 Invoker 权重不等于对应的 WeightedRoundRobin 对象中的权重，则重新设置当前权重到对应的 WeightedRoundRobin 对象中
            if (weight != weightedRoundRobin.getWeight()) {
                weightedRoundRobin.setWeight(weight);
            }
            // 累加权重到 current 中
            long cur = weightedRoundRobin.increaseCurrent();
            // 设置 weightedRoundRobin 对象最后更新时间
            weightedRoundRobin.setLastUpdate(now);
            // 最大 current 的 Invoker，并赋值给相应的变量
            if (cur > maxCurrent) {
                maxCurrent = cur;
                selectedInvoker = invoker;
                selectedWRR = weightedRoundRobin;
            }
            // 累加权重到总权重中
            totalWeight += weight;
        }
        // 如果 Invokers 列表中的数量不等于缓存map中的数量
        if (!updateLock.get() && invokers.size() != map.size()) {
            if (updateLock.compareAndSet(false, true)) {
                try {
                    // 拷贝 map 到 newMap 中
                    ConcurrentMap<String, WeightedRoundRobin> newMap = new ConcurrentHashMap<String, WeightedRoundRobin>();
                    newMap.putAll(map);
                    // newMap 转化为 Iterator
                    Iterator<Entry<String, WeightedRoundRobin>> it = newMap.entrySet().iterator();
                    // 循环删除超过设定时长没更新的缓存
                    while (it.hasNext()) {
                        Entry<String, WeightedRoundRobin> item = it.next();
                        if (now - item.getValue().getLastUpdate() > RECYCLE_PERIOD) {
                            it.remove();
                        }
                    }
                    // 将当前newMap服务缓存中
                    methodWeightMap.put(key, newMap);
                } finally {
                    updateLock.set(false);
                }
            }
        }
        // 如果存在被选中的 Invoker
        if (selectedInvoker != null) {
            // 计算 current = current - totalWeight
            selectedWRR.sel(totalWeight);
            return selectedInvoker;
        }
        // 正常情况这里不会到达
        return invokers.get(0);
    }
}
```
上面选中 Invoker 逻辑为：每个 Invoker 都有一个 current 值，初始值为自身权重。在每个 Invoker 中 `current = current + weight`。遍历完 Invoker 后，current 最大的那个 Invoker 就是本次选中的 Invoker。选中 Invoker 后，将本次 current 值计算 `current = current - totalWeight`。
以上面 192.168.1.10 和 192.168.1.11 两个负载均衡的服务，权重分别为 4、6 。基于选中前 `current = current + weight`、选中后 `current = current - totalWeight` 计算公式得出如下:

| 请求次数 | 选中前 current | 选中后 current | 被选中服务 |
| --- | --- | --- | --- |
| 1 | [4, 6] | [4, -4] | 192.168.1.11 |
| 2 | [8, 2] | [-2, 2] | 192.168.1.10 |
| 3 | [2, 8] | [2, -2] | 192.168.1.11 |
| 4 | [6, 4] | [-4, 4] | 192.168.1.10 |
| 5 | [0, 10] | [0, 0] | 192.168.1.11 |

#### ConsistentHashLoadBalance

一致性 Hash 负载均衡策略（ConsistentHashLoadBalance）是让参数相同的请求分配到同一机器上。把每个服务节点分布在一个环上，请求也分布在环形中。以请求在环上的位置，顺时针寻找换上第一个服务节点同时，为避免请求散列不均匀，Dubbo 中会将每个 Invoker 再虚拟多个节点出来，使得请求调用更加均匀。

代码摘要：

```java
public class ConsistentHashLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "consistenthash";

    /**
     * Hash nodes name
     */
    public static final String HASH_NODES = "hash.nodes";

    /**
     * Hash arguments name
     */
    public static final String HASH_ARGUMENTS = "hash.arguments";

    private final ConcurrentMap<String, ConsistentHashSelector<?>> selectors = new ConcurrentHashMap<String, ConsistentHashSelector<?>>();

    @SuppressWarnings("unchecked")
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // 获取请求的方法名
        String methodName = RpcUtils.getMethodName(invocation);
        // key = 接口名+方法名
        String key = invokers.get(0).getUrl().getServiceKey() + "." + methodName;
        // invokers 的 hashcode
        int identityHashCode = System.identityHashCode(invokers);
        // 查看缓存中是否存在对应 key 的数据，或 Invokers 列表是否有过变动。如果没有，则新添加到缓存中，并且返回负载均衡得出的 Invoker
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectors.get(key);
        if (selector == null || selector.identityHashCode != identityHashCode) {
            selectors.put(key, new ConsistentHashSelector<T>(invokers, methodName, identityHashCode));
            selector = (ConsistentHashSelector<T>) selectors.get(key);
        }
        return selector.select(invocation);
    }

    // ConsistentHashSelector class ...
}
```

doSelect 中主要实现缓存检查和 Invokers 变动检查，一致性 Hash 负载均衡的实现在这个内部类 ConsistentHashSelector 中实现。

ConsistentHashSelector：

```java
private static final class ConsistentHashSelector<T> {

    // 存储虚拟节点
    private final TreeMap<Long, Invoker<T>> virtualInvokers;

    // 节点数
    private final int replicaNumber;

    // invoker 列表的 hashcode，用来判断 Invoker 列表是否变化
    private final int identityHashCode;

    // 请求中用来作Hash映射的参数的索引
    private final int[] argumentIndex;

    ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) {
        this.virtualInvokers = new TreeMap<Long, Invoker<T>>();
        this.identityHashCode = identityHashCode;
        URL url = invokers.get(0).getUrl();
        // 获取节点数
        this.replicaNumber = url.getMethodParameter(methodName, HASH_NODES, 160);
        // 获取配置中的 参数索引
        String[] index = COMMA_SPLIT_PATTERN.split(url.getMethodParameter(methodName, HASH_ARGUMENTS, "0"));
        argumentIndex = new int[index.length];
        for (int i = 0; i < index.length; i++) {
            argumentIndex[i] = Integer.parseInt(index[i]);
        }

        for (Invoker<T> invoker : invokers) {
            // 获取 Invoker 中的地址，包括端口号
            String address = invoker.getUrl().getAddress();
            // 创建虚拟节点
            for (int i = 0; i < replicaNumber / 4; i++) {
                byte[] digest = md5(address + i);
                for (int h = 0; h < 4; h++) {
                    long m = hash(digest, h);
                    virtualInvokers.put(m, invoker);
                }
            }
        }
    }

    // 找出 Invoker
    public Invoker<T> select(Invocation invocation) {
        // 将参数转为字符串
        String key = toKey(invocation.getArguments());
        // 字符串参数转换为 md5
        byte[] digest = md5(key);
        // 根据 md5 找出 Invoker
        return selectForKey(hash(digest, 0));
    }

    // 将参数拼接成字符串
    private String toKey(Object[] args) {
        StringBuilder buf = new StringBuilder();
        for (int i : argumentIndex) {
            if (i >= 0 && i < args.length) {
                buf.append(args[i]);
            }
        }
        return buf.toString();
    }

    // 利用 md5 匹配到对应的 Invoker
    private Invoker<T> selectForKey(long hash) {
        // 找到第一个大于当前 hash 的 Invoker
        Map.Entry<Long, Invoker<T>> entry = virtualInvokers.ceilingEntry(hash);
        if (entry == null) {
            entry = virtualInvokers.firstEntry();
        }
        return entry.getValue();
    }

    // hash 运算
    private long hash(byte[] digest, int number) {
        return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                | (digest[number * 4] & 0xFF))
                & 0xFFFFFFFFL;
    }

    // md5 运算
    private byte[] md5(String value) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        md5.reset();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        md5.update(bytes);
        return md5.digest();
    }
}
```


#### ShortestResponseLoadBalance

最短时间调用调用算法是指预估出来每个处理完请求的提供者所需时间，然后又选择最少最短时间的提供者进行调用，整体处理逻辑和最少活跃数算法基本相似。

代码摘要：

```java
/**
 * 过滤成功调用响应时间最短的invoker的数量，并计算这些invoker的权重和数量。
 * 如果只有一个invoker，直接调用
 * 如果多个invoker不同，按总权重加权随机
 * 如果多个invoker相同，按权重随机
 */
public class ShortestResponseLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "shortestresponse";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // invoker数量
        int length = invokers.size();
        // 所有invoker的最短响应时间
        long shortestResponse = Long.MAX_VALUE;
        // 具有相同最短响应时间的invoker的数量
        int shortestCount = 0;
        // 具有相同估计最短响应时间的调用者的索引
        int[] shortestIndexes = new int[length];
        // 每个invoker的权重
        int[] weights = new int[length];
        // 所有最短响应invokers的权重总和
        int totalWeight = 0;
        // 第一个最短响应invoker的权重
        int firstWeight = 0;
        // Every shortest response invoker has the same weight value?
        // 每个相应invoker是否具有相同权重？
        boolean sameWeight = true;

        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            RpcStatus rpcStatus = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName());
            // 从活动连接中计算估计的响应时间和成功的平均运行时间。
            long succeededAverageElapsed = rpcStatus.getSucceededAverageElapsed();
            int active = rpcStatus.getActive();
            long estimateResponse = succeededAverageElapsed * active;
            int afterWarmup = getWeight(invoker, invocation);
            weights[i] = afterWarmup;
            // Same as LeastActiveLoadBalance
            if (estimateResponse < shortestResponse) {
                shortestResponse = estimateResponse;
                shortestCount = 1;
                shortestIndexes[0] = i;
                totalWeight = afterWarmup;
                firstWeight = afterWarmup;
                sameWeight = true;
            } else if (estimateResponse == shortestResponse) {
                shortestIndexes[shortestCount++] = i;
                totalWeight += afterWarmup;
                if (sameWeight && i > 0
                        && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        if (shortestCount == 1) {
            return invokers.get(shortestIndexes[0]);
        }
        if (!sameWeight && totalWeight > 0) {
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            for (int i = 0; i < shortestCount; i++) {
                int shortestIndex = shortestIndexes[i];
                offsetWeight -= weights[shortestIndex];
                if (offsetWeight < 0) {
                    return invokers.get(shortestIndex);
                }
            }
        }
        return invokers.get(shortestIndexes[ThreadLocalRandom.current().nextInt(shortestCount)]);
    }
}
```

### 外链

- [负载均衡](https://dubbo.apache.org/zh/docs/v2.7/dev/source/loadbalance/)