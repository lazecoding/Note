# Redis 6.0 总览

- 目录
  - [众多新模块 API](#众多新模块-API)
  - [更好的过期循环](#更好的过期循环)
  - [支持 SSL](#支持 SSL)
  - [ACLs 权限控制](#ACLs-权限控制)
    - [ACL 使用](#ACL-使用)
    - [ACL 规则](#ACL-规则)

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