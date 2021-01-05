# EXPLAIN 执行计划

MySQL 提供 EXPLAIN 命令分析 SQL 执行计划，用法很简单，加载 SQL 前面即可。MySQL 5.6 版本之前，EXPLAIN  只支持 SELECT 查询，自 5.6 版本开始支持DML语句，即UPDATE、DELETE、INSERT。

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

索引添加前后执行计划有明显变化，EXPLAIN 命令输出一共有 12 个字段，我们先了解这些字段含义再分析示例。

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
