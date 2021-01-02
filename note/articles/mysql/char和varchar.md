# char 和 varchar

### 定长还是变长？
通常理解 char 是定长字符类型，varchar 是变长字符类型。 char 字符类型会用空格填充空余的空间，varchar 保存的是实际长度的数据。

这带来的影响是，查询 varchar 字符类型的数据要先提供变成字段列表中的记录得到这个数据的长度，而 char 字符类型的数据是定长，不需要单独获取，char 往往有着比 varchar 更好的读写效率，但是浪费一定存储空间。

低版本中 CHR(N) 中 N 代表的是字节长度，上面的说法是没有争议的，但是从 MySQL 4.1 版本开始，CHR(N) 中 N 代表的是字符长度，也就意味着不同数据字符集下，char 字符类型的列内存存储的可能不是定长的数据。

``` sql
CREATE TABLE `encode_chr` (
  `c_chr` char(10) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


INSERT INTO encode_chr SELECT 'A';
INSERT INTO encode_chr SELECT 'SQL';
INSERT INTO encode_chr SELECT '数据库';

SELECT c_chr,CHAR_LENGTH(c_chr),LENGTH(c_chr),HEX(c_chr)  FROM encode_chr;
```

| c_chr   | CHAR_LENGTH(c_chr) | LENGTH(c_chr) |      HEX(c_chr)      |
| ------- | ------------------ | ------------- | -------------------- |
|  A      |        1           |       1       |         41           |
|  SQL    |        3           |       3       |         53514C       |
|  数据库  |        3           |       9       |  E695B0E68DAEE5BA93  |

从 CHAR_LENGTH(c_chr)、LENGTH(c_chr)、HEX(c_chr) 函数我们分别的得到了字符的字符长度、字节长度、内部十六进制存储，虽然对于我们依然可以说 char 是字符长度固定的数据类型，但对于使用来说并没有上面意义。长度固定应该是从内存存储来说的，我们可以看到对于多字符集下的 char 数据类型的字节长度是可变的，需要在行记录的变长字段列表中记录 char 数据类型的长度，读取数据时也必须获取数据长度。 因此，对于多字节字符集（如：utf8mb4、gbk等），char 和 varchar 的实际存储基本是没有区别的。


### 长度上限

以 MySQL 4.1 版本之后来说 char 数据类型的长度上限是 255 字符长度，而 varchar 数据类型长度上限是 65535 字节长度。

其实严格说 `varchar 数据类型长度上限是 65535 字节长度` 是不准确的，首先 65535 是一个理论值，实际阈值还需要收缩吗，因为变长列表有一个 1-2 字节长度的前缀标识，实际需要减去 2 个字节长度，此外还需要一个字节来表示是否是 NULL 值，再减去 1 个字节长度，即 varchar 字节长度阈值是 65532。

既然varchar 数据类型长度上限是以字节为单位，那么自然收到字符集的影响，下面采用 utf8mb4 字符集演示。

我们先计算出最大的字符长度 N = 65532 / 4 = 16383，即 utf8mb4 下 varchar 的字符长度最大值是 16383。

Demo1:
```sql
CREATE TABLE `len_demo1` (
  `vc_chr` varchar(16384) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

> 1074 - Column length too big for column 'vc_chr' (max = 16383); use BLOB or TEXT instead
```

首先测试 varchar 字符长度大于 16383，提示字段长度 max = 16383。

Demo2:
```sql
CREATE TABLE `len_demo2` (
  `vc_chr` varchar(16383) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

> OK
```
意料之中的成功。

Demo3:
```sql
CREATE TABLE `len_demo3` (
  `vc_chr` varchar(16383) DEFAULT NULL,
  `c_chr` char(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    
> 1118 - Row size too large. The maximum row size for the used table type, not counting BLOBs, is 65535. 
This includes storage overhead, check the manual. You have to change some columns to TEXT or BLOBs
```

居然报错了，varchar 长度还是 16383，至少加入了一个 char 数据类型。从报错中可以看到的是行记录太大,因为MySQL要求一个行的定义长度不能超过65535。

Demo4:
```sql
CREATE TABLE `len_demo4` (
  `vc_chr` varchar(16128) DEFAULT NULL,
  `c_chr` char(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    
> OK
```
当我们减去 char 的数据类型长度，建立成功。

我们再设计表的时候，要合理分配空间，不仅要考虑数据类型的长度上限，还要注意行记录大小。