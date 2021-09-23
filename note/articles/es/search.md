# Search 流程

- 目录
    - [分布式检索](#分布式检索)
        - [query](#query)
        - [fetch](#fetch)
        - [dfs](#dfs)

Elasticsearch 文档的读取分为 Get 和 Search，本章讲解 Search 请求执行流程。

### 分布式检索

搜索是一种更加复杂的执行模型，因为我们不知道查询会命中哪些文档: 这些文档有可能在集群的任何分片上，它是一个`分布式检索`。

Elasticsearch 目前支持 `query_then_fetch` 和 `dfs_query_then_fetch` 两种搜索方法，不提供对 `query_and_fetch` 和 `dfs_query_and_fetch` 的支持。

```java
public static final SearchType [] CURRENTLY_SUPPORTED = {QUERY_THEN_FETCH, DFS_QUERY_THEN_FETCH};
```

我们以缺省的搜索方式 `query_then_fetch` 来看：一个搜索请求必须询问索引的所有分片的某个副本来确定它们是否含有匹配的文档。找到所有的匹配文档仅仅完成事情的一半。在 Search 接口返回一个 page 结果之前，多分片中的结果必须组合成单个排序列表。 为此，搜索是一个两阶段过程，我们称之为 `query then fetch`。

#### query

在 query 时， 搜索请求会广播到索引中每一个分片拷贝（主分片或者副本分片），每个分片在本地执行搜索并构建一个匹配文档的 优先队列。

> 优先队列是一个 top-N 的有序列表，它的大小取决于分页参数 from 和 size。如 {"from": 90, "size": 10} 对应的优先队列大小是 100。

query 阶段包含 3 个步骤:

- 客户端发送一个 search 请求到 Node 3 ， Node 3 会创建一个大小为 from + size 的空优先队列。
- Node 3 将查询请求转发到索引的每个主分片或副本分片中。每个分片在本地执行查询并添加结果到大小为 from + size 的本地有序优先队列中。
- 每个分片返回各自优先队列中所有文档的 ID 和排序值给协调节点，也就是 Node 3 ，它合并这些值到自己的优先队列中来产生一个全局排序后的结果列表。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/query阶段流程.png" width="600px">
</div>

当一个搜索请求被发送到某个节点时，这个节点就变成了协调节点。 这个节点的任务是广播查询请求到所有相关分片并将它们的响应整合成全局排序后的结果集合，这个结果集合会返回给客户端。

搜索请求可以被某个主分片或某个副本分片处理，更多的副本能够增加搜索吞吐率，协调节点会轮询所有的分片来分摊负载。

每个分片在本地执行查询请求并且创建一个长度为 from + size 的优先队列，分片返回一个轻量级的结果列表到协调节点，它仅包含文档 ID 集合以及任何排序需要用到的值，例如 _score。

协调节点将这些分片级的结果合并到自己的有序优先队列里，它标识哪些文档满足搜索请求。

#### fetch

query 阶段标识哪些文档满足搜索请求，我们需要取回这些文档,这是 fetch 阶段的任务。

fetch 阶段包含 3 个步骤:

- 协调节点辨别出哪些文档需要被取回并向相关的分片提交多个 GET 请求。
- 每个分片加载并丰富文档，返回文档给协调节点。
- 一旦所有的文档都被取回了，协调节点返回结果给客户端。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/fetch阶段流程.png" width="600px">
</div>

协调节点首先决定哪些文档需要被取回，一旦协调节点接收到所有的结果文档，它就组装这些结果为单个响应返回给客户端。

### dfs

缺省的搜索类型是 `query_then_fetch`，在某些情况下，你可能想明确设置 search_type 为 `dfs_query_then_fetch`  来改善相关性精确度：

```C
GET /_search?search_type=dfs_query_then_fetch
```

搜索类型 `dfs_query_then_fetch` 有预查询阶段，这个阶段可以从所有相关分片获取词频来计算全局词频。

每个文档都有相关性评分，用一个正浮点数字段 _score 来表示: _score 的评分越高，相关性越高。Elasticsearch 的相似度算法被定义为 `TF/IDF`（检索词频率/反向文档频率）。

- TF：检索词在该字段出现的频率，出现频率越高，相关性也越高。
- IDF：每个检索词在索引的所有文档出现的频率，出现频率越高，相关性越低。检索词出现在多数文档中会比出现在少数文档中的权重更低。

但是由于性能原因，Elasticsearch 不会每次都计算索引内所有文档的 IDF，而是每个分片会根据 该分片内的所有文档计算一个本地 IDF。在数据量很小的时候，可能会因为不同分片的 IDF 不同导致不同的结果，但当数据较大，局部的 IDF 会被均化，基本不会对结果产生影响。

和 `query_then_fetch`  相比，`dfs_query_then_fetch` 有 dfs 阶段。dfs 是指分布式频率搜索（Distributed Frequency Search），它告诉 Elasticsearch ，先分别获得每个分片本地的 IDF ，然后根据结果再计算整个索引的全局 IDF。

但是，不要在生产环境上使用 `dfs_query_then_fetch` 。这个是完全没有必要，只要有足够的数据就能保证词频是均匀分布的，没有理由给每个查询额外加上 dfs。