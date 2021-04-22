# 排序

- 目录
    - [模板](#模板)
    - [重要指标](#重要指标)
    - [选择排序](#选择排序)
    - [插入排序](#插入排序)
    - [希尔排序](#希尔排序)
    - [归并排序](#归并排序)
    - [快速排序](#快速排序)


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

### 插入排序

思路：首先当前位置之前的元素有序的，拿当前位置的元素和前一个元素比较，如果小于之前的元素，则交换位置，继续比较...如此反复。

分析：与选择排序一样，当前索引左边的所有元素都是有序的，但它们的最终位置还不确定，为了给更小的元素腾出空间，它们可能会被移动。但是当索引到达数组的右端时，数组排序就完成了。
和选择排序不同的是，插人排序所需的时间取决于输人中元素的初始顺序。例如，对一个很大且其中的元素已经有序(或接近有序)的数组进行排序将会比对随机顺序的数组或是逆序数组进行排序要快得多。

对于随机排列的长度为 N 且主键不重复的数组，平均情况下插入排序需要 ~(N^2)/4 次比较以及 ~(N^2)/4 次交换。最坏情况下需要 ~(N^2)/2 次比较与 ~(N^2)/2 次交换，最好情况下需要 N-1 次比较与 0 次交换。

实现：

```java
public class Insertion {
    /**
     * 排序实现
     *
     * @param a 待排序数组
     */
    public static void sort(Comparable[] a) {
        int length = a.length;
        for (int i = 1; i < length; i++) {
            // 将 a[i] 插入到a [i-1]、a[i-2]、a[i-3]... 之中
            for (int j = i; j > 0 && less(a[j], a[j - 1]); j--) {
                exch(a, j, j - 1);
            }
        }
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
        assert isSort(arr);
        show(arr);
    }
}
```

### 希尔排序

插入排序对于大规模乱序数组插入排序很慢，因为它只会交换相邻的元素，元素只能一点一点从数组的一端移动到另一端。

希尔排序是一种基于插入排序的快速排序算法，交换不相邻的元素以对数组的局部进行排序，并最终用插入排序将局部有序的数组排序。

思想：希尔排序的思想是使数组中任意间隔为 h 的元素都是有序的，这样的数组被称为 `h 有序数组`。即一个 `h 有序数组`就是 h 个互相独立的有序数组编织在一起组成一个数组。在进行排序时，如果 h 很大，就能将元素移动到很远的地方，为实现更小的h有序创造方便。
用这种方式，对任意以 1 结尾的 h 序列，都能将数组排序，这就是希尔排序。(注意：此处的 h 有序，不是指连续 h 个元素有序，而是每间隔 h 个元素有序)

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/algorithms/希尔排序_h有序数组.png" width="600px">
</div>

实现希尔排序是对于每个 h，用插入排序将 h 个子数组独立地排序。但因为子数组是相互独立的，一个更简单的方法是在h子数组中将每个元素交换到比它大的元素之前去。只需要在插入排序的代码中将移动元素的距离从 1 改为 h 即可。这样，希尔排序的实现就转化为了一个类似插入排序但使用不同增量的过程。
随着排序循环进行，h 不断缩小，直到 h 小于 1 时，排序完毕。

实现：

示例中 h 等于 length / 3 开始递减至 1。

```java
public class Shell {
    /**
     * 排序实现
     *
     * @param a 待排序数组
     */
    public static void sort(Comparable[] a) {
        int length = a.length;
        int h = 1;
        while (h < length / 3) {
            h = 3 * h + 1;
        }
        while (h >= 1) {
            for (int i = h; i < length; i++) {
                for (int j = i; (j >= h) && less(a[j], a[j - h]); j -= h) {
                    exch(a, j, j - h);
                }
            }
            h = h / 3;
        }
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

希尔排序更高效的原因是它权衡了子数组的规模和有序性。排序之初，每个子数组都很短，排序之后子数组都是部分有序的，这两种情况都很适合插入排序。

### 归并排序

`归并`是指将两个有序的数组归并成个更大的有序数组，基于这种思想演进出了`归并排序`。

归并的最简单实现就是把两个不同数组归并到第三个数组中，应用再归并排序中，可以将数组的左右两部分先复制到另一个数组中，再把归并结构放回原数组。

归并实现：

```java
/**
 * 归并所需的辅助数组
 */
private static Comparable[] aux;

/**
 * 将 a[low..mid] 和 a[mid+1..high] 归并
 */
public static void merge(Comparable[] a, int low, int mid, int high) {
    int i = low, j = mid + 1;
    // 将 a[low..high] 复制到 aux[low..high]
    for (int k = low; k <= high; k++) {
        aux[k] = a[k];
    }
    // 归并回到 a[low..high]
    for (int k = low; k <= high; k++) {
        if (i > mid) {
            // 左半边元素用完（取右半边元素）
            a[k] = aux[j++];
        } else if (j > high) {
            // 右半边元素用完（取左半边元素）
            a[k] = aux[i++];
        } else if (less(aux[j], aux[i])) {
            // 右半边元素小于左半边元素（取右半边元素）
            a[k] = aux[j++];
        } else {
            // 左半边元素小于右半边元素（取左半边元素）
            a[k] = aux[i++];
        }
    }
}
```

该实现将所有元素复制到 aux[] 中再归并回 a[] 中。再归并过程中进行了 4 个条件判断： 左半边元素用完（取右半边元素）、 右半边元素用完（取左半边元素）、右半边元素小于左半边元素（取右半边元素）、左半边元素小于右半边元素（取左半边元素）。

基于归并的实现，产生了两种归并排序：

- 自顶而下：分治思想，将一个大问题分割成小问题分别解决，然后用所有小问题的答案来解决整个大问题。
- 自底而上：先归并那些微型数组，然后再成对归并得到的子数组，如此，直到我们将整个数组归并在一起。

归并排序（自顶而下）：

```java
public class Merge {
    /**
     * 归并所需的辅助数组
     */
    private static Comparable[] aux;

    /**
     * 将 a[low..mid] 和 a[mid+1..high] 归并
     */
    public static void merge(Comparable[] a, int low, int mid, int high) {
        int i = low, j = mid + 1;
        // 将 a[low..high] 复制到 aux[low..high]
        for (int k = low; k <= high; k++) {
            aux[k] = a[k];
        }
        // 归并回到 a[low..high]
        for (int k = low; k <= high; k++) {
            if (i > mid) {
                // 左半边元素用完（取右半边元素）
                a[k] = aux[j++];
            } else if (j > high) {
                // 右半边元素用完（取左半边元素）
                a[k] = aux[i++];
            } else if (less(aux[j], aux[i])) {
                // 右半边元素小于左半边元素（取右半边元素）
                a[k] = aux[j++];
            } else {
                // 左半边元素小于右半边元素（取左半边元素）
                a[k] = aux[i++];
            }
        }
    }

    /**
     * 排序实现
     *
     * @param a 待排序数组
     */
    public static void sort(Comparable[] a) {
        aux = new Comparable[a.length];
        sort(a, 0, a.length - 1);
    }

    /**
     * 将数组 a[low..high] 排序 (自顶而下)
     */
    private static void sort(Comparable[] a, int low, int high) {
        if (high <= low) {
            return;
        }
        int mid = low + (high - low) / 2;
        // 将左半边排序
        sort(a, low, mid);
        // 将右半边排序
        sort(a, mid + 1, high);
        // 归并结果
        merge(a, low, mid, high);
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

归并排序（自底而上）：

```java
/**
 * 归并排序（自底而上）
 *
 * @param a
 */
public static void sort(Comparable[] a) {
    int length = a.length;
    aux = new Comparable[length];
    // size：子数组大小
    for (int size = 1; size < length; size = size + size) {
        // low：子数组索引
        for (int low = 1; low < length - size; low += size + size) {
            merge(a, low, low + size - 1, Math.min(low + size + size - 1, length - 1));
        }
    }
}
```

### 快速排序

快速排序是一种分治的排序算法。它将一个数组分成两个子数组，将两部分独立地排序。

快速排序和归并排序是互补的：归并排序将数组分成两个子数组分别排序，并将有序的子数组归并以将整个数组排序；而快速排序则是当两个子数组都有序时整个数组也就自然有序了。
第一种情况中，递归调用发生在处理整个数组之前；在第二种情况中，递归调用发生在处理整个数组之后。在归并排序中，一个数组被等分为两半；在快速排序中，切分的位置取决于数组的内容。快速排序的大致过程如下图所示。

快速排序示意图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/algorithms/快速排序示意图.png" width="600px">
</div>

快速排序递归地对子数组排序，选取一个标准值（第一个索引），小于等于标准值地在左面，大于标准值地在右面。

实现：

```java
public class Quick {
  /**
   * 排序实现
   *
   * @param a 待排序数组
   */
  public static void sort(Comparable[] a) {
    sort(a, 0, a.length - 1);
  }

  /**
   * 排序
   */
  private static void sort(Comparable[] a, int low, int high) {
    if (high <= low) {
      return;
    }
    // 切分数组并返回分割点索引
    int j = partition(a, low, high);
    // 将左半部分 a[low..j-1] 排序
    sort(a, low, j - 1);
    // 将右半部分 a[j+1..high] 排序
    sort(a, j + 1, high);
  }

  /**
   * 将数组切分为 a[low..j-1]，a[i]，a[j+1..high]
   */
  private static int partition(Comparable[] a, int low, int high) {
    int i = low, j = high;
    // 第一个元素为标准数
    Comparable stard = a[low];
    while (i < j) {
      // 比 stard 小的在左面，比 stard 大的在右面
      while (i < j && stard.compareTo(a[j]) <= 0) {
        j--;
      }
      a[i] = a[j];
      while (i < j && a[j].compareTo(stard) <= 0) {
        i++;
      }
      a[j] = a[i];
    }
    // 此时 i = j，数组切分完毕
    a[i] = stard;
    return j;
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
    System.out.println(indexI + "  " + indexJ);
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
    Integer[] arr = new Integer[]{2, 1, 6, 2, 3, 4, 5};
    sort(arr);
    // 编译器默认不适用 assert 检测（但是junit测试中适用），所以要使用时要添加参数虚拟机启动参数 -ea 具体添加过程
    assert isSort(arr);
    show(arr);
  }
}
```

快速排序切分方法的内循环会用一个递增的索引将数组元素和一个定值比较。这种简洁性也是快速排序的一个优点，很难想象排序算法中还能有比这更短小的内循环了。

快速排序另一个速度优势在于它的比较次数很少。排序效率最终还是依赖切分数组的效果，而这依赖于切分元素的值。切分将一个较大的随机数组分成两个随机子数组，而实际上这种分割可能发生在数组的任意位置（对于元素不重复的数组而言）。