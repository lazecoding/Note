package brush.algorithms.soft;

/**
 * @author: lazecoding
 * @date: 2021/4/23 23:06
 * @description: 最大堆
 */
public class MaxHeap<K extends Comparable<K>> {

    /**
     * 堆数据
     */
    private K[] pq;

    /**
     * 堆大小
     */
    private int size = 0;

    public MaxHeap(int maxN) {
        pq = (K[]) new Comparable[maxN + 1];
    }

    /**
     * 获取堆大小
     * @return
     */
    public int size() {
        return size;
    }

    /**
     * 是否是空堆
     * @return
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 插入元素
     */
    public void insert(K v) {
        pq[++size] = v;
        swim(size);
    }

    /**
     * 删除最大值
     */
    public K delMax() {
        K max = pq[1];
        exch(1, size--);
        pq[size + 1] = null;
        sink(1);
        return max;
    }

    private boolean less(int i, int j) {
        return pq[i].compareTo(pq[j]) < 0;
    }

    /**
     * 交换数据
     */
    private void exch(int i, int j) {
        K t = pq[i];
        pq[i] = pq[j];
        pq[j] = t;
    }

    /**
     * 节点上浮
     */
    private void swim(int k) {
        while (k > 1 && less(k / 2, k)) {
            exch(k / 2, k);
            k = k / 2;
        }
    }

    /**
     * 节点下沉
     */
    private void sink(int k) {
        while (2 * k <= size) {
            int j = 2 * k;
            if (j < size && less(j, j + 1)) {
                j++;//larger children
            }
            if (!less(k, j)) {
                break;
            }
            exch(k, j);
            k = j;
        }
    }

    public static void main(String[] args) {
        MaxHeap<Integer> heap = new MaxHeap<>(7);
        heap.insert(3);
        heap.insert(1);
        heap.insert(2);
        heap.insert(4);
        heap.insert(6);
        heap.insert(5);
        System.out.println("start size:" + heap.size);
        while (!heap.isEmpty()) {
            System.out.println("remove >>" + heap.delMax() + " size:" + heap.size);
        }
        System.out.println("end ...");
    }
}
