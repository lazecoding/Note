# 排序

排序就是将一组对象按照某种逻辑重新排列的过程。

### 模板

我们约定：
- sort() 用于排序；
- less() 用于比较大小；
- exch() 用于元素替换；
- show() 用于打印元素；
- isSort() 用于判断是否有序；

排序模板 Example：

```java
public class Example {
    /**
     * 排序实现
     *
     * @param arr 待排序数组
     */
    public static void sort(Comparable[] arr) {
        // 各种各样的排序代码
    }

    /**
     * 比较两个元素的大小
     *
     * @param comparableA 待比较元素A
     * @param comparableB 待比较元素B
     * @return 若 A < B,返回 true,否则返回 false
     */
    private static boolean less(Comparable comparableA, Comparable comparableB) {
        return comparableA.compareTo(comparableB) < 0;
    }

    /**
     * 将两个元素交换位置
     *
     * @param arr    待交换元素所在的数组
     * @param indexI 第一个元素索引
     * @param indexJ 第二个元素索引
     */
    private static void exch(Comparable[] arr, int indexI, int indexJ) {
        Comparable temp = arr[indexI];
        arr[indexI] = arr[indexJ];
        arr[indexJ] = temp;
    }

    /**
     * 打印数组的内容
     *
     * @param arr 待打印的数组
     */
    private static void show(Comparable[] arr) {
        for (int index = 0; index < arr.length; index++) {
            System.out.print(arr[index] + " ");
        }
        System.out.println();
    }

    /**
     * 判断数组是否有序
     *
     * @param arr 待判断数组
     * @return 若数组有序，返回 true，否则返回 false
     */
    public static boolean isSort(Comparable[] arr) {
        for (int index = 1; index < arr.length; index++) {
            if (less(arr[index], arr[index - 1])) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        Integer[] arr = new Integer[]{1, 6, 3, 4, 5};
        sort(arr);
        // 编译器默认不适用 assert 检测（但是junit测试中适用），所以要使用时要添加参数虚拟机启动参数 -ea 具体添加过程
        assert isSort(arr);
        show(arr);
    }
}
```

### 重要指标

对于排序算法的重要指标：
- 验证： 推荐在测试代码中添加一条语句 assert isSort(arr); 来确认排序后数组元素都是有序的。
- 运行时间： 要评估算法的性能。首先，要计算各个排序算法在不同的随机输入下的基本操作的次数（包括比较和交换，或者是读写数组的次数）；然后，我们用这些数据来估计算法的相对性能
- 额外的内存使用： 排序算法的额外内存开销和运行时间是同等重要的。
- 数据类型： 我们的排序算法模板适用于任何实现了 Comparable 接口的数据类型。


### 选择排序

思路：首先找到数组中最小的那个元素，其次将它和数组的第一个元素交换位置。再次，再剩下的元素中找到最小的元素，将它和数组第二个元素交换位置 ... 如此反复。

分析：选择排序的内循环只是比较当前元素与目前已知的最小元素，每次交换都能拍排定一个元素，因此交换的总次数是 N，算法的时间效率取决于比较次数。

对于长度为 N 的数组，选择排序需要大约 N^2/2 次比较和 N 次交换，它有两个鲜明特征：
- 运行时间和输入无关：无论原数组是否有序，为了找出最小元素都需要扫描一次数组。
- 数据移动是最少的：每次交换都会排定一个元素。

实现：

```java
public class Selection {

    /**
     * selection sort
     */
    public static void sort(Comparable[] a) {
        int length = a.length;
        for (int i = 0; i < length; i++) {
            int min = i;
            for (int j = i + 1; j < length; j++) {
                if (less(a[j], a[min])) {
                    min = j;
                }
            }
            exch(a, i, min);
            assert isSorted(a, 0, i);
        }
        assert isSorted(a);
    }

    /**
     * use a custom order and Comparator interface
     */
    public static void sort(Object[] a, Comparator c) {
        int length = a.length;
        for (int i = 0; i < length; i++) {
            int min = i;
            for (int j = i + 1; j < length; j++) {
                if (less(c, a[j], a[min])) {
                    min = j;
                }
            }
            exch(a, i, min);
            assert isSorted(a, c, 0, i);
        }
        assert isSorted(a, c);
    }

    /**
     * is v < w ?
     */
    private static boolean less(Comparable v, Comparable w) {
        return (v.compareTo(w) < 0);
    }

    /**
     * is v < w ?
     */
    private static boolean less(Comparator c, Object v, Object w) {
        return (c.compare(v, w) < 0);
    }

    /**
     * exchange a[i] and a[j]
     */
    private static void exch(Object[] a, int i, int j) {
        Object swap = a[i];
        a[i] = a[j];
        a[j] = swap;
    }

    /**
     * is the array a[] sorted ?
     */
    private static boolean isSorted(Comparable[] a) {
        return isSorted(a, 0, a.length - 1);
    }

    // is the array sorted from a[lo] to a[hi]

    /**
     * is the array sorted from a[lo] to a[hi]
     */
    private static boolean isSorted(Comparable[] a, int lo, int hi) {
        for (int i = lo + 1; i <= hi; i++) {
            if (less(a[i], a[i - 1])) {
                return false;
            }
        }
        return true;
    }

    /**
     * is the array a[] sorted ?
     */
    private static boolean isSorted(Object[] a, Comparator c) {
        return isSorted(a, c, 0, a.length - 1);
    }

    /**
     * is the array sorted from a[lo] to a[hi]
     */
    private static boolean isSorted(Object[] a, Comparator c, int lo, int hi) {
        for (int i = lo + 1; i <= hi; i++) {
            if (less(c, a[i], a[i - 1])) {
                return false;
            }
        }
        return true;
    }

    /**
     * print array to standard output
     */
    private static void show(Comparable[] a) {
        for (int i = 0; i < a.length; i++) {
            System.out.println(a[i]);
        }
    }

    /**
     * Test
     */
    public static void main(String[] args) {
        System.out.println("String softed array print:");
        String[] a = new String[]{"0", "2", "1", "A"};
        Selection.sort(a);
        show(a);

        System.out.println("\nInteger softed array print:");
        Integer[] b = new Integer[]{1, 4, 2, -4};
        Selection.sort(b);
        show(b);
    }
}
/* Output:
String softed array print:
0
1
2
A

Integer softed array print:
-4
1
2
4
*/
```
