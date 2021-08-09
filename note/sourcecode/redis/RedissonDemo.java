package personal.boot.redisclient;

import org.redisson.Redisson;
import org.redisson.RedissonMultiLock;
import org.redisson.api.*;
import org.redisson.config.*;

import java.io.Serializable;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author liux
 */
public class RedissonDemo {

    /**
     * RedissonClient
     */
    private static RedissonClient redisson = null;

    static {
        Config config = new Config();
        // 使用单机Redis服务
        config.useSingleServer().setAddress("redis://IP:port").setDatabase(8).setPassword("PWD");
        // 创建Redisson客户端
        redisson = Redisson.create(config);
    }

    public static void main(String[] args) {
        // 配置
        //configOperate();
        // 数据操作
        // dataTypeOperate();
        // 锁操作
        locksOperate();
        // 工具操作
        //toolOprate();
    }

    /**
     * 配置
     */
    public static void configOperate() {
        // 单机
        singleConfig();
        // 主从
        masterSlaveConfig();
        // 哨兵
        sentinelConfig();
        // 集群
        clusterConfig();
    }

    /**
     * 配置 : 单机
     */
    public static void singleConfig() {
        // 连接配置
        RedissonClient redisson = Redisson.create();
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        RedissonClient redissonClient = Redisson.create(config);

        // 实例配置类
        SingleServerConfig singleConfig = config.useSingleServer();
    }

    /**
     * 配置 : 主从
     */
    public static void masterSlaveConfig() {
        // 连接配置
        Config config = new Config();
        config.useMasterSlaveServers()
                .setMasterAddress("redis://127.0.0.1:6379")
                .addSlaveAddress("redis://127.0.0.1:6389", "redis://127.0.0.1:6332", "redis://127.0.0.1:6419")
                .addSlaveAddress("redis://127.0.0.1:6399");
        RedissonClient redissonClient = Redisson.create(config);

        // 实例配置类
        MasterSlaveServersConfig masterSlaveConfig = config.useMasterSlaveServers();
    }

    /**
     * 配置 : 哨兵
     */
    public static void sentinelConfig() {
        // 连接配置
        Config config = new Config();
        config.useSentinelServers()
                .setMasterName("mymaster")
                .addSentinelAddress("127.0.0.1:26389", "127.0.0.1:26379")
                .addSentinelAddress("127.0.0.1:26319");
        RedissonClient redissonClient = Redisson.create(config);

        // 实例配置类
        SentinelServersConfig sentinelConfig = config.useSentinelServers();
    }

    /**
     * 配置 : 集群
     */
    public static void clusterConfig() {
        // 连接配置
        Config config = new Config();
        config.useClusterServers()
                .setScanInterval(2000) // 集群状态扫描间隔时间，单位是毫秒
                .addNodeAddress("redis://127.0.0.1:7000", "redis://127.0.0.1:7001")
                .addNodeAddress("redis://127.0.0.1:7002");
        RedissonClient redissonClient = Redisson.create(config);

        // 实例配置类
        ClusterServersConfig clusterConfig = config.useClusterServers();
    }

    /**
     * 数据结构操作
     */
    public static void dataTypeOperate() {
        // 原子操作
        atomicOperate();
        // 字符串操作
        stringOperate();
        // 位图操作
        bitSetOperate();
        // 列表操作
        listOperate();
        // 哈希操作
        hashOperate();
        // 集合操作
        setOperate();
        // 有序集合操作
        zsetOperate();
        // 布隆过滤器
        bloomFilterOperate();
        // 地理位置操作
        //geoOperate();
    }

    /**
     * 数据结构操作 : Atomic 原子操作
     */
    public static void atomicOperate() {
        // 原子操作 Long
        String key1 = "atomicLongkey1";
        RAtomicLong atomicLong = redisson.getAtomicLong(key1);
        atomicLong.set(3);
        atomicLong.incrementAndGet();
        Long v1 = atomicLong.get();
        System.out.println("key1 atomic long value1:" + v1);

        // 原子操作 Double
        String key2 = "atomicDoublekey2";
        RAtomicDouble atomicDouble = redisson.getAtomicDouble(key2);
        atomicDouble.set(2.81);
        atomicDouble.addAndGet(4.11);
        Double v2 = atomicDouble.get();
        System.out.println("key1 atomic double value2:" + v2);
        atomicDouble.incrementAndGet();
        Double v3 = atomicDouble.get();
        System.out.println("key1 atomic double value3:" + v3);

        // 累加器 LongAdder
        RLongAdder rLongAdder = redisson.getLongAdder("myLongAdder");
        rLongAdder.add(12);
        rLongAdder.increment();
        rLongAdder.decrement();
        rLongAdder.sum();
    }

    /**
     * 数据结构操作 : string
     */
    public static void stringOperate() {
        // 操作字符串
        String key1 = "stringkey1";
        RBucket<String> rBucket1 = redisson.getBucket(key1);
        rBucket1.set("key1 string value1");
        String value1 = (String) redisson.getBucket(key1).get();
        System.out.println(value1);
        // 获取并同步修改 key
        redisson.getBucket(key1).getAndSet("key1 string value2");
        value1 = (String) redisson.getBucket(key1).get();
        System.out.println("key1 string value2 :" + value1);
        // 获取并同步删除 key
        redisson.getBucket(key1).getAndDelete();
        value1 = (String) redisson.getBucket(key1).get();
        System.out.println("key1 string value1 :" + value1);

        // 操作对象
        Student student = new Student();
        student.setId(1412);
        student.setName("岚");
        String key2 = "studentkey2";
        RBucket<Student> rBucket2 = redisson.getBucket(key2);
        rBucket2.set(student);
        Student value3 = (Student) redisson.getBucket(key2).get();
        System.out.println(value3.toString());
    }

    /**
     * 数据结构操作 : BitSet
     */
    public static void bitSetOperate() {
        String key1 = "bitSetkey1";
        RBitSet set = redisson.getBitSet(key1);
        // key 是 offset ，value 的 true/true 代表 1/0
        set.set(0, true);
        set.set(999, true);
        set.set(1812, false);
        set.set(1412, true);
        // 获取为 1 的 offset
        long value1 = redisson.getBitSet(key1).cardinality();
        // 将指定位置的置为 0
        set.clear(0);
        value1 = redisson.getBitSet(key1).cardinality();

        // 获取指定位置的值
        boolean v2 = redisson.getBitSet(key1).get(999);
        boolean v3 = redisson.getBitSet(key1).get(263762);

        // 获取所有为 1 的位置
        BitSet bitSet = redisson.getBitSet(key1).asBitSet();
        System.out.println("bitSet.toString : " + bitSet.toString());

        // 清空 bitSet
        set.xor(key1);
        bitSet = redisson.getBitSet(key1).asBitSet();
        System.out.println("bitSet.toString xor : " + bitSet.toString());
    }

    /**
     * 数据结构操作 : list
     */
    public static void listOperate() {
        // 对象
        Student student = new Student();
        student.setId(1412);
        student.setName("岚");

        String key1 = "listkey1";
        RList<Student> studentRList = redisson.getList(key1);
        studentRList.add(student);
        // 设置有效期
        studentRList.expire(300, TimeUnit.SECONDS);
        // 通过key获取value
        Student v1 = (Student) redisson.getList(key1).get(0);
        System.out.println("studentRList v1 :" + v1.toString());

        // 字符串
        String key2 = "listkey2";
        RList<String> stringRList1 = redisson.getList(key2);
        stringRList1.add("我");
        stringRList1.add("有");
        stringRList1.add("点");
        stringRList1.add("不");
        stringRList1.add("对");
        stringRList1.add("劲");
        System.out.println("stringRList1 key2 readAll" + stringRList1.readAll());

        String key3 = "listkey3";
        RList<String> stringRList2 = redisson.getList(key3);
    }

    /**
     * 数据结构操作 : hash
     */
    public static void hashOperate() {
        String key1 = "hashkey1";
        RMap<String, String> rMap1 = redisson.getMap(key1);
        rMap1.put("1", "1");
        rMap1.put("2", "2");
        rMap1.put("3", "3");
        rMap1.put("4", "4");
        rMap1.put("5", "5");
        rMap1.put("6", "6");
        System.out.println("rMap1 key1 keySet : " + redisson.getMap(key1).keySet());
        redisson.getMap(key1).clear();
        System.out.println("rMap1 key1 keySet clear : " + redisson.getMap(key1).keySet());

        // 对象亦可
    }

    /**
     * 数据结构操作 : set
     */
    public static void setOperate() {
        String key1 = "setkey1";
        RSet<String> rSet1 = redisson.getSet(key1);
        rSet1.add("jack");
        rSet1.add("tom");
        System.out.println("rSet1 key1 readAll: " + redisson.getSet(key1).readAll());

        // 对象亦可
    }

    /**
     * 数据结构操作 : zset
     */
    public static void zsetOperate() {
        String key1 = "zsetkey1";
        RSortedSet<Integer> rSortedSet1 = redisson.getSortedSet(key1);
        rSortedSet1.add(1);
        rSortedSet1.add(2);
        rSortedSet1.add(4);
        rSortedSet1.add(3);
        System.out.println("rSortedSet1 key1 readAll: " + redisson.getSortedSet(key1).readAll());

        String key2 = "zsetkey2";
        RScoredSortedSet<String> rScoredSortedSet1 = redisson.getScoredSortedSet(key2);
        rScoredSortedSet1.add(1, "我");
        rScoredSortedSet1.add(5, "对");
        rScoredSortedSet1.add(3, "点");
        rScoredSortedSet1.add(2, "有");
        rScoredSortedSet1.add(4, "不");
        rScoredSortedSet1.add(6, "劲");
        System.out.println("rScoredSortedSet1 key1 readAll : " + redisson.getScoredSortedSet(key2).readAll());
        System.out.println("rScoredSortedSet1 key1 getScore 有 : " + redisson.getScoredSortedSet(key2).getScore("有"));

        // 对象亦可
    }

    /**
     * 数据结构操作 : Bloom Filter
     */
    public static void bloomFilterOperate() {
        String key1 = "bloomkey1";
        RBloomFilter seqIdBloomFilter = redisson.getBloomFilter(key1);
        // 初始化预期插入的数据量为10000000和期望误差率为0.01
        seqIdBloomFilter.tryInit(10000000, 0.01);
        // 插入部分数据
        seqIdBloomFilter.add("123");
        seqIdBloomFilter.add("456");
        seqIdBloomFilter.add("789");
        // 判断是否存在
        System.out.println("Bloom Filter contains 123 :" + seqIdBloomFilter.contains("123"));
        System.out.println("Bloom Filter contains 789 :" + seqIdBloomFilter.contains("789"));
        System.out.println("Bloom Filter contains 100 :" + seqIdBloomFilter.contains("100"));
    }

    /**
     * 数据结构操作 : geo
     */
    public static void geoOperate() {
        RGeo<String> geo = redisson.getGeo("test");
        geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"),
                new GeoEntry(15.087269, 37.502669, "Catania"));
        geo.addAsync(37.618423, 55.751244, "Moscow");
        Double distance = geo.dist("Palermo", "Catania", GeoUnit.METERS);
        geo.hashAsync("Palermo", "Catania");
        Map<String, GeoPosition> positions = geo.pos("test2", "Palermo", "test3", "Catania", "test1");
        List<String> cities = geo.radius(15, 37, 200, GeoUnit.KILOMETERS);
        Map<String, GeoPosition> citiesWithPositions = geo.radiusWithPosition(15, 37, 200, GeoUnit.KILOMETERS);
    }

    /**
     * 锁操作
     */
    public static void locksOperate() {
        // 可重入锁
        lockOperate();
        // 公平锁
        fairLockOperate();
        // MultiLock
        multiLockOperate();
        // RedLock
        redLockOperate();
        // 读写锁
        readWriteLockOperate();
        // Semaphore
        semaphoreOperate();
        // CountDownLatch
        countDownLatchOperate();

    }

    /**
     * 锁操作 ：Lock
     */
    public static void lockOperate() {
        String key1 = "lockkey1";
        RLock lock = redisson.getLock(key1);

        // 阻塞可重入
        lock.lock();
        try {
            // dothing
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        // 阻塞可重入 TTL
        lock.lock(10, TimeUnit.SECONDS);
        try {
            // dothing
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        // 阻塞可重入 tryLock
        boolean hasLock = lock.tryLock();
        if (hasLock) {
            try {
                // dothing
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 锁操作 : Fair Lock
     */
    public static void fairLockOperate() {
        String key1 = "fairLockkey1";
        RLock lock = redisson.getFairLock(key1);

        // 阻塞可重入
        lock.lock();
        try {
            // dothing
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        // 阻塞可重入 TTL
        lock.lock(10, TimeUnit.SECONDS);
        try {
            // dothing
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        // 阻塞可重入 tryLock
        boolean hasLock = lock.tryLock();
        if (hasLock) {
            try {
                // dothing
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 锁操作 : MultiLock
     */
    public static void multiLockOperate() {
        RLock lock1 = redisson.getLock("lockformulti1");
        RLock lock2 = redisson.getLock("lockformulti2");
        RLock lock3 = redisson.getLock("lockformulti3");
        RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);
        // locks: lock1 lock2 lock3
        boolean hasLock = lock.tryLock();
        if (hasLock) {
            try {
                System.out.println("MultiLock dothings");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 锁操作 : RedLock
     */
    public static void redLockOperate() {
        RLock lock1 = redisson.getLock("lockforred1");
        RLock lock2 = redisson.getLock("lockforred2");
        RLock lock3 = redisson.getLock("lockforred3");
        RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);
        // locks: lock1 lock2 lock3
        boolean hasLock = lock.tryLock();
        if (hasLock) {
            try {
                System.out.println("MultiLock dothings");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 锁操作 : ReadWriteLock
     */
    public static void readWriteLockOperate() {
        String key1 = "readWriteLockkey1";
        RReadWriteLock rReadWriteLock = redisson.getReadWriteLock(key1);
        RLock readLock = rReadWriteLock.readLock();
        RLock writeLock = rReadWriteLock.writeLock();

        System.out.println("读锁测试：");
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> {
                try {
                    readLock.lock();
                    System.out.println("线程 " + Thread.currentThread().getId() + " readLock 获得锁：" + System.currentTimeMillis());
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    System.out.println("线程 " + Thread.currentThread().getId() + " readLock 释放锁：" + System.currentTimeMillis());
                    readLock.unlock();
                }
            });
        }

        try {
            Thread.sleep(10000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("写锁测试：");
        ExecutorService executorService2 = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 10; i++) {
            executorService2.submit(() -> {
                try {
                    writeLock.lock();
                    System.out.println("线程 " + Thread.currentThread().getId() + " writeLock 获得锁：" + System.currentTimeMillis());
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    System.out.println("线程 " + Thread.currentThread().getId() + " writeLock 释放锁：" + System.currentTimeMillis());
                    writeLock.unlock();
                }
            });
        }
    }

    /**
     * Semaphore
     */
    public static void semaphoreOperate() {

    }

    /**
     * CountDownLatch
     */
    public static void countDownLatchOperate() {
        String key1 = "countDownLatchKey1";
        RCountDownLatch latch = redisson.getCountDownLatch(key1);
        latch.trySetCount(2);
        System.out.println("start countDown");
        // 由于 Redis 实现，可以实现分布式，其他服务器亦可
        latch.countDown();
        System.out.println("end countDown");
        try {
            System.out.println("start await");
            latch.await();
            System.out.println("end await");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 工具操作
     */
    public static void toolOprate() {
        // 限流器
        rateLimiterOprate();
    }

    /**
     * 工具操作 ：RateLimiter
     */
    public static void rateLimiterOprate() {
        String key1 = "rateLimiterkey1";
        RRateLimiter rateLimiter = redisson.getRateLimiter(key1);
        // 每 5 秒钟产生 3 个令牌
        rateLimiter.trySetRate(RateType.OVERALL, 8, 5, RateIntervalUnit.SECONDS);

        while (true) {
            rateLimiter.acquire(3);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}

/**
 * 学生
 */
class Student implements Serializable {

    private static final long serialVersionUID = -7817644314026112916L;

    int id;

    String name;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Student{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}