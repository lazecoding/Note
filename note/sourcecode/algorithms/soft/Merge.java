package brush.algorithms.soft;

/**
 * @author: lazecoding
 * @date: 2021/4/19 21:13
 * @description: 归并排序
 */
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
     * 归并排序（自底而上）
     *
     * @param a
     */
    /*public static void sort(Comparable[] a) {
        int length = a.length;
        aux = new Comparable[length];
        // size：子数组大小
        for (int size = 1; size < length; size = size + size) {
            // low：子数组索引
            for (int low = 1; low < length - size; low += size + size) {
                merge(a, low, low + size - 1, Math.min(low + size + size - 1, length - 1));
            }
        }
    }*/

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
