/**
 * @author: lazecoding
 * @date: 2021/5/20 21:37
 * @description: 红黑树
 */
public class RedBlackBST<Key extends Comparable<Key>, Value> {

    private Node root;

    private static final boolean RED = true;
    private static final boolean BLACK = false;

    /**
     * 节点
     */
    private class Node {
        Key key;
        Value val;
        Node left, right;
        int size;
        boolean color;

        public Node(Key key, Value val, int size, boolean color) {
            this.key = key;
            this.val = val;
            this.size = size;
            this.color = color;
        }
    }

    /**
     * 是不是红链接
     *
     * @param x
     * @return
     */
    private boolean isRed(Node x) {
        if (x == null) {
            return false;
        }
        return x.color == RED;
    }

    /**
     * 左旋
     */
    public Node rotateLeft(Node h) {
        Node x = h.right;
        h.right = x.left;
        x.left = h;
        x.color = h.color;
        h.color = RED;
        x.size = h.size;
        h.size = 1 + size(h.left) + size(h.right);
        return x;
    }

    /**
     * 右旋
     */
    public Node rotateRight(Node h) {
        Node x = h.left;
        h.left = x.right;
        x.right = h;
        x.color = h.color;
        h.color = RED;
        x.size = h.size;
        h.size = 1 + size(h.left) + size(h.right);
        return x;
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
        return x.size;
    }

    /**
     * 颜色变换
     */
    void flipColors(Node h) {
        h.color = RED;
        h.left.color = BLACK;
        h.right.color = BLACK;
    }

    /**
     * 新增节点
     */
    public void put(Key key, Value value) {
        // 查找 key ，找到则更新，否则新建
        root = put(root, key, value);
        root.color = BLACK;
    }

    /**
     * 新增节点
     */
    private Node put(Node h, Key key, Value value) {
        if (h == null) {
            // 插入操作，和父节点用红链接相连
            return new Node(key, value, 1, RED);
        }

        int compp = key.compareTo(h.key);

        if (compp < 0) {
            h.left = put(h.left, key, value);
        } else if (compp > 0) {
            h.right = put(h.right, key, value);
        } else {
            h.val = value;
        }

        // 如果右子节点是红色的而左子节点是黑色的，进行左旋转；
        // 如果左子节点是红色的，而且左子节点的左子节点也是红色的，进行右旋转；
        // 如果左右子节点均为红色的，进行颜色转换。
        if (isRed(h.right) && !isRed(h.left)) {
            h = rotateLeft(h);
        }
        if (isRed(h.left) && isRed(h.left.left)) {
            h = rotateRight(h);
        }
        if (isRed(h.left) && !isRed(h.right)) {
            rotateLeft(h);
        }

        h.size = 1 + size(h.left) + size(h.right);
        return h;
    }

    // 删除操作 略 ....

}
