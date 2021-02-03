import java.util.Comparator;

/**
 * @author: lazecoding
 * @date: 2021/2/3 22:25
 * @description:
 */
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
        System.out.println("\nString softed array print:");
        String[] a = new String[]{"0", "2", "1", "A"};
        Selection.sort(a);
        show(a);

        System.out.println("\nInteger softed array print:");
        Integer[] b = new Integer[]{1, 4, 2, -4};
        Selection.sort(b);
        show(b);
    }
}
