package brush.algorithms.search;

import java.util.Arrays;

/**
 * @author: lazecoding
 * @date: 2021/4/25 22:32
 * @description: 二分查找
 */
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
