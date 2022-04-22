# 字符集问题

- 目录
  - [查看与修改](#查看与修改)
  - [排序规则](#排序规则)

MySQL 字符集很有趣，慎用 uft8 编码，这是个虚假的 UTF-8 编码，真正的是 utf8mb4 编码。uft8 自作聪明用 3 字节来存储，而 UTF-8 最大可能占用 4 字节，导致部分数据无法存储。

### 查看与修改

查看：

```sql
-- 查看数据库系统字符集
SHOW VARIABLES LIKE 'character%';

-- 查看各个数据库字符集
SELECT SCHEMA_NAME,DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME,SQL_PATH FROM information_schema.SCHEMATA;

-- 查看表中各个字段的字符集（以及其他属性）
SHOW full columns FROM <tableName>;

-- 修改某个字段字符集
ALTER TABLE <tableName> CHANGE <columnName> <columnName> VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
```

修改：

```sql
-- 修改表的字符集（但是老字段不变）
ALTER TABLE <tableName> CHARACTER SET utf8mb4;

-- 修改表字段字符集(老字段通过 CONVERT TO 变更)
ALTER TABLE <tableName> CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

-- 修改数据库默认字符集
ALTER DATABASE `<dbName>` DEFAULT CHARACTER SET utf8mb4  COLLATE utf8mb4_bin;
```

### 排序规则

> 我们推荐使用 utf8mb4 编码，再次基础上了解排序规则。

MySQL 常用排序规则 utf8mb4_general_ci、utf8mb4_unicode_ci、utf8mb4_bin。

> ci 即 case insensitive，不区分大小写。

- `utf8mb4_unicode_ci`：是基于标准的 Unicode 来排序和比较，能够在各种语言之间精确排序，Unicode 排序规则为了能够处理特殊字符的情况，实现了略微复杂的排序算法。
- `utf8mb4_general_ci`：是一个遗留的校对规则，不支持扩展，它仅能够在字符之间进行逐个比较。utf8_general_ci 校对规则进行的比较速度相对快一点（其实也没快多少），但是与使用 utf8mb4_unicode_ci 的校对规则相比，比较正确性较差。
- `utf8mb4_bin`：将字符串每个字符用二进制数据编译存储，区分大小写，而且可以存二进制的内容。

> utf8_genera_ci 是不仅不区分大小写的，也不区分 e 和 é 这类字符；而 utf8mb4_bin 区分。