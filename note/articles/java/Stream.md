# Stream

Stream 是 JDK 1.8 引入的新特性，还配合出现了 Lambda。

Stream 将要处理的元素集合看作一种流，在流的过程中，借助 Stream API 对流中的元素进行操作，比如：筛选、排序、聚合等。

Stream 生命周期分为：`创建` -> `中间操作` -> `终止操作`。`创建`，会产生一个流；`中间操作`，每次返回一个新的流，可以有多个；`终止操作`，每个流只能进行一次终端操作，终端操作结束后流无法再次使用。终止操作会产生一个新的集合或值。

Stream 的操作符大体上分为两种：`中间操作符` 和 `终止操作符`，如下图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/stream/Stream-操作符列表.png" width="600px">
</div>

### 创建

- 通过 `java.util.Collection.stream()` 方法用集合创建流。

```java
List<String> list = Arrays.asList("a", "b", "c");
// 创建一个顺序流
Stream<String> stream = list.stream();
// 创建一个并行流
Stream<String> parallelStream = list.parallelStream();
```

> stream() 是顺序流，由主线程按顺序对流执行操作，而 parallelStream() 是并行流，内部以多线程并行执行的方式对流进行操作。

- 使用 `java.util.Arrays.stream(T[] array)` 方法用数组创建流。

```java
int[] array={1,3,5,6,8};
IntStream stream = Arrays.stream(array);
```

- 使用 Stream 的静态方法：`of()`、`iterate()`、`generate()`。

```java
Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5, 6);

Stream<Integer> stream2 = Stream.iterate(0, (x) -> x + 3).limit(4);
stream2.forEach(System.out::println); // 0 2 4 6 8 10

Stream<Double> stream3 = Stream.generate(Math::random).limit(3);
stream3.forEach(System.out::println); 
```

### 中间操作

- 遍历/匹配(foreach/find/match)

Stream 是支持类似集合的遍历和匹配元素的，只是 Stream 中的元素是以 Optional 类型存在的。

```java
List<Integer> list = Arrays.asList(7, 6, 9, 3, 8, 2, 1);
// 遍历输出符合条件的元素
list.stream().filter(x -> x > 6).forEach(System.out::println);
// 匹配第一个
Optional<Integer> findFirst = list.stream().filter(x -> x > 6).findFirst();
// 匹配任意（适用于并行流）
Optional<Integer> findAny = list.parallelStream().filter(x -> x > 6).findAny();
// 是否包含符合特定条件的元素
boolean anyMatch = list.stream().anyMatch(x -> x < 6);
System.out.println("匹配第一个值：" + findFirst.get());
System.out.println("匹配任意一个值：" + findAny.get());
System.out.println("是否存在大于6的值：" + anyMatch);
```

- 筛选（filter）

筛选，是按照一定的规则校验流中的元素，将符合条件的元素提取到新的流中的操作。

```java
List<Integer> list = Arrays.asList(6, 7, 3, 8, 1, 2, 9);
Stream<Integer> stream = list.stream();
stream.filter(x -> x > 7).forEach(System.out::println);
```

- 映射(map/flatMap)

映射，可以将一个流的元素按照一定的映射规则映射到另一个流中。

> map,接收一个函数作为参数，该函数会被应用到每个元素上，并将其映射成一个新的元素。
>
> flatMap：接收一个函数作为参数，将流中的每个值都换成另一个流，然后把所有流连接成一个流。

```java
// 1.英文字符串数组的元素全部改为大写。整数数组每个元素 +3。
String[] strArr = { "abcd", "bcdd", "defde", "fTr" };
List<String> strList = Arrays.stream(strArr).map(String::toUpperCase).collect(Collectors.toList());
System.out.println("每个元素大写：" + strList);
List<Integer> intList = Arrays.asList(1, 3, 5, 7, 9, 11);
List<Integer> intListNew = intList.stream().map(x -> x + 3).collect(Collectors.toList());
System.out.println("每个元素+3：" + intListNew);

// 2.将两个字符数组合并成一个新的字符数组。
List<String> list = Arrays.asList("m,k,l,a", "1,3,5,7");
List<String> listNew = list.stream().flatMap(s -> {
    // 将每个元素转换成一个 stream
    String[] split = s.split(",");
    Stream<String> s2 = Arrays.stream(split);
    return s2;
}).collect(Collectors.toList());
System.out.println("处理前的集合：" + list);
System.out.println("处理后的集合：" + listNew);
```

- 排序(sorted)

sorted，中间操作。有两种排序：sorted()：自然排序，流中元素需实现Comparable接口；sorted(Comparator com)：Comparator排序器自定义排序。

```java
List<Integer> list = Arrays.asList(1000, -6000, 3000, 4000, 6000, 7000, 9000, 6000, 2000);
// 自然排序
List<Integer> newList = list.stream().sorted(Comparator.comparing(Integer::intValue)).collect(Collectors.toList());
// 自定义排序
List<Integer> newList2 = list.stream().sorted((p1, p2) -> {
    p1 = Math.abs(p1);
    p2 = Math.abs(p2);
    return p1.compareTo(p2);
}).collect(Collectors.toList());
System.out.println("自然升序排序：" + newList);
System.out.println("自定义升序排序：" + newList2);
```

- 提取/组合

Stream 也可以进行合并、去重、限制、跳过等操作。

```java
String[] arr1 = { "a", "b", "c", "d" };
String[] arr2 = { "d", "e", "f", "g" };
Stream<String> stream1 = Stream.of(arr1);
Stream<String> stream2 = Stream.of(arr2);
// concat:合并两个流 distinct：去重
List<String> newList = Stream.concat(stream1, stream2).distinct().collect(Collectors.toList());
// limit：限制从流中获得前n个数据
List<Integer> collect = Stream.iterate(1, x -> x + 2).limit(10).collect(Collectors.toList());
// skip：跳过前n个数据
List<Integer> collect2 = Stream.iterate(1, x -> x + 2).skip(1).limit(5).collect(Collectors.toList());
System.out.println("流合并：" + newList);
```

### 终止操作

- 聚合（max/min/count)

max、min、count 是常用的聚合运算。

```java
List<Integer> list = Arrays.asList(-13, 7, 6, 9, 4, 11, 6);
// 自然排序
Optional<Integer> max = list.stream().max(Integer::compareTo);
// 自然排序
Optional<Integer> min = list.stream().max(Integer::compareTo);
// 自定义排序,取绝对值比较
Optional<Integer> max2 = list.stream().max(new Comparator<Integer>() {
    @Override
    public int compare(Integer o1, Integer o2) {
        o1 = Math.abs(o1);
        o2 = Math.abs(o2);
        return o1.compareTo(o2);
    }
});
System.out.println("自然排序的最大值：" + max.get());
System.out.println("自然排序的最小值：" + min.get());
System.out.println("自定义排序的最大值：" + max2.get());

long count = list.stream().filter(x -> x > 6).count();
System.out.println("list 中大于 6 的元素个数：" + count);
```

- 归约(reduce)

归约，也称缩减，顾名思义，是把一个流缩减成一个值，能实现对集合求和、求乘积和求最值操作。

```java
List<Integer> list = Arrays.asList(1, 3, 2, 8, 11, 4);
// 求和方式1
Optional<Integer> sum = list.stream().reduce((x, y) -> x + y);
// 求和方式2
Optional<Integer> sum2 = list.stream().reduce(Integer::sum);
// 求和方式3
Integer sum3 = list.stream().reduce(0, Integer::sum);

// 求乘积
Optional<Integer> product = list.stream().reduce((x, y) -> x * y);

// 求最大值方式1
Optional<Integer> max = list.stream().reduce((x, y) -> x > y ? x : y);
// 求最大值写法2
Integer max2 = list.stream().reduce(1, Integer::max);

System.out.println("list求和：" + sum.get() + "," + sum2.get() + "," + sum3);
System.out.println("list求积：" + product.get());
System.out.println("list求和：" + max.get() + "," + max2);
```

- 收集(collect)

collect，收集，可以说是内容最繁多、功能最丰富的部分了。从字面上去理解，就是把一个流收集起来，最终可以是收集成一个值也可以收集成一个新的集合。

> collect 主要依赖 java.util.stream.Collectors 类内置的静态方法。

- 归集(toList/toSet/toMap)

因为流不存储数据，那么在流中的数据完成处理后，需要将流中的数据重新归集到新的集合里。toList、toSet 和 toMap 比较常用，另外还有 toCollection、toConcurrentMap 等复杂一些的用法。

```java
List<Integer> list = Arrays.asList(1, 6, 3, 4, 7, 9, 6, 20);
List<Integer> listNew = list.stream().filter(x -> x % 2 == 0).collect(Collectors.toList());
Set<Integer> set = list.stream().filter(x -> x % 2 == 0).collect(Collectors.toSet());
Map<Integer, Integer> map = list.stream().distinct().collect(Collectors.toMap(x -> x, x -> x * 2));
System.out.println("toList:" + listNew);
System.out.println("toSet:" + set);
System.out.println("toMap:" + map);
```

- 统计(count/averaging)

Collectors 提供了一系列用于数据统计的静态方法：

1. 计数：count。
2. 平均值：averagingInt、averagingLong、averagingDouble。
3. 最值：maxBy、minBy。
4. 求和：summingInt、summingLong、summingDouble。
5. 统计以上所有：summarizingInt、summarizingLong、summarizingDouble。

```java
List<Integer> list = Arrays.asList(1000, 6000, 3000, 4000, 6000, 7000, 9000, 6000, 2000);
// 求总数
Long count = list.stream().collect(Collectors.counting());
// 求平均
Double average = list.stream().collect(Collectors.averagingDouble(Integer::intValue));
// 求最高
Optional<Integer> max = list.stream().collect(Collectors.maxBy(Integer::compare));
// 求和
Integer sum = max.stream().collect(Collectors.summingInt(Integer::intValue));
// 一次性统计所有信息
DoubleSummaryStatistics collect = list.stream().collect(Collectors.summarizingDouble(Integer::intValue));

System.out.println("总数：" + count);
System.out.println("平均：" + average);
System.out.println("总和：" + sum);
System.out.println("所有统计：" + collect);
```

> ... 点到为止，不举了，更多用法网上冲浪。
