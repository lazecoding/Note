# Lua 脚本

Lua 是一个高效的轻量级脚本语言，通过内嵌对 Lua 环境的支持，Redis 解决了长久以来不能高效地处理 CAS（check-and-set）命令的缺点，并且可以通过组合使用多个命令，轻松实现以前很难实现或者不能高效实现的模式。

### 为什么使用脚本
- 减少网络开销：可以将多个请求通过脚本的形式一次发送，减少网络时延。
- 原子操作：Redis 会将整个脚本作为一个整体执行，中间不会被其他命令插入。因此在编写脚本的过程中无需担心会出现竞态条件，无需使用事务。
- 复用：客户端发送的脚本会永久存在 Redis 中，这样，其他客户端可以复用这一脚本而不需要使用代码完成相同的逻辑。

### 如何使用脚本

命令格式：

```C
EVAL script numkeys key [key ...] arg [arg ...]
```

- script 是第一个参数，为脚本内容。
- 第二个参数 numkeys 指定后续参数有几个 key。
- key [key ...]，是要操作的键，可以指定多个，在 Lua 脚本中通过 KEYS[1]，KEYS[2] 获取。
- arg [arg ...]，参数，在 Lua 脚本中通过 ARGV[1]，ARGV[2]获取。

使用示例：

```C
> eval "return ARGV[1]" 0 2021 
"2021"

> eval "return {ARGV[1],ARGV[2]}" 0 2020 2021
1) "2020"
2) "2021"

> eval "return {KEYS[1],KEYS[2],ARGV[1],ARGV[2]}" 2 key1 key2 argv1 argv2
1) "key1"
2) "key2"
3) "argv1"
4) "argv2"

> eval "redis.call('SET', KEYS[1], ARGV[1]);redis.call('EXPIRE', KEYS[1], ARGV[2]); return 1;" 1 key1 argv1 60
(integer) 1
> ttl key1
(integer) 59
> get key1
"argv1"
```

- redis.call() 用于调用 Redis 命令。
