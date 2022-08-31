# Redis 6.0 总览

- 目录
  - [众多新模块 API](#众多新模块-API)
  - [更好的过期循环](#更好的过期循环)
  - [支持 SSL](#支持-SSL)
  - [ACLs 权限控制](#ACLs-权限控制)
    - [ACL 使用](#ACL-使用)
    - [ACL 规则](#ACL-规则)
  - [RESP3 协议](#RESP3-协议)
  - [客户端缓存](#客户端缓存)
  - [多线程 IO](#多线程-IO)
  - [其余特性](#其余特性)

本文总览 Redis 6.0 新特性。

### 众多新模块 API

Redis 6 中模块（modules）API 开发进展非常大，因为 Redis Labs 为了开发复杂的功能，从一开始就用上 Redis 模块。Redis 可以变成一个框架，利用 Modules 来构建不同系统，而不需要从头开始写然后还要 BSD 许可。Redis 一开始就是一个向编写各种系统开放的平台。如：Disque 作为一个 Redis Module 使用足以展示 Redis 的模块系统的强大。集群消息总线 API、屏蔽和回复客户端、计时器、模块数据的 AOF 和 RDB 等。

### 更好的过期循环

Redis 6 重构了过期循环（expire cycle），更快地回收到期的 key。

### 支持 SSL

Redis 6 连接支持 SSL，更加安全。

### ACLs 权限控制

Redis 6 开始支持 ACL，该功能通过限制对命令和 key 的访问来提高安全性。
ACL 的工作方式是在连接之后，要求客户端进行身份验证（用户名和有效密码）；如果身份验证阶段成功，则连接与指定用户关联，并且该用户具有限制。
在默认配置中，Redis 6 的工作方式与 Redis 的旧版本完全相同，每个新连接都能够调用每个可能的命令并访问每个键，因此 ACL 功能与旧版本向后兼容。客户和应用程序。依旧使用 requirepass 配置密码的，但现在只是为默认用户设置密码。

#### ACL 使用

```C
 1) ACL <subcommand> arg arg ... arg. Subcommands are:
 2) LOAD                             -- Reload users from the ACL file.
 3) SAVE                             -- Save the current config to the ACL file.
 4) LIST                             -- Show user details in config file format.
 5) USERS                            -- List all the registered usernames.
 6) SETUSER <username> [attribs ...] -- Create or modify a user.
 7) GETUSER <username>               -- Get the user details.
 8) DELUSER <username> [...]         -- Delete a list of users.
 9) CAT                              -- List available categories.
10) CAT <category>                   -- List commands inside category.
11) GENPASS [<bits>]                 -- Generate a secure user password.
12) WHOAMI                           -- Return the current connection username.
13) LOG [<count> | RESET]            -- Show the ACL log entries.
```

Redis 6 中 auth 命令在 Redis 6 中进行了扩展，因此现在可以在两个参数的形式中使用它：

```C
#before Redis 6
AUTH <password>
#Redis 6
AUTH <username> <password>
```

默认情况下，有一个用户定义，称为 default。可以使用 ACL LIST 命令来查看，默认配置的 Redis 实例的配置是：

```C
#无密码
127.0.0.1:6379> ACL LIST
1) "user default on nopass ~* +@all"
#有密码
127.0.0.1:6379> ACL LIST
1) "user default on #ce306e0ee195cc817620c86d7b74126d0d66c077b66f66c10f1728cf34a214d3
127.0.0.1:6379> ACL WHOAMI
"default"
127.0.0.1:6379> ACL USERS
1) "default"
```

每行的开头都是 "user"，后面跟用户名，`on` 表示用户是启用的，否则是禁用的。`nopass` 表示无密码，否则表示有密码认证。`~*` 表示能够访问所有的 key，`+@all` 表示能够调用所有可能的命令。

#### ACL 规则

**启用和禁止用户**

- `on`：启用用户：可以以该用户身份进行认证。
- `off`：禁用用户：不再可以与此用户进行身份验证，但是已经过身份验证的连接仍然可以使用。如果默认用户标记为off，则无论默认用户配置如何，新连接都将开始不进行身份验证，并且要求用户使用AUTH选项发送AUTH以进行身份验证。

**允许和禁止命令**

- `+<command>`：将命令添加到用户可以调用的命令列表中。
- `-<command>`：将命令从用户可以调用的命令列表中删除。
- `+@<category>`：添加该类别中要由用户调用的所有命令，有效类别为@ admin，@ set，@ sortedset等，通过调用ACL CAT命令查看完整列表。特殊类别@all表示所有命令，包括当前在服务器中存在的命令，以及将来将通过模块加载的命令。

```C
127.0.0.1:6379> ACL CAT
 1) "keyspace"
 2) "read"
 3) "write"
 4) "set"
 5) "sortedset"
 6) "list"
 7) "hash"
 8) "string"
 9) "bitmap"
10) "hyperloglog"
11) "geo"
12) "stream"
13) "pubsub"
14) "admin"
15) "fast"
16) "slow"
17) "blocking"
18) "dangerous"
19) "connection"
20) "transaction"
21) "scripting"
```

- `-@<category>`：从客户端可以调用的命令列表中删除命令。
- `+<command>|subcommand`：允许使用本来禁用的命令的特定子命令。该语法不允许使用-<command>|subcommand，例如-DEBUG|SEGFAULT，只能以“ +”开头的加法运算符。如果命令整体上已处于活动状态，则此ACL将导致错误。
- `allcommands`：+ @ all的别名。
- `nocommands`：-@ all的别名。

**允许或禁止访问某些 Key**

- `~<pattern>`：添加可以在命令中提及的键模式。例如 ~ 和 allkeys 允许所有键。
- `* resetkeys`：使用当前模式覆盖所有允许的模式。如：~foo: ~bar: resetkeys ~objects: ，客户端只能访问匹配 object: 模式的 KEY。

**为用户配置有效密码**

- `><password>`：将此密码添加到用户的有效密码列表中。例如，`>mypass` 将 "mypass" 添加到有效密码列表中。该命令会清除用户的 nopass 标记。每个用户可以有任意数量的有效密码。
- `<<password>`：从有效密码列表中删除此密码。若该用户的有效密码列表中没有此密码则会返回错误信息。
- `#<hash>`：将此SHA-256哈希值添加到用户的有效密码列表中。该哈希值将与为 ACL 用户输入的密码的哈希值进行比较。允许用户将哈希存储在 users.acl 文件中，而不是存储明文密码。仅接受 SHA-256 哈希值，因为密码哈希必须为 64 个字符且小写的十六进制字符。
- `!<hash>`：从有效密码列表中删除该哈希值。当不知道哈希值对应的明文是什么时很有用。
- `nopass`：移除该用户已设置的所有密码，并将该用户标记为 nopass 无密码状态：任何密码都可以登录。resetpass 命令可以清除nopass这种状态。
- `resetpass`：情况该用户的所有密码列表。而且移除 nopass 状态。resetpass 之后用户没有关联的密码同时也无法使用无密码登录，因此 resetpass 之后必须添加密码或改为 nopass 状态才能正常登录。
- `reset`：重置用户状态为初始状态。执行以下操作 resetpass，resetkeys，off，-@all。

### RESP3 协议

RESP（Redis Serialization Protocol）是 Redis 服务端与客户端之间通信的协议。

RESP3 是 RESP version 2 的更新版本。RESP v2 大致从 Redis 2.0 开始支持（其实 1.2 就支持了，只不过 Redis 2.0 是第一个仅支持此协议的版本）。

```C
127.0.0.1:6379> hello 2
1) "server"
2) "Redis"
3) "version"
4) "6.0.5"
5) "proto"
6) (integer) 2
7) "id"
8) (integer) 7
9) "mode"
10) "standalone"
11) "role"
12) "master"
13) "modules"
14) (empty array)
127.0.0.1:6379> hello 3
1# "server" => "Redis" // 服务名称
2# "version" => "6.0.5" // 版本号
3# "proto" => (integer) 3 // 支持的最高协议
4# "id" => (integer) 7 // 客户端连接 ID
5# "mode" => "standalone" //  模式："standalone", "sentinel", "cluster"
6# "role" => "master" //  "master" 或 "replica"
7# "modules" => (empty array) // 加载的模块列表
```

点击 [RESP version 1](https://github.com/lazecoding/Note/blob/main/note/articles/redis/客户端和服务器.md#通信协议) 了解更多。

### 客户端缓存

Redis 客户端（Client side caching）缓存在某些方面进行了重新设计，特别是放弃了缓存槽（caching slot）方法而只使用 key 的名称。在分析了备选方案之后，在其他 Redis 核心团队成员的帮助下，这种方法最终看起来更好。

客户端缓存重新设计中引入了广播模式（broadcasting mode）。在使用广播模式时，服务器不再尝试记住每个客户端请求的 key。取而代之的是，客户订阅 key 的前缀：每次修改匹配前缀的 key 时，这些订阅的客户端都会收到通知。这意味着会产生更多的消息（仅适用于匹配的前缀），但服务器端无需进行任何内存操作。

### 多线程 IO

在 Redis 6 之前，Redis 通过单线程来处理网络请求和命令执行，具体分析可以点击查看 [事件驱动](https://github.com/lazecoding/Note/blob/main/note/articles/redis/事件驱动.md) 。

Redis 6 引入多线程 IO（Threaded I/O），但多线程部分只是用来处理网络数据的读写和协议解析，执行命令仍然是单线程。之所以这么设计是不想因为多线程而变得复杂，需要去控制 key、lua、事务、LPUSH/LPOP 等并发问题。

默认情况下，Redis多线程是禁用的，我们可以在配置文件 `redis.conf` 开启：

```C
# 开启多线程 IO
io-threads-do-reads yes
 
# 配置线程数量，如果设为 1 就是主线程模式。
io-threads 4
```

多线程下数据交互关系：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/多线程下数据交互关系.png" width="600px">
</div>

多线程下执行流程：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/redis/多线程下执行流程.png" width="600px">
</div>

流程简述：

- 主线程负责接收建立连接请求，获取 socket 放入全局等待读处理队列。
- 主线程处理完读事件之后，通过 RR(Round Robin) 将这些连接分配给这些 IO 线程。
- 主线程阻塞等待 IO 线程读取 socket 完毕。
- 主线程通过单线程的方式执行请求命令，请求数据读取并解析完成，但并不执行。
- 主线程阻塞等待 IO 线程将数据回写 socket 完毕。
- 解除绑定，清空等待队列。

特点：

- IO 线程要么同时在读 socket，要么同时在写，不会同时读或写（我觉得是为了避免处理读写并发的安全问题）。
- IO 线程只负责读写 socket 解析命令，不负责命令处理。

### 其余特性

#### 无盘复制和 PSYNC2

现在，Redis 用于复制的 RDB 文件如果不再有用，将立即被删除。不过，在某些环境中，最好不要将数据放在磁盘上，而只放在内存中。

```C
repl-diskless-sync no
repl-diskless-sync-delay 5
repl-diskless-load disabled
```

复制协议 PSYNC2 现在得到了改进。Redis 将能够更频繁地部分重新同步，因为它能够修整协议中的最终 PING，从而使副本和主副本更有可能找到一个公共的偏移量。

#### Redis-benchmark 支持集群

#### Redis-cli 优化

#### 重写 Systemd 支持

#### Redis 集群代理

Redis 集群代理与 Redis 6 一同发布（但在不同的 repo）。

在 Redis 集群中，客户端会非常分散，Redis 6 为此引入了一个集群代理，可以为客户端抽象 Redis 群集，使其像正在与单个实例进行对话一样。同时在简单且客户端仅使用简单命令和功能时执行多路复用。

#### RDB 更快加载

Redis 6.0，RDB 文件的加载速度比之前变得更快了。根据文件的实际组成（较大或较小的值），大概可以获得 20-30％ 的改进。除此之外，INFO 也变得更快了，当有许多客户端连接时，这会消耗很多时间，不过现在终于消失了。

#### SRANDMEMBER 和类似的命令具有更好的分布

#### STRALGO 命令

STRALGO 实现了复杂的字符串算法，目前唯一实现的是 LCS（最长的公共子序列）。

```C
127.0.0.1:6379> STRALGO LCS keys name zijie
"1"
127.0.0.1:6379> STRALGO LCS keys name zijie  len
(integer) 1
```

#### 带有超时的 Redis 命令

除了 BLPOP 命令，其他用于接受秒的命令现在都接受十进制数字，而且实际分辨率也得到了改进，以使其永远不会比当前的“HZ”值更差，因为其不管连接客户端的数量。