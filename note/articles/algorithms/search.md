# 查找

- 目录
    - [二分查找](#二分查找)
    - [二叉查找树](#二叉查找树)
    - [平衡查找树](#平衡查找树)
      - [红黑树](#红黑树)

经典的查找算法主要有三种高效的数据类型实现：二叉查找树、红黑树、散列表。

### 二分查找

基于有序数组的二分查找是最简单也是最常见的查找算法。

下述代码采用迭代的方式：首先将 key 和中间值做比较，如果相等返回索引；如果小于中间值则在左半部分查找；大于则在右半部分查找。

```java
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
```

二分查找（有序链表）区别于顺序查找（基于链表），最明显的优势就是查询效率和空间需求较低，但是相比链表插入操作很慢。

### 二叉查找树

二叉查找树（BST）是一颗二叉树，其中每个节点都含有一个 Comparable 的键（亦可能包含相关联的值）且每个节点的键都大于其左子树的任意节点的键而小于右子树的任意节点的键。
BST 有一个重要性质，就是它的中序遍历结果递增排序。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/algorithms/二叉查找树值有序映射.png" width="300px">
</div>

在二叉树中查找一个键：如果树是空的，则查找未命中；如果被查找的键和根节点相等，则命中；否则我们就递归地在适当地子树中继续查找。

代码实现：

```java
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
```

二叉查找树所有操作在最坏的情况下所需要的时间都和树的高度成正比。但是，二叉查找树在极端情况下会退化成链表，这时候查找会退化成顺序查找。

### 平衡查找树

二叉查找树在极端情况会退化成链表，为此衍生出了平衡二叉树（AVL）。
平衡二叉树本质是一个二叉查找树，但是它又具有以下特点：它是一棵空树或它的左右两个子树的高度差的绝对值不超过 1，并且左右两个子树都是一棵平衡二叉树；在 AVL 树中任何节点的两个子树的高度最大差别为 1。

但是平衡二叉树地要求很严格，经常需要通过一次或两次 "旋转" 才能达到平衡。为了协调达到平衡和查找地效率，产生了一些变种，如：2-3 树、红黑树。

#### 2-3 树

2-3 树引入了 2- 节点和 3- 节点，目的是为了让树平衡。一颗完美平衡的 2-3 查找树的所有空链接到根节点的距离应该是相同的。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/algorithms/2-3树.png" width="300px">
</div>

插入操作和 BST 的插入操作有很大区别，BST 的插入操作是先进行一次未命中的查找，然后再将节点插入到对应的空链接上。但是 2-3 查找树如果也这么做的话，那么就会破坏了平衡性。它是将新节点插入到叶子节点上。

根据叶子节点的类型不同，有不同的处理方式：

- 如果插入到 2- 节点上，那么直接将新节点和原来的节点组成 3- 节点即可。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/algorithms/2-3树插入2-节点.png" width="600px">
</div>

- 如果是插入到 3- 节点上，就会产生一个临时 4- 节点时，需要将 4- 节点分裂成 3 个 2- 节点，并将中间的 2- 节点移到上层节点中。如果上移操作继续产生临时 4- 节点则一直进行分裂上移，直到不存在临时 4- 节点。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/algorithms/2-3树插入3-节点.png" width="600px">
</div>

性质：

2-3 查找树插入操作的变换都是局部的，除了相关的节点和链接之外不必修改或者检查树的其它部分，而这些局部变换不会影响树的全局有序性和平衡性。

2-3 查找树的查找和插入操作复杂度和插入顺序无关，在最坏的情况下查找和插入操作访问的节点必然不超过 logN 个，含有 10 亿个节点的 2-3 查找树最多只需要访问 30 个节点就能进行任意的查找和插入操作。

#### 红黑树

红黑树背后的思想是在普通的二叉树基础上加上一些额外信息来表示 2-3 树。树中的连接分为两种类型：`红连接`将两个 2- 节点连接构成一个 3- 节点；`黑连接`就是普通的 2- 节点之间的链接。
在一颗红黑树中，3- 节点表示为由一条左斜的红链接相连的两个 2- 节点，而右斜的链接全都是黑链接。

红黑树满足以下条件：
- 红链接均为左连接；
- 没有任何一个节点同时和两台红色链接相连；
- 该树是完美的黑色平衡，即任意空链接到根节点的路径上的黑链接数相同。

上述约束，使得红黑树满足 2-3 树的特性，黑链接保证平衡，红链接构建 3- 节点。将所有红节点画平，所展现的结构便是一颗 2-3 树。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/algorithms/红黑树_红链接画平.png" width="600px">
</div>

链接颜色的表示是通过节点实现的。每个节点都有一条指向自己的链接，我们将链接的颜色保存在每个节点中。

在某些操作可能会出现红色右链接或者两条连续的红链接，在操作完成前这些情况都需要通过旋转或变色修复。

- 左旋

因为合法的红链接都为左链接，如果出现右链接为红链接，那么就需要进行左旋操作。



- 右旋

进行右旋转是为了转换两个连续的左红链接，将它们分割到一个节点的两侧。



- 变色

一个 4- 节点在红黑树中表现为一个节点的左右子节点都是红色的。
分裂 4- 节点除了需要将子节点的颜色由红变黑之外，同时需要将父节点的颜色由黑变红，从 2-3 树的角度看就是将中间节点移到上层节点。



- 插入

先将一个节点按二叉查找树的方法插入到正确位置，然后再进行如下颜色操作：

- 如果右子节点是红色的而左子节点是黑色的，进行左旋转；
- 如果左子节点是红色的，而且左子节点的左子节点也是红色的，进行右旋转；
- 如果左右子节点均为红色的，进行颜色转换。
