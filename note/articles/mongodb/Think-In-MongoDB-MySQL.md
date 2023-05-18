# MongoDB 和 MySQL

本文简单分析下在 MySQL 和 NO-SQL(MongoDB) 中，对模型设计的一些原则，以及二者在索引实现机制上的差异。

## Data Model (one to many)

### MySQL

MySQL 作为关系型数据库，有二个实体： people 和 passports，这二个实体对应的二张表，`就是一对多（people-passports) 的映射关系`。

```sql
mysql> select * from people;
+----+------------+
| id | name       |
+----+------------+
|  1 | Stephane   |
|  2 | John       |
|  3 | Michael    |
|  4 | Cinderella |
+----+------------+

mysql> select * from passports;
+----+-----------+---------+-------------+
| id | people_id | country | valid_until |
+----+-----------+---------+-------------+
|  4 |         1 | FR      | 2020-01-01  |
|  5 |         2 | US      | 2020-01-01  |
|  6 |         3 | RU      | 2020-01-01  |
+----+-----------+---------+-------------+
```

### MongoDB：单向嵌套

MongoDB作为当下流行的非关系型文档数据库，`对于一对多的关系，通常使用内嵌模型，以空间换取时间`。

```C
> db.people_all.find().pretty()
{
  "_id" : ObjectId("51f7be1cd6189a56c399d3bf"),
  "name" : "Stephane",
  "country" : "FR",
  "valid_until" : ISODate("2019-12-31T23:00:00Z")
}
{
  "_id" : ObjectId("51f7be3fd6189a56c399d3c0"),
  "name" : "John",
  "country" : "US",
  "valid_until" : ISODate("2019-12-31T23:00:00Z")
}
{
  "_id" : ObjectId("51f7be4dd6189a56c399d3c1"),
  "name" : "Michael",
  "country" : "RU",
  "valid_until" : ISODate("2019-12-31T23:00:00Z")
}
```

MongoDB 是 Schema-free 模式，支持异构的文档。

上面设计的缺点是无法分辨出哪些字段是 People 属性，哪些字段又是护照 Passport 的属性，你需要正确地理解整个数据结构。

### 改进点

将护照的多个字段属性以一个联合字段嵌入到 People 记录中。

```C
> db.people_embed.find().pretty()
{
  "_id" : ObjectId("51f7c0048ded44d5ebb83774"),
  "name" : "Stephane",
  "passport" : {
    "country" : "FR",
    "valid_until" : ISODate("2019-12-31T23:00:00Z")
  }
}
{
  "_id" : ObjectId("51f7c70e8ded44d5ebb83775"),
  "name" : "John",
  "passport" : {
    "country" : "US",
    "valid_until" : ISODate("2019-12-31T23:00:00Z")
  }
}
{
  "_id" : ObjectId("51f7c71b8ded44d5ebb83776"),
  "name" : "Michael",
  "passport" : {
    "country" : "RU",
    "valid_until" : ISODate("2019-12-31T23:00:00Z")
  }
}
```

## Data Model (many to many)

### MySQL

针对关系数据库中建立了多对多关系，引入中间表实现多对多的映射。中间表包括了关联的二张表的主键。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/mongodb/MySQL-many-to-many.png" width="600px">
</div>

### MongoDB：双向嵌套

设计方案：每个子文档都保留一个对父文档的引用。需要在二个 MongoDB 的集合 Person 和 Group 中做 `双向引用`。

```C
//person
db.person.insert({
  "_id": ObjectId("4e54ed9f48dc5922c0094a43"),
  "firstName": "Joe",
  "lastName": "Mongo",
  "groups": [
    ObjectId("4e54ed9f48dc5922c0094a42"),
    ObjectId("4e54ed9f48dc5922c0094a41")
  ]
});

//groups 
db.groups.insert({
  "_id": ObjectId("4e54ed9f48dc5922c0094a42"),
  "groupName": "mongoDB User",
  "persons": [
    ObjectId("4e54ed9f48dc5922c0094a43"),
    ObjectId("4e54ed9f48dc5922c0094a40")
  ]
});
```

> 对于一对多的MySQL模型，MongoDB使用单向嵌套解决方案；对于多对多的MySQL模型，MongoDB使用双向嵌套解决方案。

## 索引结构： B Tree VS. B+ Tree

B 树：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/mysql/B树.png" width="600px">
</div>

B+ 树：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/mysql/B+树.png" width="600px">
</div>

- B 树中: key-value 同时存储在内部节点和叶子节点中。
- 在 B+ 树中: key-value 只存储在叶子节点上，而内部节点只存储 key 用来索引检索功能。
- 在 B+ 树中，叶子节点数据按顺序链表排序，但在 B 树中，叶子节点不能使用链表存储。

`MongoDB 的索引采用的是 B 树，而 MySQL 索引采纳的是 B+ 树`。

在现实生活中，`B 树和 B+ 树被用来建立海量数据的索引，从而使数据库中的搜索操作变得高效`。但还是有一些典型的差别。我们以 MySQL 与 MongoDB 的索引结构来说明。

B+ 树与 B 树中存在一个非常大的区别：

- B+ 树的叶子节点是链表有序的，因此对所有键进行线性扫描只需要通过所有叶子节点一次。
- 另一方面，一个B树需要遍历树中的每个级别。

MySQL 索引背后 B+ 树结构的选择，让它更容易，更高效执行对范围区间扫描；而 MongoDB 索引必须对完整树遍历才能完整扫描。

- MySQL 查询中经常涉及到多表查询，背后会使用范围区间扫描。因此，MySQL 索引选择的 B+ 树，对范围区间扫描的需求给出了设计与实现上的支持（叶子节点链表有序组织)。
- 而 MongoDB 本身设计原则，它通过嵌套方式，以面向对象组合多个模型在一个集合 collection 中，尽可能避免检索 JOIN 多个 collection 操作。因此，MongoDB 索引选择的 B 树，弱化对范围区间扫描的需求的支持。



