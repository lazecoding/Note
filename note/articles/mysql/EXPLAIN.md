# EXPLAIN 执行计划

MySQL 提供 EXPLAIN 命令分析 SQL 执行计划，用法很简单，加载 SQL 前面即可。MySQL 5.6 版本之前，EXPLAIN 只支持 SELECT 查询，自 5.6 版本开始支持 DML 语句，即 UPDATE、DELETE、INSERT。

构造示例数据：

```sql
CREATE TABLE `user` (
  `uid` int(11) NOT NULL AUTO_INCREMENT,
  `uname` varchar(20) NOT NULL,
  `role_id` int(11) NOT NULL,
  PRIMARY KEY (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `role` (
  `rid` int(11) NOT NULL AUTO_INCREMENT,
  `rname` varchar(20) NOT NULL,
  PRIMARY KEY (`rid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO role VALUES(1,'管理员');
INSERT INTO role VALUES(2,'老师');
INSERT INTO role VALUES(3,'学生');
 
INSERT INTO user VALUES (1,'Admin',1);
INSERT INTO user VALUES (2,'饕餮',2);
INSERT INTO user VALUES (3,'混沌',2);
INSERT INTO user VALUES (4,'梼杌',2);
INSERT INTO user VALUES (5,'穷奇',2);
INSERT INTO user VALUES (6,'青龙',3);
INSERT INTO user VALUES (7,'白虎',3);
INSERT INTO user VALUES (8,'朱雀',3);
INSERT INTO user VALUES (9,'玄武',3);
```

EXPLAIN 使用示例：

```sql
EXPLAIN SELECT b.rname,COUNT(*) FROM user a,role b WHERE a.role_id = b.rid GROUP BY b.rname;
```

| id  | select_type | table | partitions | type | possible_keys | key | key_len | ref | rows | filtered |                         Extra                      |
| --- | ----------- | ----- | ---------- | ---- | ------------- | --- | ------- | --- | ---- | -------- | -------------------------------------------------- |
|  1  |    SIMPLE   |   b   |            | ALL  |   PRIMARY     |     |         |     |   3  |   100    |          Using temporary; Using filesort           |
|  1  |    SIMPLE   |   a   |            | ALL  |               |     |         |     |   9  |  11.11   | Using where; Using join buffer (Block Nested Loop) |

创建索引后 EXPLAIN 使用示例：

```sql
CREATE INDEX index1 ON role(rid,rname);
CREATE INDEX index2 ON role(rname,rid);


CREATE INDEX index1 ON user(role_id,uid);
CREATE INDEX index2 ON user(uid,role_id);

EXPLAIN SELECT b.rname,COUNT(*) FROM user a,role b WHERE a.role_id = b.rid GROUP BY b.rname;
```

| id  | select_type | table | partitions |  type |     possible_keys     |   key  | key_len |   ref    | rows | filtered |    Extra    |
| --- | ----------- | ----- | ---------- | ----- | --------------------- | ------ | ------- | -------- | ---- | -------- | ----------- |
|  1  |    SIMPLE   |   b   |            | index | PRIMARY,index1,index2 | index2 |   86    |          |   3  |   100    | Using index |
|  1  |    SIMPLE   |   a   |            |  ref  |                       | index1 |   4     | DB.b.rid |   3  |   100    | Using index |

<br>
索引添加前后执行计划有明显变化，EXPLAIN 命令输出一共有 12 个字段，如下：

| 列名           | 含义                               | 官方文档解释                                     |
| ------------- | --------------------------------- | ---------------------------------------------- |
| id            | 查询id                             | The SELECT identifier                          |
| select_type   | 查询类型                            | The SELECT type                                |
| table         | 表名                               | The table for the output row                   |
| partitions    | 查询匹配的分区                       | The matching partitions                        |
| type          | 关联类型                            | The join type                                  |
| possible_keys | MySQL优化器可能选择的索引key          | The possible indexes to choose                 |
| key           | 实际执行查询时选择的索引key            | The index actually chosen                      |
| key_len       | 选择的索引的使用长度                  | The length of the chosen key                   | 
| ref           | join时对比的字段                    | The columns compared to the index               |
| rows          | 预估执行查询时扫描的行数               | Estimate of rows to be examined                |
| filtered      | 根据表查询条件过滤的扫描行数百分值       | Percentage of rows filtered by table condition |
| Extra         | 额外信息                            | Additional information                         |

### id

该语句的唯一标识。如果 EXPLAIN 的结果包括多个 id 值，则数字越大越先执行；而对于相同 id 的行，则表示从上往下依次执行。

```sql
EXPLAIN 
SELECT uname FROM user WHERE role_id = (SELECT rid FROM role WHERE rname = '管理员')
    UNION 
SELECT uname FROM user WHERE role_id = (SELECT rid FROM role WHERE rname = '学生');
```

| id  | select_type |   table    | partitions |  type | possible_keys |   key  | key_len |  ref  | rows | filtered |       Extra     |
| --- | ----------- | ---------- | ---------- | ----- | ------------- | ------ | ------- | ----- | ---- | -------- | --------------- |
|  1  |   PRIMARY   |    user    |            |  ref  |    index1     | index1 |   4     | const |   1  |   100    |   Using index   |
|  2  |   SUBQUERY  |    role    |            |  ref  |    index2     | index2 |   82    | const |   4  |   100    |   Using index   |
|  3  |    UNION    |    user    |            |  ref  |    index1     | index1 |   4     | const |   1  |   100    |   Using index   |
|  4  |   SUBQUERY  |    role    |            |  ref  |    index2     | index2 |   82    | const |   1  |   100    |   Using index   |
|     |    UNION    | <union1,3> |            |  ALL  |               |        |         |       |      |          | Using temporary |

### select_type

| select_type          | 解释                                                                                               | 官方文档解释                                                                                              |
| -------------------- | -----------------------------------------------------------------------------------------------   | --------------------------------------------------------------------------------------------------------|
| SIMPLE               | 简单查询(不使用 UNION 或者子查询)                                                                      | Simple SELECT (not using UNION or subqueries)                                                           |
| PRIMARY              | 一个需要 UNION 操作或者含有子查询的 select，位于最外层的单位查询的 select_type 即为 primary,且只有一个          | Outermost SELECT                                                                                        |
| UNION                | UNION 连接的 select 查询，除了第一个表外，第二个及以后的表 select_type 都是 UNION                           | Second or later SELECT statement in a UNION                                                             |
| DEPENDENT UNION      | 与 UNION 一样，出现在 UNION 或 UNION ALL 语句中，但是这个查询要受到外部查询的影响                             | Second or later SELECT statement in a UNION, dependent on outer query                                   |
| UNION RESULT         | UNION 的结果集                                                                                      | Result of a UNION.                                                                                      |
| SUBQUERY             | 除了 FROM 字句中包含的子查询外，其他地方出现的子查询都可能是 subquery                                        | First SELECT in subquery                                                                                |
| DEPENDENT SUBQUERY   | 与 dependent union 类似，表示这个 subquery 的查询要受到外部表查询的影响                                    | First SELECT in subquery, dependent on outer query                                                      |
| DERIVED              | FROM 字句中出现的子查询                                                                               | Derived table                                                                                           |
| DEPENDENT DERIVED    | 与 derived 类似，并且这个查询依赖其他的表                                                                | Derived table dependent on another table                                                                 |
| MATERIALIZED         | 物化子查询。优化程序实体化以实现更高效的子查询的处理。 实例化通常通过在内存中生成子查询结果作为临时表来加快查询执行速度。 MySQL第一次需要子查询结果时，将其结果化为临时表。  |Materialized subquery.The optimizer uses materialization to enable more efficient subquery processing. Materialization speeds up query execution by generating a subquery result as a temporary table, normally in memory. The first time MySQL needs the subquery result, it materializes that result into a temporary table. |
| UNCACHEABLE SUBQUERY | 对于外层的主表，子查询不可被物化，每次都需要计算（耗时操作）                                                  | A subquery for which the result cannot be cached and must be re-evaluated for each row of the outer query |
| UNCACHEABLE UNION    | UNION 操作中，内层的不可被物化的子查询（类似于 UNCACHEABLE SUBQUERY）                                      | The second or later select in a UNION that belongs to an uncacheable subquery (see UNCACHEABLE SUBQUERY) |

### table

该列显示对应行正在访问的表(有别名就显示别名)，它也可能是以下显示的值：
- `<unionM,N>`: 这一行代表着 id 为 M 和 N 的查询的关联结果。
- `<derivedN>`: 该派生表取值于 id 为 N 的的查询结果。例如，派生表可能来自于 FROM 子句的子查询。 
- `<subqueryN>`: 这一行来自于 id 为 N 的查询的物化子查询的查询结果。

### partitions

该列显示分区表命中的分区情况,非分区表该字段为空（NULL）。

### type

该列称为关联类型或者访问类型，它指明了 MySQL 决定如何查找表中符合条件的行，同时是我们判断查询是否高效的重要依据。
- `ALL`：全表扫描，这个类型是性能最差的查询之一。通常来说，我们的查询不应该出现 ALL 类型，因为这样的查询，在数据量最大的情况下，对数据库的性能是巨大的灾难。如果在查询里使用了 LIMIT N，即使 type 依然是 ALL，但是只需要扫描到符合条件的前 N 行数据，就会停止继续扫描。
- `index`：全索引扫描，和 ALL 类型类似，只不过 ALL 类型是全表扫描，而 index 类型是扫描全部的索引，主要优点是避免了排序，但是开销仍然非常大。如果在 Extra 列看到 Using index，说明正在使用覆盖索引，只扫描索引的数据，它比按索引次序全表扫描的开销要少很多。
- `range`：范围扫描，就是一个有限制的索引扫描，它开始于索引里的某一点，返回匹配这个值域的行，range 比全索引扫描更高效，因为它不需要遍历全部索引。这个类型通常出现在 =、<>、>、>=、<、<=、IS NULL、<=>、BETWEEN、IN() 的操作中，key 列显示使用了哪个索引，当 type 为该值时，则输出的 ref 列为 NULL，并且 key_len 列是此次查询中使用到的索引最长的那个。
- `ref`：一种索引访问，也称索引查找，它返回所有匹配某个单个值的行。此类型通常出现在多表的 JOIN 查询, 针对于非唯一或非主键索引, 或者是使用了最左前缀规则索引的查询。
- `ref_or_null`：ref_or_null 与 ref 类似，但是 MySQL 必须对包含 NULL 值的行进行行额外搜索。
- `eq_ref`：使用这种索引查找，最多只返回一条符合条件的记录。在使用唯一性索引或主键查找时会出现该值，非常高效。
- `index_subquery`：index_subquery 替换了以下形式的子查询中的 eq_ref 访问类型，其中 key_column 是非唯一索引。
```sql
value IN (SELECT key_column FROM table)
```
- `unique_subquery`：unique_subquery 跟 index_subquery 类似，它替换了以下形式的子查询中的 eq_ref 访问类型，其中 primary_key 可以是主键索引或唯一索引。
```sql
value IN (SELECT primary_key FROM table)
```
- `index_merge`：表示出现了索引合并优化，通常是将多个索引字段的范围扫描合并为一个。包括单表中多个索引的交集，并集以及交集之间的并集，但不包括跨多张表和全文索引。
- `const`：MySQL 知道查询最多只能匹配到一条符合条件的记录。因为只有一行，所以优化器可以将这一行中的列中的值视为常量。const 表查询非常快，因为它们只读取一次数据行。通常使用主键或唯一索引进行等值条件查询时会用 const。
- `system`：该表只有一行（系统表），是 const 关联类型的特例。
- `NULL`：在执行阶段不需要访问表。
- `fulltext`：命中全文索引时 type 为 fulltext。

### possible_keys

该列显示查询可能使用哪些索引来查找。

### key

该列显示实际使用的索引，如果没有选择索引，值为 NULL。

### key_len

该列显实际使用的索引的字节长度，观察 key_len 可以让你知道 MySQL 实际上使用了一个联合索引的多少个字段。

### ref

该列显示哪些字段或者常量被用来和 key 配合从表中查询记录出来。

### rows

该列显示了估计要找到所需的行而要读取的行数，这是个估计值，原则上值越小越好。

### filtered

该列显示存储引擎返回的数据在 Server 层过滤后，剩下多少满足查询的记录数量的比例，注意是百分比，不是具体记录数。用 rows × filtered 可获得和下一张表连接的行数。例如 rows = 1000，filtered = 50%，则和下一张表连接的行数是 500。

### Extra

该列显示执行查询时的额外信息，常见的取值如下：
- `Using index`：使用覆盖索引，表示查询索引就可查到所需数据，不用扫描表数据文件，往往说明性能不错。
- `Using Where`：在存储引擎检索行后再进行过滤，使用了 WHERE 从句来限制哪些行将与下一张表匹配或者是返回给用户。
- `Using temporary`：在查询结果排序时会使用一个临时表，一般出现于排序、分组和多表 JOIN 的情况，查询效率不高，建议优化。
- `Using filesort`：对结果使用一个外部索引排序，而不是按索引次序从表里读取行，一般有出现该值，都建议优化去掉，因为这样的查询 CPU 资源消耗大。
- ...... 还有不少，不一一说明，如果想查询尽可能快，多关注 Using filesort 和 Using temporary。

### SHOW WARNINGS

在 EXPLAIN 语句后紧跟 `SHOW WARNINGS` 命令可以看到MySQL优化器优化查询语句的结果。

```sql
EXPLAIN 
SELECT uname FROM user WHERE role_id = (SELECT rid FROM role WHERE rname = '管理员')
    UNION 
SELECT uname FROM user WHERE role_id = (SELECT rid FROM role WHERE rname = '学生');
SHOW WARNINGS;

Message:
/* select#1 */ select `DB`.`user`.`uname` AS `uname` from `DB`.`user` 
where (`DB`.`user`.`role_id` = (/* select#2 */ select `DB`.`role`.`rid` from `DB`.`role` where (`DB`.`role`.`rname` = '管理员'))) 
union 
/* select#3 */ select `DB`.`user`.`uname` AS `uname` from `DB`.`user` 
where (`DB`.`user`.`role_id` = (/* select#4 */ select `DB`.`role`.`rid` from `DB`.`role` where (`DB`.`role`.`rname` = '学生')))
```