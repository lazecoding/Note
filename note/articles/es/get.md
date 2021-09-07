# Get 流程

Elasticsearch 文档的读取分为 Get 和 Search，本章讲解 Get 请求执行流程。

请求格式：

```C
GET <index>/_doc/<_id>
```

基本流程：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/Get基本流程.png" width="600px">
</div>

1. 客户端向 `Node 1` 发送 Get 请求。
2. `Node 1` 使用文档的 `_id` 来确定文档属于 `分片 0`，通过集群状态中得知 `分片 0` 的副本分片位于三个节点上。此时它可以将请求发送到任其中任意一个节点上，这里它将请求转发到 `Node 2`。
3. `Node 2` 将文档返回给 `Node 1`，`Node 1` 将文档返回给客户端。

这里，`Node 1` 作为协调节点，会将来自客户端的请求负载均衡地请求集群中地各个副本分片。

详细流程：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/Get详细流程.png" width="600px">
</div>

Get 请求涉及两个节点：协调节点和数据节点。