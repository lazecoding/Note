# 触发器

- 目录
    - [创建触发器](#创建触发器)
    - [trigger_event](#trigger_event)
    - [BEGIN...END](#BEGIN...END)
        - [关键字](#关键字)
    - [执行顺序](#执行顺序)
    - [查看和删除](#查看和删除)
    - [例子](#例子)

在指定表上执行 INSERT、UPDATE、DELETE 动作的 AFTER 和 BEFORE 时机，执行指定的一个或一组 SQL 语句。

### 创建触发器

``` SQL
CREATE TRIGGER trigger_name trigger_time trigger_event ON tbl_name FOR EACH ROW trigger_stmt
```

- trigger_name : 触发器名称，自定义。
- trigger_time： 触发时机，AFTER 和 BEFORE。
- trigger_event : 触发事件，INSERT、UPDATE、DELETE。
- tbl_name : 需要建立触发器的表名。
- trigger_stmt : 触发程序体，可以是一条 SQL 语句或是 BEGIN 和 END 包含的一组语句

### trigger_event

> INSERT 型触发器 ：插入某一行时激活触发器，可能 INSERT、LOAD DATA、REPLACE 语句触发。
<br>
UPDATE 型触发器 ： 更改某一行时激活触发器，可能通过 UPDATE 语句触发。
<br>
DELETE 型触发器 ： 删除某一行时激活触发器，可能通过 DELETE、REPLACE 语句触发。

### BEGIN...END

``` SQL
BEGIN
[statement_list]
END
```
`statement_list` 代表一个或多个语句的列表，列表内的每条语句都必须用分号（;）来结尾(默认值)。

#### 关键字

statement_list 中可以使用 NEW 和 OLD 两个关键字，该关键字，表示触发了触发器的哪一行数据。
- INSERT 触发器中,NEW用来表示将要 (BEFORE) 或已经 (AFTER) 插入的新数据。
- UPDATE 触发器中，OLD 用来表示将要或已经被修改的原数据，NEW 用来表示将要或已经修改为的新数据。
- DELETE 触发器中，OLD 用来表示将要或已经被删除的原数据。

另外，OLD 是只读的，而 NEW 则可以在触发器中使用 SET 赋值，这样不会再次触发触发器，造成循环调用。

### 执行顺序

触发器的执行顺序：
- 如果 BEFORE 触发器执行失败，SQL 无法正确执行。
- SQL 执行失败时，AFTER 型触发器不会触发。
- AFTER 类型的触发器执行失败，SQL 会回滚。

### 查看和删除

``` SQL
-- 查看触发器
SHOW TRIGGERS [FROM schema_name];
-- 删除触发器
DROP TRIGGER [IF EXISTS] [schema_name.]trigger_name;
```

### 例子

需求：一个字段的值变化时改变另一个字段的值。
例子如下：
``` SQL
-- 修改数据触发
CREATE TRIGGER trigger_1 BEFORE UPDATE ON cms FOR EACH ROW
BEGIN
	SET new.c_date = new.c_deploytime;
END;
-- 新增数据触发
CREATE TRIGGER trigger_2 BEFORE INSERT ON cms FOR EACH ROW
BEGIN
	SET new.c_date = new.c_deploytime;
END;
```
