

## 关于本书 ##

这本书，The Little MongoDB Book，基于 Attribution-NonCommercial 3.0 Unported license 发布。**您不需要为本书付钱。**

您有权复制、分发、修改或展示本书。但请认可本书的作者是 Karl Seguin，也请勿将其用于任何商业用途。

您可以在以下链接查看该许可证的全文：

<http://creativecommons.org/licenses/by-nc/3.0/legalcode>

### 关于作者 ###

Karl Seguin 是一位在多个技术领域有着丰富经验的研发人员，精通 .NET 以及 Ruby。作为技术文档撰写人，他有时还会参与 OSS 项目的工作或做演讲。在 MongoDB 方面，他曾是C# MongoDB library NoRM 的核心开发人员，开发了互动教程 [mongly](http://mongly.com) 以及 [Mongo Web Admin](https://github.com/karlseguin/Mongo-Web-Admin)。他还用 MongoDB 开发了 [mogade.com](http://mogade.com/) 为业余游戏开发者提供免费服务。

Karl 还著有 [The Little Redis Book](http://openmymind.net/2012/1/23/The-Little-Redis-Book/)。

他的博客 <http://openmymind.net>，推特账号是 [@karlseguin](http://twitter.com/karlseguin)。

### 最新版本 ###

本书的最新版本可以在下面的链接找到：

<http://github.com/karlseguin/the-little-mongodb-book>.

## 简介 ##

 > 本章很短，但不是我的错，MongoDB就是那么简单易学。

人们总是说科技的发展风驰电掣。确实，一直以来都不断有新的技术涌现出来。但是我却一直坚持认为程序员所用的基本技术的发展相对而言就缓慢很多。您可以很多年不学习什么但还是可以混过去。让人瞩目的是业已成熟的技术被替代的速度。仿佛一夜间，那些长期以来业已成熟的技术忽然就失去了开发者的关注，昔日地位岌岌可危。

NoSQL 步攻陷了传统关系数据库的领地，就是这种急剧转变最好的例子。仿佛就在昨天所有的网页还是由一些 RDBMS 驱动的，而一早起来就已经有大约 5 种 NoSQL 的方案证明了他们都是有价值的解决方案。

虽然这些转变看起来是一夜间就发生的，事实却是这些新生的技术历经多年才被接受并应用于实践。一开始先是由一小部分开发者或者企业推动，然后逐步吸取教训，改善方案并见证新技术地位的确立。其他的跟随者之后也慢慢地开始了尝试。对于 NoSQL 来说也是一样。很多新方案的出现都不是为了去代替更加传统的存储方案，而是为了填补后者所能满足需求之外的一些空白。

说了那么多，在这里我们首先要做的是弄清楚什么是 NoSQL。这是一个很宽泛的概念，不同的人有不同的解读。我个人用 NoSQL 来泛指参与数据存储的系统。换句话说，NoSQL（还是我个人的意见），是一种观念，这种观念认为维护数据的持久性不必是单个系统的责任。相比之下，关系数据库的缔造者一开始就力图把他们的软件当作通用解决方案。NoSQL则更倾向于负责系统中的一小部分功能：限定了部分功能，便可以使用最适合的工具。因此，您以后的 NoSQL 架构中依旧有可能利用到关系数据库，比如说 MySQL，于此同时还可能在特定的部件使用 Redis，还会用 Hadoop 进行大量的数据处理。简而言之，NoSQL 就是保持开放和警醒，利用已有的可用的工具和方法去管理您的数据。

您也许会想，MongoDB 怎么能搞定那么多？作为一个面向文档的数据库，Mongo 是一个更加通用的 NoSQL 方案。可以认为它是关系数据库的一个替代方案。和关系数据库一样，Mongo 也可以和其它更细化的 NoSQL 方案协作而更加强大。MongoDB 既有优点也有缺点，我们在后续的章节中都会提及。

> 您也许也注意到了，在书中我们是混用 Mongo 和 MongoDB 这两个词的。

## 准备 ##

本书大部分篇幅会用来关注 MongoDB 的核心功能。所以我们基本上使用的是 MongoDB 的外壳（shell）。shell 在学习 MongoDB 还有管理数据库的时候很有用，不过您的实际代码还是会用相应的语言来驱动 MongoDB 的。

这也引出了关于 MongoDB 您首先需要了解的东西：它的驱动。MongoDB 有许多针对不同语言的 [官方驱动](http://www.mongodb.org/display/DOCS/Drivers)。可以认为这些驱动和您所熟知的各种数据库驱动是一样的。基于这些驱动，MongoDB 的开发社区又搭建了更多语言/框架相关的库。比如说 [NoRM](https://github.com/atheken/NoRM) 就是一个实现了 LINQ 的 C# 库，还有 [MongoMapper](https://github.com/jnunemaker/mongomapper)，一个很好地支持 ActiveRecord 的 Ruby 库。您可以自行决定直接针对 MongoDB 的核心驱动编程，或者采用一些高层的库。在这里指出这点，是因为不少 MongoDB 的新手都会为既有官方驱动又有社区提供的库而困惑不已——前者着重与 MongoDB 的核心通讯/连接，而后者则提供了更多语言/框架相关的具体实现。

我建议您在阅读本书的同时，也在 MongoDB中 尝试我给出的例子。如果在这个过程中您自己发现了什么问题，也可以在 MongoDB 环境中探索需求答案。安装并运行 MongoDB 其实很简单，只需要几分钟的时间。那么现在就开始吧。

1. 从 [官方下载页面](http://www.mongodb.org/downloads) 的第一行（这是推荐的稳定版本）下载与您操作系统相应的安装包。根据不同的开发需要，选择 32 位或是 64 位的包。

2. 解压下载的包（到任意路径）并进入 `bin` 子目录，暂且不要执行任何命令。让我先介绍一下，`mongod` 将启动服务器进程而 `mongo` 会打开客户端的shell——大部分时间我们将和这两个可执行文件打交道。

3. 在 `bin` 子目录中创建一个新的文本文件，取名为 `mongodb.config`。

4. 在 `mongodb.config` 中加一行：`dbpath=PATH_TO_WHERE_YOU_WANT_TO_STORE_YOUR_DATABASE_FILES`。例如，在Windows中您需要添加的可能是 `dbpath=c:\mongodb\data` 而在 Linux 下可能就是 `dbpath=/etc/mongodb/data`。

5. 确认您指定的 `dbpath` 是存在的。

6. 执行 mongod，带上参数 `--config /path/to/your/mongodb.config`。

以 Windows 用户为例，如果您把下载的的文件解压到 `c:\mongodb\`，创建了 `c:\mongodb\data\`，然后在 `c:\mongodb\bin\mongodb.config` 中添加了 `dbpath=c:\mongodb\data\`。那么您就可以在命令行中输入以下指令来启动 `mongod`：

`c:\mongodb\bin\mongod --config c:\mongodb\bin\mongodb.config`

您可以把这个 `bin` 加入到您的默认路径中省得每次都要输入完整的路径。对于 MacOSX 和 Linux 的用户，方法也是几乎一样的。唯一的区别在于路径不同。

我希望您现在已经安装并可以运行 MongoDB 了。如果您遇到什么错误，注意看输出的错误信息——服务器（server）很善于解释究竟是哪里出了问题。

此时您可以运行 `mongo` 了（没有 *d*），它会启动一个 shell 并连接到运行中的服务器。输入 'db.version()` 以确认所有的东西都正常工作：您应该可以看到您所安装的软件版本。