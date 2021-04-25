# 查找

- 目录
    - [二分查找](#二分查找)

经典的查找算法主要有三种高效的数据类型实现：二叉查找树、红黑树、散列表。

### 二分查找

基于有序数组的二分查找是最简单也是最常见的查找算法。

下述代码采用迭代的方式：首先将 key 和中间值做比较，如果相等返回索引；如果小于中间值则在左半部分查找；大于则在右半部分查找。

```java
public class BinarySearch {

    /**
     * 有序数组
     */
    private static Comparable[] keys = null;

    /**
     * 查找
     *
     * @param a 数组
     * @return
     */
    public static int search(Comparable[] a, Comparable key) {
        keys = a;
        Arrays.sort(keys);
        // 二分查找
        int low = 0, high = keys.length - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            int compare = keys[mid].compareTo(key);
            if (compare == 0) {
                return mid;
            } else if (compare > 0) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return high;
    }

    public static void main(String[] args) {
        Integer[] a = new Integer[]{6, 2, 1, 4, 9, 5};

        System.out.println(search(a, 2));

    }
}
```

二分查找（有序链表）区别于顺序查找（基于链表），最明显的优势就是查询效率和空间需求较低，但是相比链表插入操作很慢。