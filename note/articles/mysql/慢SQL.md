# 慢 SQL 优化

- 目录
    - [是不是慢 SQL](#是不是慢-SQL)
        - [脏页](#脏页)
        - [锁](#锁)
    - [慢 SQL 优化](#慢-SQL-优化)
        - [没创建索引](#没创建索引)
        - [字段有索引但是没使用](#字段有索引但是没使用)
        - [其他原因](#其他原因)
    - [数据量和复杂度](#数据量和复杂度)
        - [数据量](#数据量)
        - [复杂度](#复杂度)

"一条 SQL 为什么执行这么慢？"，是我们经常接触的问题，本文总结一下个人的分析思路。

### 是不是慢 SQL

分析慢 SQL 第一步是确定该 SQL 到底是不是慢 SQL。是大多数情况执行稳定，只是偶尔慢，还是再数据稳定情况下一直执行较慢。

如果是偶尔慢，一般出于 2 个方面原因：

- 数据库正在刷脏页。
- 没有获取到锁

#### 脏页

在执行写操作的时候，存储引擎（InnoDB 存储引擎为例）是先将记录写入到 redo log 中，并更新内存中的记录，并不是立即写入磁盘的。存储引擎会在适当的时候把操作记录同步到磁盘里，即刷新脏页。

但是，redo log 大小是有限的，且是循环写入的。在高并发场景下，redo log 很快被写满了，但是数据来不及同步到磁盘里，这时候就会产生脏页，并且还会阻塞后续的写入操作，导致 SQL 执行变慢。

#### 锁

如果执行语句涉及的行记录被其他锁占有且对方的锁类型与自身的锁类型互斥，就需要等待对方释放锁，甚至可能出现大事务导致当前事务等待超时等极端情况。

在 InnoDB 1.0 版本之前，用户可以通过 SHOW ENGINE INNODB STATUS 命令查看当前锁请求的信息。从 InnoDB 1.0 版本开始，在 INFORMATION_SCHEMA 架构下添加了表 INNODB_TRX、INNODB_LOCKS、INNODB_LOCK_WAITS 三张表，用户可以更简单地监控当前事务和锁状态。

如果是一直慢，是我们的慢 SQL 优化的重点。

### 慢 SQL 优化

如果在数据稳定的情况，一条 SQL 执行始终很慢，就要好好分析分析这条 SQL 了。我们可以通过 EXPLAIN 关键字分析当前 SQL 的执行计划。

首先构建数据模型：

```sql
-- 建表
CREATE TABLE slow (
    id int(11) NOT NULL,
    b int(11) DEFAULT NULL,
    c int(11) DEFAULT NULL,
    d VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = INNODB;

-- 创建索引
ALTER TABLE slow ADD KEY (b);
ALTER TABLE slow ADD KEY (d);

-- 存储过程构建数据
CREATE PROCEDURE batch_insert() 
BEGIN 

	DECLARE max int;
	DECLARE temp int;
	SET max = 10000;
	SET temp = 1;

	WHILE temp < max DO
	
		INSERT INTO
		 slow
		VALUES
		 (temp,temp*10,temp*100,temp*1000);
		 
		SET temp = temp + 1;
		
	END WHILE;
	
END;

-- 调用批量插入的存储过程
CALL batch_insert();

-- 删除存储过程和清除数据
-- DROP PROCEDURE batch_insert;
-- DELETE FROM slow;
```

索引是最影响 SQL 执行效率的因素，这是我们慢 SQL 优化的主战场。

#### 没创建索引

过分了，有的 SQL 的 WHERE 条件没创建对应索引，没有索引就只能走全部扫描，这很容易产生慢 SQL。

#### 字段有索引但是没使用

```sql
-- 可以使用索引的 SQL
EXPLAIN SELECT * FROM slow WHERE b = 999;
EXPLAIN SELECT * FROM slow WHERE d = '999000';
```

上面的两条 SQL 都可以使用索引，但是我们简单调整一下，就会导致 SQL 无法使用索引。

- 隐式转换

```sql
EXPLAIN SELECT * FROM slow WHERE d = 999000;
```

上述 SQL 中，我们把字符类型的 d 字段用数值类型赋值，就会导致无法使用索引

- 函数和计算

```sql
EXPLAIN SELECT * FROM slow WHERE LEFT(d,3) = 999;
EXPLAIN SELECT * FROM slow WHERE b - 1 = 999;
```

上述 SQL 中，我们对 WHERE 条件（= 左侧）使用了函数和计算，这也会导致无法使用使用。

#### 其他原因
- 成本

```sql
EXPLAIN SELECT * FROM slow WHERE b > 10 
```

上述 SQL 也是不使用索引的。

我们知道，InnoDB 存储引擎中数据是索引组织的。索引分为聚簇索引和辅助索引。聚簇索引存放的整行记录，辅助索引中存放的是主键的值。执行一条 SQL，会先从辅助索引中获取主键的值，在到聚簇索引中查询出对应数据。

MySQL 执行 SQL 会经过查询优化器，它们是基于成本分析的。上述 SQL 中 b > 10 的条件所对应的行记录几乎是整个表的数据，由于走辅助索引还需要回表，效率可能低于全表扫描，因此查询优化器制定执行计划不使用索引。

- 写法

有些写法是无法使用索引的，如 <>、NOT IN、LIKE "%内容"、OR、REGEXP等，还有出于成本原因也不会使用索引，如 IN 很多字段。

- 数据库选错索引

有一种极端情况是数据库选错索引，查询分析器是通过索引的散列程度（cardinality）来判断索引的区分度的，cardinality 表示某个索引对应的列包含多少个不同的值。如果 cardinality 大大少于数据的实际散列程度，那么索引就基本失效了，这时候我们需要使用命令：

```sql
analyze table slow;
```

来修复索引。

### 数据量和复杂度

很多时候，单从 SQL 层面已经无法进一步优化了，症结在于数据量和复杂度。

#### 数据量

如果单表太大，达到千万级，InnoDB 存储引擎的性能会明显下降。一般单表数据达到 500W 行或者大小达到 100G 就要考虑优化大表了。不建议直接上来就分表，应该先从 SQL 优化入手，接着逐级深入索引、缓存、消息队列、分区、垂直分表、水平分表，能不分表就不分表，尤其是避免水平分表。

事实上，分表的阈值和实际记录的条数无关，而与 MySQL 的配置以及机器的硬件有关。因为，MySQL 为了提高性能，会将表的索引装载到内存中。InnoDB buffer size 足够的情况下，其能完成全加载进内存，查询不会有问题。但是，当单表数据库到达某个量级的上限时，导致内存无法存储其索引，使得之后的 SQL 查询会产生磁盘 IO，从而导致性能下降。

#### 复杂度

有些查询条件是否复杂是由于业务需要，不可避免的产生慢 SQL，进行慢 SQL 优化的时候还需要分析业务是否可以优化或做一定割舍。