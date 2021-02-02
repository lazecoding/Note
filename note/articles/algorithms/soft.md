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