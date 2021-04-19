package brush.algorithms.soft;

/**
 * @author: lazecoding
 * @date: 2021/4/19 21:18
 * @description: 插入排序
 */
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
