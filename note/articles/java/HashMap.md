# HashMap

- 目录
    - [HashMap 的数据结构](#HashMap-的数据结构)
    - [执行流程](#执行流程)

HashMap 是 Map 接口的实现，此实现提供所有可选的映射操作，但不保证映射的顺序，特别是它不保证该顺序恒久不变，而且 HashMap 非线程安全，即多个线程同时写 HashMap，可能会导致数据的不一致。如果需要满足线程安全，可以用 Collections 的 synchronizedMap 方法使 HashMap 具有线程安全的能力，或者使用 ConcurrentHashMap。

### HashMap 的数据结构

下图是 JDK 1.8 中 HashMap 的数据结构：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/collection/HashMap的数据结构.png" width="600px">
</div>

HashMap 是基于数组、链表和红黑树实现的，它具有较快的查询速度主要得益于它是通过计算散列码来确定存储的位置。HashMap 中是通过 key 的 hashCode 来计算 hash 值的，如果存储的元素对多了，就有可能出现不同的元素具有相同的 hash 值，即 `hash 冲突`。HashMap 底层是通过链表来解决 `hash 冲突` 的。在 JDK 1.8 中，当链表达到一定长度时会转化成红黑树，同样，当红黑树收缩到一定节点数量时也会转化成链表。

HashMap 源码摘选：

```java
// java/util/HashMap.java
public class HashMap<K,V> extends AbstractMap<K,V>
        implements Map<K,V>, Cloneable, Serializable {

    private static final long serialVersionUID = 362498820763181265L;


    /**
     * The default initial capacity - MUST be a power of two.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The load factor used when none specified in constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The bin count threshold for using a tree rather than list for a
     * bin.  Bins are converted to trees when adding an element to a
     * bin with at least this many nodes. The value must be greater
     * than 2 and should be at least 8 to mesh with assumptions in
     * tree removal about conversion back to plain bins upon
     * shrinkage.
     */
    static final int TREEIFY_THRESHOLD = 8;

    /**
     * The bin count threshold for untreeifying a (split) bin during a
     * resize operation. Should be less than TREEIFY_THRESHOLD, and at
     * most 6 to mesh with shrinkage detection under removal.
     */
    static final int UNTREEIFY_THRESHOLD = 6;

    /**
     * The smallest table capacity for which bins may be treeified.
     * (Otherwise the table is resized if too many nodes in a bin.)
     * Should be at least 4 * TREEIFY_THRESHOLD to avoid conflicts
     * between resizing and treeification thresholds.
     */
    static final int MIN_TREEIFY_CAPACITY = 64;

    /**
     * Basic hash bin node, used for most entries.  (See below for
     * TreeNode subclass, and in LinkedHashMap for its Entry subclass.)
     */
    static class Node<K,V> implements Map.Entry<K,V> {
        final int hash;
        final K key;
        V value;
        Node<K,V> next;

        Node(int hash, K key, V value, Node<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public final K getKey()        { return key; }
        public final V getValue()      { return value; }
        public final String toString() { return key + "=" + value; }

        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public final boolean equals(Object o) {
            if (o == this)
                return true;
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>)o;
                if (Objects.equals(key, e.getKey()) &&
                        Objects.equals(value, e.getValue()))
                    return true;
            }
            return false;
        }
    }
    
    /* ---------------- Fields -------------- */

    /**
     * The table, initialized on first use, and resized as
     * necessary. When allocated, length is always a power of two.
     * (We also tolerate length zero in some operations to allow
     * bootstrapping mechanics that are currently not needed.)
     */
    transient Node<K,V>[] table;

    /**
     * Holds cached entrySet(). Note that AbstractMap fields are used
     * for keySet() and values().
     */
    transient Set<Map.Entry<K,V>> entrySet;

    /**
     * The number of key-value mappings contained in this map.
     */
    transient int size;

    /**
     * The number of times this HashMap has been structurally modified
     * Structural modifications are those that change the number of mappings in
     * the HashMap or otherwise modify its internal structure (e.g.,
     * rehash).  This field is used to make iterators on Collection-views of
     * the HashMap fail-fast.  (See ConcurrentModificationException).
     */
    transient int modCount;

    /**
     * The next size value at which to resize (capacity * load factor).
     */
    int threshold;

    /**
     * The load factor for the hash table.
     */
    final float loadFactor;

    // 省略若干方法

}
```

上面是 HashMap 源码，包含了属性变量、Node 内部类、配置变量。

这里有一个非常重要的字段 —— `Node[] table`，即哈希槽数组，它是一个 Node 的数组。Node 是 HashMap 的一个内部类，实现了Map.Entry 接口，本质是就是一个映射(键值对)。HashMap 其实就是一个 Entry 数组，Entry 对象中包含了键和值，其中 next 也是一个 Entry 对象，它就是用来处理 hash 冲突的，形成一个链表。

### 执行流程

下面以 PUT 操作源码来分析 HashMap 的执行流程：

```java
// java/util/HashMap.java#put
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}

// java/util/HashMap.java#hash
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}

// java/util/HashMap.java#putVal
final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
               boolean evict) {
    Node<K, V>[] tab;
    Node<K, V> p;
    int n, i;
    if ((tab = table) == null || (n = tab.length) == 0)
        n = (tab = resize()).length;
    // (n - 1) & hash 寻址Hash槽newNode，如果发生Hash冲突就进行追加
    if ((p = tab[i = (n - 1) & hash]) == null)
        tab[i] = newNode(hash, key, value, null);
    else {
        Node<K, V> e;
        K k;
        if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
            e = p;
        else if (p instanceof TreeNode)
            e = ((TreeNode<K, V>) p).putTreeVal(this, tab, hash, key, value);
        else {
            for (int binCount = 0; ; ++binCount) {
                if ((e = p.next) == null) {
                    p.next = newNode(hash, key, value, null);
                    if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                        treeifyBin(tab, hash);
                    break;
                }
                if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                    break;
                p = e;
            }
        }
        // 若key对应的键值对已经存在，则用新的value取代旧的value,return！
        if (e != null) { // existing mapping for key
            V oldValue = e.value;
            if (!onlyIfAbsent || oldValue == null)
                e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
    }
    // 修改次数+1
    ++modCount;
    // 如果容器元素数量超过容器容量上线执行resize()
    if (++size > threshold)
        resize();
    afterNodeInsertion(evict);
    return null;
}
```

流程分析如图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/collection/HashMap-PUT-流程分析.png" width="600px">
</div>

`①`. 判断键值对数组 table[i] 是否为空或为 null，否则执行 resize() 进行扩容；

`②`. 根据键值 key 计算 hash 值得到插入的数组索引 i，如果 table[i] == null，直接新建节点添加，转 `⑥`，如果 table[i]不 为空，转 `③`；

`③`. 判断 table[i] 的首个元素是否和 key 一样，如果相同直接覆盖 value，否则转向 ④，这里的相同指的是 hashCode 以及 equals；

`④`. 判断 table[i] 是否为 treeNode，即 table[i] 是否是红黑树，如果是红黑树，则直接在树中插入键值对，否则转 `⑤`；

`⑤`. 遍历 table[i]，判断链表长度是否大于 8，大于 8 的话把链表转换为红黑树，在红黑树中执行插入操作，否则进行链表的插入操作；遍历过程中若发现 key 已经存在直接覆盖 value 即可；

`⑥`. 插入成功后，判断实际存在的键值对数量 size 是否超多了最大容量 threshold，如果超过，进行扩容。

可以看到 HashMap 做了一些优化。

#### Hash 算法优化

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

HashMap 对于 Hash 算法做了优化，对于 32 位 hash 值，将低 16 位与高 16 位做异或操作，让低 16 位保持高低 16 位特征，减少 hash 冲突，让元素尽可能分散。

#### 寻址方法优化

```java
if ((tab = table) == null || (n = tab.length) == 0)
    n = (tab = resize()).length;
if ((p = tab[i = (n - 1) & hash]) == null)
    tab[i] = newNode(hash, key, value, null);
else {
    ........
}
```

用与运算代替取模运算，效率更高。(n - 1) & hash，因为 "取余（%）操作中如果除数是 2 的幂次则等价于与其除数减一的与（&）操作（也就是说  `hash%length==hash&(length-1)` 的前提是 length 是 2 的 n 次方；）。" 这也是为什么 HashMap 的长度为什么是 2 的幂次方。

#### 链表转红黑树

```java
/**
 *  存储阈值：小于8时链表节点转化为红黑树节点
 */
static final int TREEIFY_THRESHOLD = 8;

/**
 *  存储阈值：小于6时红黑树节点转化为链表节点
 */
static final int UNTREEIFY_THRESHOLD = 6;

/**
 * 红黑树结构下 Map 最小容量阈值
 */
static final int MIN_TREEIFY_CAPACITY = 64;

/**
 * Replaces all linked nodes in bin at index for given hash unless
 * table is too small, in which case resizes instead.
 */
final void treeifyBin(Node<K,V>[] tab, int hash) {
    int n, index; Node<K,V> e;
    if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
        resize();
    else if ((e = tab[index = (n - 1) & hash]) != null) {
        TreeNode<K,V> hd = null, tl = null;
        do {
            TreeNode<K,V> p = replacementTreeNode(e, null);
            if (tl == null)
                hd = p;
            else {
                p.prev = tl;
                tl.next = p;
            }
            tl = p;
        } while ((e = e.next) != null);
        if ((tab[index] = hd) != null)
            hd.treeify(tab);
    }
}
```

当链表中节点数量大于等于 TREEIFY_THRESHOLD 时，链表会转成红黑树，如果 Node 数组小于 MIN_TREEIFY_CAPACITY 则进行 resize()。

> JDK 1.8 在 HashMap 中引入了红黑树优化性能，但扩容依然是一个特别耗性能的行为，在使用 HashMap 时尽可能初始化一个大致的容量，避免频繁扩容。

### 思考

为什么 HashMap 不直接使用红黑树，而是用链表升级？

因为维护树需要更高的复杂度，在数据量较小的时候，树维护的成本大于遍历链表的成本，故此。而且，针对整体数量较小的时候，数据增长可能会导致频繁扩容，这给红黑树的维护带来了更多压力。
