package brush.algorithms.search;

import java.util.Queue;

/**
 * @author: lazecoding
 * @date: 2021/4/26 22:08
 * @description: 二叉查找树
 */
public class BST<Key extends Comparable<Key>, Value> {
    /**
     * 定义根节点
     */
    private Node root;

    /**
     * 节点
     */
    private class Node {
        /**
         * 键
         */
        private Key key;
        /**
         * 值
         */
        private Value val;
        /**
         * 左、右子树
         */
        private Node left, right;
        /**
         * 以该节点为根的子树中节点总数
         */
        private int N;

        public Node(Key key, Value val, int N) {
            this.key = key;
            this.val = val;
            this.N = N;
        }
    }

    /**
     * 二叉树节点总数
     */
    public int size() {
        return size(root);
    }

    /**
     * 某个节点及其子节点数量
     **/
    private int size(Node x) {
        if (x == null) {
            return 0;
        }
        return x.N;
    }

    /**
     * 查找
     */
    public Value get(Key key) {
        return get(root, key);
    }

    /**
     * 从某个节点开始查找
     */
    private Value get(Node x, Key key) {
        if (x == null) {
            return null;
        }
        int cmp = key.compareTo(x.key);
        if (cmp < 0) {
            return get(x.left, key);
        } else if (cmp > 0) {
            return get(x.right, key);
        } else {
            return x.val;
        }
    }

    /**
     * （按序）插入
     */
    public void put(Key key, Value val) {
        //查找key,找到则更新它的值，否则为他创建一个新的结点
        root = put(root, key, val);
    }

    /**
     * （按序）从某个节点插入
     */
    private Node put(Node x, Key key, Value val) {
        //如果key存在于以x为根结点的子树中则更新它的值;
        //否则将以key和val为键值对的新结点插入到该子树中
        if (x == null) {
            return new Node(key, val, 1);
        }
        int cmp = key.compareTo(x.key);
        if (cmp < 0) {
            x.left = put(x.left, key, val);
        } else if (cmp > 0) {
            x.right = put(x.right, key, val);
        } else {
            x.val = val;
        }
        x.N = size(x.left) + size(x.right) + 1;
        return x;
    }

    /**
     * 最小值
     */
    public Key min() {
        return min(root).key;
    }

    /**
     * 从某个节点开始获取最小值
     */
    private Node min(Node x) {
        if (x.left == null) {
            return x;
        }
        return min(x.left);
    }

    /**
     * 最大值
     */
    public Key max() {
        return max(root).key;
    }

    /**
     * 从某个节点开始获取最大值
     */
    private Node max(Node x) {
        if (x.right == null) {
            return x;
        }
        return max(x.right);
    }

    /**
     * 删除最小键
     */
    public void deleteMin() {
        root = deleteMin(root);
    }

    /**
     * 从某个节点开始删除最小键
     */
    private Node deleteMin(Node x) {
        if (x.left == null) {
            return x.right;
        }
        x.left = deleteMin(x.left);
        x.N = size(x.left) + size(x.right) + 1;
        return x;
    }

    /**
     * 删除某个键
     */
    public void delete(Key key) {
        root = delete(root, key);
    }

    /**
     * 删除某个节点下的某个键
     */
    private Node delete(Node x, Key key) {
        if (x == null) {
            return null;
        }
        int cmp = key.compareTo(x.key);
        if (cmp < 0) {
            x.left = delete(x.left, key);
        } else if (cmp > 0) {
            x.right = delete(x.right, key);
        } else {
            if (x.right == null) {
                return x.left;
            }
            if (x.left == null) {
                return x.right;
            }
            Node t = x;
            x = min(t.right);
            x.right = deleteMin(t.right);
            x.left = t.left;
        }
        x.N = size(x.left) + size(x.right) + 1;
        return x;
    }

    public static void main(String[] args) {
        BST<Integer,String> bst = new BST();
        System.out.println(bst.size());
        bst.put(1, "一");
        bst.put(2, "二");
        bst.put(3, "三");
        bst.put(4, "四");
        bst.put(5, "无");
        bst.put(6, "六");
        bst.put(7, "七");
        bst.put(8, "八");
        System.out.println(bst.size());
        System.out.println(bst.get(1));
        bst.delete(1);
        System.out.println(bst.get(1));
    }
}