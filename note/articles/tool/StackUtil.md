# StackUtil

栈链路日志：获取方法调用链路。

源码文件地址：[StackUtil.java](https://github.com/lazecoding/Note/blob/main/note/sourcecode/tool/StackUtil.java)

StackUtil 包含一个内部类 -- `StackMap`，这是一个 TreeMap，它是一棵有序树，根据栈调用顺序排序，用来收集栈链路信息。

StackUtil 提高了两类 API：getStackTraceContain 和 getStackTraceFilter。顾名思义，getStackTraceContain 只收集包含某些字符串的链路信息，getStackTraceFilter 排除包含某些字符串的链路信息。同时还额外地提供了 getStackTraceMerge 方法，意为 `不包含任意一个 filter,而且必须包含任意一个 contain`。

使用 StackUtil，你可以获取通过 getXXX 获取 StackMap，也可通过 printXXX 打印链路信息，而且可以重写 print 放决定如何记录日志。

源码：

```java
public class StackUtil {
    /**
     * 禁止实例化
     */
    private StackUtil() {
    }

    /**
     * 获取栈链路
     *
     * @return TreeMap
     */
    public static TreeMap getStackTrace() {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return StackMap.getNilInstance();
        }
        String threadName = Thread.currentThread().getName();
        /**
         *  栈链路信息
         */
        StackMap stackMap = StackMap.getInstance();
        String stack = "";
        int step = elements.length - 2;
        for (int i = 2; i < elements.length; i++) {
            if (step == 1) {
                stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
            } else if (step == elements.length - 2) {
                stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
            } else {
                stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
            }
            stackMap.put(step, stack + "\n");
            step--;
        }
        return stackMap;
    }

    /**
     * 打印栈链路  （根据系统日志框架自行调整 print方法）
     */
    public static void printStackTrace() {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        /**
         *  栈链路信息
         */
        String stack = "";
        int step = elements.length - 2;
        for (int i = 2; i < elements.length; i++) {
            if (step == 1) {
                stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
            } else if (step == elements.length - 2) {
                stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
            } else {
                stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
            }
            print(stack);
            step--;
        }
    }

    /**
     * 获取栈链路（过滤包含某个字符串）
     *
     * @param filter 过滤字符串
     * @return TreeMap
     */
    public static TreeMap getStackTraceFilter(String filter) {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return StackMap.getNilInstance();
        }
        String threadName = Thread.currentThread().getName();
        /**
         *  栈链路信息
         */
        StackMap stackMap = StackMap.getInstance();
        String stack = "";
        int step = elements.length - 2;
        for (int i = 2; i < elements.length; i++) {
            if (filter != null && filter.length() > 0 && elements[i].toString().indexOf(filter) != -1) {
                // do nothing
            } else {
                if (step == 1) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
                } else if (step == elements.length - 2) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
                } else {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
                }
                stackMap.put(step, stack + "\n");
            }
            step--;
        }
        return stackMap;
    }

    /**
     * 打印栈链路（过滤包含某个字符串）
     *
     * @param filter 过滤字符串
     */
    public static void printStackTraceFilter(String filter) {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        String stack = "";
        int step = elements.length - 2;
        for (int i = 2; i < elements.length; i++) {
            if (filter != null && filter.length() > 0 && elements[i].toString().indexOf(filter) != -1) {
                // do nothing
            } else {
                if (step == 1) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
                } else if (step == elements.length - 2) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
                } else {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
                }
                print(stack);
            }
            step--;
        }
    }

    /**
     * 获取栈链路（过滤包含任意一个过滤字符串）
     *
     * @param filters 过滤字符串列表
     * @return TreeMap
     */
    public static TreeMap getStackTraceFilter(List<String> filters) {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return StackMap.getNilInstance();
        }
        String threadName = Thread.currentThread().getName();
        /**
         *  栈链路信息
         */
        StackMap stackMap = StackMap.getInstance();
        String stack = "";
        int step = elements.length - 2;
        boolean isFilter;
        // 是否跳过该记录 ->filters
        boolean isPass;
        for (int i = 2; i < elements.length; i++) {
            isPass = false;
            if (filters != null && filters.size() > 0) {
                isFilter = true;
                int f = 0;
                while (isFilter) {
                    if (f < filters.size()) {
                        if (elements[i].toString().indexOf(filters.get(f)) != -1) {
                            isFilter = false;
                            isPass = true;
                        }
                    } else {
                        isFilter = false;
                    }
                    f++;
                }
            }
            if (isPass) {
                // do nothing
            } else {
                if (step == 1) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
                } else if (step == elements.length - 2) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
                } else {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
                }
                stackMap.put(step, stack + "\n");
            }
            step--;
        }
        return stackMap;
    }

    /**
     * 打印栈链路（过滤包含任意一个过滤字符串）
     *
     * @param filters 过滤字符串列表
     */
    public static void printStackTraceFilter(List<String> filters) {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        String stack = "";
        int step = elements.length - 2;
        boolean isFilter;
        // 是否跳过该记录 ->filters
        boolean isPass;
        for (int i = 2; i < elements.length; i++) {
            isPass = false;
            if (filters != null && filters.size() > 0) {
                isFilter = true;
                int f = 0;
                while (isFilter) {
                    if (f < filters.size()) {
                        if (elements[i].toString().indexOf(filters.get(f)) != -1) {
                            isFilter = false;
                            isPass = true;
                        }
                    } else {
                        isFilter = false;
                    }
                    f++;
                }
            }
            if (isPass) {
                // do nothing
            } else {
                if (step == 1) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
                } else if (step == elements.length - 2) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
                } else {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
                }
                print(stack);
            }
            step--;
        }
    }

    /**
     * 获取栈链路(包含某个字符串)
     *
     * @param contain 包含字符串
     * @return TreeMap
     */
    public static TreeMap getStackTraceContain(String contain) {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return StackMap.getNilInstance();
        }
        String threadName = Thread.currentThread().getName();
        /**
         *  栈链路信息
         */
        StackMap stackMap = StackMap.getInstance();
        String stack = "";
        int step = elements.length - 2;
        for (int i = 2; i < elements.length; i++) {
            if (contain != null && contain.length() > 0 && elements[i].toString().indexOf(contain) != -1) {
                if (step == 1) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
                } else if (step == elements.length - 2) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
                } else {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
                }
                stackMap.put(step, stack + "\n");
            }
            step--;
        }
        return stackMap;
    }

    /**
     * 获取栈链路(包含某个字符串)
     *
     * @param contain 包含字符串
     */
    public static void printStackTraceContain(String contain) {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        String stack = "";
        int step = elements.length - 2;
        for (int i = 2; i < elements.length; i++) {
            if (contain != null && contain.length() > 0 && elements[i].toString().indexOf(contain) != -1) {
                if (step == 1) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
                } else if (step == elements.length - 2) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
                } else {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
                }
                print(stack);
            }
            step--;
        }
    }

    /**
     * 获取栈链路(包含任意一个contain)
     *
     * @param contains 包含字符串列表
     * @return TreeMap
     */
    public static TreeMap getStackTraceContain(List<String> contains) {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return StackMap.getNilInstance();
        }
        String threadName = Thread.currentThread().getName();
        /**
         *  栈链路信息
         */
        StackMap stackMap = StackMap.getInstance();
        String stack = "";
        int step = elements.length - 2;
        boolean isContain;
        // 是否通过该记录 ->contains
        boolean isContinue;
        for (int i = 2; i < elements.length; i++) {
            isContinue = false;
            if (contains != null && contains.size() > 0) {
                isContain = true;
                int c = 0;
                while (isContain) {
                    if (c < contains.size()) {
                        if (elements[i].toString().indexOf(contains.get(c)) != -1) {
                            isContain = false;
                            isContinue = true;
                        }
                    } else {
                        isContain = false;
                    }
                    c++;
                }
            }
            if (isContinue) {
                if (step == 1) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
                } else if (step == elements.length - 2) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
                } else {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
                }
                stackMap.put(step, stack + "\n");
            }
            step--;
        }
        return stackMap;
    }

    /**
     * 打印栈链路(包含任意一个contain)
     *
     * @param contains 包含字符串列表
     */
    public static void printStackTraceContain(List<String> contains) {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        String stack = "";
        int step = elements.length - 2;
        boolean isContain;
        // 是否通过该记录 ->contains
        boolean isContinue;
        for (int i = 2; i < elements.length; i++) {
            isContinue = false;
            if (contains != null && contains.size() > 0) {
                isContain = true;
                int c = 0;
                while (isContain) {
                    if (c < contains.size()) {
                        if (elements[i].toString().indexOf(contains.get(c)) != -1) {
                            isContain = false;
                            isContinue = true;
                        }
                    } else {
                        isContain = false;
                    }
                    c++;
                }
            }
            if (isContinue) {
                if (step == 1) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
                } else if (step == elements.length - 2) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
                } else {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
                }
                print(stack);
            }
            step--;
        }
    }

    /**
     * 获取栈链路(不包含任意一个filter,而且必须包含任意一个contain)
     *
     * @param filters
     * @param contains
     * @return TreeMap
     */
    public static TreeMap getStackTraceMerge(List<String> filters, List<String> contains) {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return StackMap.getNilInstance();
        }
        String threadName = Thread.currentThread().getName();
        /**
         *  栈链路信息
         */
        StackMap stackMap = StackMap.getInstance();
        String stack = "";
        int step = elements.length - 2;
        boolean isFilter;
        // 是否跳过该记录 ->filters
        boolean isPass;
        boolean isContain;
        // 是否通过该记录 ->contains
        boolean isContinue;
        for (int i = 2; i < elements.length; i++) {
            isPass = false;
            if (filters != null && filters.size() > 0) {
                isFilter = true;
                int f = 0;
                while (isFilter) {
                    if (f < filters.size()) {
                        if (elements[i].toString().indexOf(filters.get(f)) != -1) {
                            isFilter = false;
                            isPass = true;
                        }
                    } else {
                        isFilter = false;
                    }
                    f++;
                }
            }
            if (isPass) {
                // 如果包含filters直接跳过，无需验证contains
                continue;
            }
            isContinue = false;
            if (contains != null && contains.size() > 0) {
                isContain = true;
                int c = 0;
                while (isContain) {
                    if (c < contains.size()) {
                        if (elements[i].toString().indexOf(contains.get(c)) != -1) {
                            isContain = false;
                            isContinue = true;
                        }
                    } else {
                        isContain = false;
                    }
                    c++;
                }
            }
            if (isContinue) {
                if (step == 1) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
                } else if (step == elements.length - 2) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
                } else {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
                }
                stackMap.put(step, stack + "\n");
            }
            step--;
        }
        return stackMap;
    }

    /**
     * 打印栈链路(不包含任意一个filter,而且必须包含任意一个contain)
     *
     * @param filters
     * @param contains
     */
    public static void printStackTraceMerge(List<String> filters, List<String> contains) {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();
        if (elements == null || elements.length <= 2) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        String stack = "";
        int step = elements.length - 2;
        boolean isFilter;
        // 是否跳过该记录 ->filters
        boolean isPass;

        boolean isContain;
        // 是否通过该记录 ->contains
        boolean isContinue;
        for (int i = 2; i < elements.length; i++) {
            isPass = false;
            if (filters != null && filters.size() > 0) {
                isFilter = true;
                int f = 0;
                while (isFilter) {
                    if (f < filters.size()) {
                        if (elements[i].toString().indexOf(filters.get(f)) != -1) {
                            isFilter = false;
                            isPass = true;
                        }
                    } else {
                        isFilter = false;
                    }
                    f++;
                }
            }
            if (isPass) {
                // 如果包含filters直接跳过，无需验证contains
                continue;
            }
            isContinue = false;
            if (contains != null && contains.size() > 0) {
                isContain = true;
                int c = 0;
                while (isContain) {
                    if (c < contains.size()) {
                        if (elements[i].toString().indexOf(contains.get(c)) != -1) {
                            isContain = false;
                            isContinue = true;
                        }
                    } else {
                        isContain = false;
                    }
                    c++;
                }
            }
            if (isContinue) {
                if (step == 1) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(入口)   refer:" + elements[i].toString();
                } else if (step == elements.length - 2) {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "(当前方法)   refer:" + elements[i].toString();
                } else {
                    stack = "线程：" + threadName + "  执行阶段：" + step + "   refer:" + elements[i].toString();
                }
                print(stack);
            }
            step--;
        }
    }

    /**
     * 打印输出
     *
     * @param msg 内容
     */
    private static void print(String msg) {
        System.out.println(msg);
    }

    /**
     * @author: lazecoding
     * @date: 2020/12/5 13:46
     * @description: 内部类
     */
    private static class StackMap extends TreeMap<Integer, String> {
        /**
         * 单例空Map
         */
        private static final StackMap NISTACKMAP = new StackMap(Comparator.reverseOrder());

        private StackMap(Comparator<? super Integer> comparator) {
            super(comparator);
        }

        public static StackMap getInstance() {
            return new StackMap(Comparator.reverseOrder());
        }

        /**
         *  获取空MAP常量实例
         * @return
         */
        public static StackMap getNilInstance() {
            return NISTACKMAP;
        }

        @Override
        public String toString() {
            Iterator<Map.Entry<Integer, String>> iterator = entrySet().iterator();
            if (!iterator.hasNext())
                return "";
            StringBuilder builder = new StringBuilder();
            for (; ; ) {
                Map.Entry<Integer, String> entry = iterator.next();
                String value = entry.getValue();
                builder.append(value);
                if (!iterator.hasNext()) {
                    return builder.toString();
                }
            }
        }
    }
}
```