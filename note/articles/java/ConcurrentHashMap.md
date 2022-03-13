# ConcurrentHashMap

JDK 1.5 引入了许多并发容器，比如引入 ConcurrentHashMap 来取代 HashTable，并且新的同步容器增加了复合操作。

`java/util/concurrent/ConcurrentHashMap.java` 在 JDK 1.7 之前采用的是分段锁：一个 ConcurrentHashMap 包含多个 Segment 数组，每个数组为单位加锁；从 JDK 1.8 开始采用 CAS + synchronized：多个线程采用 CAS 算法访问同一个槽，当发现存在并发写则通过 synchronized 关键字对这个槽加锁，这种方式 ConcurrentHashMap 带来了更高的并发能力。

ConcurrentHashMap 借鉴了一部分 HashMap 的特性，可以查看 [HashMap](https://github.com/lazecoding/Note/blob/main/note/articles/java/HashMap.md) 。

下面以 PUT 操作为例，展示 ConcurrentHashMap 原理。

### putVal

ConcurrentHashMap.java#putVal：

```java
// java/util/concurrent/ConcurrentHashMap.java#putVal
/** Implementation for put and putIfAbsent */
final V putVal(K key, V value, boolean onlyIfAbsent) {
    if (key == null || value == null) throw new NullPointerException();
    int hash = spread(key.hashCode());
    int binCount = 0;
    for (Node<K,V>[] tab = table;;) {
        Node<K,V> f; int n, i, fh;
        if (tab == null || (n = tab.length) == 0)
            tab = initTable();
        else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
            if (casTabAt(tab, i, null,
                         new Node<K,V>(hash, key, value, null)))
                break;                   // no lock when adding to empty bin
        }
        else if ((fh = f.hash) == MOVED)
            tab = helpTransfer(tab, f);
        else {
            V oldVal = null;
            synchronized (f) {
                if (tabAt(tab, i) == f) {
                    if (fh >= 0) {
                        binCount = 1;
                        for (Node<K,V> e = f;; ++binCount) {
                            K ek;
                            if (e.hash == hash &&
                                ((ek = e.key) == key ||
                                 (ek != null && key.equals(ek)))) {
                                oldVal = e.val;
                                if (!onlyIfAbsent)
                                    e.val = value;
                                break;
                            }
                            Node<K,V> pred = e;
                            if ((e = e.next) == null) {
                                pred.next = new Node<K,V>(hash, key,
                                                          value, null);
                                break;
                            }
                        }
                    }
                    else if (f instanceof TreeBin) {
                        Node<K,V> p;
                        binCount = 2;
                        if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key,
                                                       value)) != null) {
                            oldVal = p.val;
                            if (!onlyIfAbsent)
                                p.val = value;
                        }
                    }
                }
            }
            if (binCount != 0) {
                if (binCount >= TREEIFY_THRESHOLD)
                    treeifyBin(tab, i);
                if (oldVal != null)
                    return oldVal;
                break;
            }
        }
    }
    addCount(1L, binCount);
    return null;
}
```

`ConcurrentHashMap.java#putVal` 用于执行 put 和 putIfAbsent 操作，我们在源码中可以看到 `synchronized (f)`，也就是 ConcurrentHashMap 是针对 Node 加同步锁的，也就是针对每个槽。当多个线程访问同一个槽，采用 CAS 算法，之后又一个线程成功，这时候其他线程会发现有人修改了，就会再这个槽上加锁。