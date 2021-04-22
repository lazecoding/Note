package brush.algorithms.soft;

/**
 * @author: lazecoding
 * @date: 2021/4/19 21:13
 * @description: 快速排序
 */
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
