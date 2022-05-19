# 大小写问题

- 目录
  - [COLLATE](#COLLATE)
    - [默认 COLLATE](#默认-COLLATE)
    - [binary 字段 ](#binary-字段)
    - [COLLATE=utf8mb4_bin](#COLLATE=utf8mb4_bin)
  - [配置文件](#配置文件)

MySQL 在 Linux 上区分大小写，但是在 Windows 下默认不区分大小写。

### COLLATE

在 [字符集问题](https://github.com/lazecoding/Note/blob/main/note/articles/mysql/character-set.md) 中，我们提到 collate 会影响大小写区分：

- _bin: 表示的是 binary case sensitive collation，也就是说是区分大小写。
- _cs: case sensitive collation，区分大小写。
- _ci: case insensitive collation，不区分大小写。

下面做一些验证。

#### 默认 COLLATE

建表：

```sql
-- 1.默认 COLLATE 建表
CREATE TABLE `case_default` (
   `code` varchar(11) NOT NULL,
	 `info` varchar(11),
   PRIMARY KEY (`code`)
) ENGINE = InnoDB;
```

写入数据：

```sql
-- 插入 A、a
INSERT INTO case_default (code) VALUES ('A');
INSERT INTO case_default (code) VALUES ('a');


-- 运行结果
INSERT INTO case_default (code) VALUES ('A')
> Affected rows: 1
> 时间: 0.042s

INSERT INTO case_default (code) VALUES ('a')
> 1062 - Duplicate entry 'a' for key 'PRIMARY'
> 时间: 0s
```

查询 COLLATE：

```sql
-- 查询 COLLATE
show full columns from case_default;

-- 结果简写
code COLLATE utf8mb4_general_ci
```

#### binary 字段 

建表：

```sql
-- 1.字段 binary 建表
CREATE TABLE `case_binary` (
   `code` varchar(11) binary  NOT NULL,
 	 `info` varchar(11),
   PRIMARY KEY (`code`)
) ENGINE = InnoDB;
```

写入数据：

```sql
-- 插入 A、a
INSERT INTO case_binary (code) VALUES ('A');
INSERT INTO case_binary (code) VALUES ('a');


-- 运行结果
INSERT INTO case_binary (code) VALUES ('A')
> Affected rows: 1
> 时间: 0.07s

INSERT INTO case_binary (code) VALUES ('a')
> Affected rows: 1
> 时间: 0.036s
```

查询 COLLATE：

```sql
-- 查询 COLLATE
show full columns from case_binary;

-- 结果简写
code COLLATE utf8mb4_bin
```

#### COLLATE=utf8mb4_bin

建表：

```sql
-- 1.字段 binary 建表
CREATE TABLE `case_collate_bin` (
   `code` varchar(11) NOT NULL,
 	 `info` varchar(11),
   PRIMARY KEY (`code`)
) ENGINE = InnoDB COLLATE=utf8mb4_bin;
```

写入数据：

```sql
-- 插入 A、a
INSERT INTO case_collate_bin (code) VALUES ('A');
INSERT INTO case_collate_bin (code) VALUES ('a');


-- 运行结果
INSERT INTO case_collate_bin (code) VALUES ('A')
> Affected rows: 1
> 时间: 0.032s

INSERT INTO case_collate_bin (code) VALUES ('a')
> Affected rows: 1
> 时间: 0.035s
```

查询 COLLATE：

```sql
-- 查询 COLLATE
show full columns from case_binary;

-- 结果简写
code COLLATE utf8mb4_bin
```

总结，字段的 COLLATE 为 utf8mb4_bin 时即可区分大小写。

### 配置文件

还可以在 MySQL 的配置文件 my.ini 中增加一行：

```C
lower_case_table_names = 0
```

> 0：区分大小写，1：不区分大小写。