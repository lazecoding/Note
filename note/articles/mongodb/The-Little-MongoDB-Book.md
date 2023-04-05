# The Little MongoDB Book

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

## 第一章 - 基础 ##

从了解基本的构成开始，我们开始踏上 MongoDB 探索之路。显然，这是认识 MongoDB 的关键，同时也有助于搞清楚 MongoDB 适用范围的高层次问题。

作为开始，我们需要了解 6 个简单的概念：

1. MongoDB 有着与您熟知的‘数据库’（database，对于 Oracle 就是 ‘schema’）一样的概念。在一个 MongoDB 的实例中您有若干个数据库或者一个也没有，不过这里的每一个数据库都是高层次的容器，用来储存其他的所有数据。

2. 一个数据库可以有若干‘集合’（collection），或者一个也没有。集合和传统概念中的‘表’有着足够多的共同点，所以您大可认为这两者是一样的东西。

3. 集合由若干‘文档’（document）组成，也可以为空。类似的，可以认为这里的文档就是‘行’。

4. 文档又由一个或者更多个‘域’（field）组成，您猜的没错，域就像是‘列’。

5. ‘索引’（index）在 MongoDB 中的意义就如同索引在 RDBMS 中一样。

6. ‘游标’（cursor）和以上 5 个概念不同，它很重要但是却常常被忽略，有鉴于此我认为应该进行专门讨论。关于游标有一点很重要，就是每当向 MongoDB 索要数据时，它总是返回一个游标。基于游标我们可以作诸如计数或是直接跳过之类的操作，而不需要真正去读数据。

小结一下，MongoDB 由‘数据库’组成，数据库由‘集合’组成，集合由‘文档’组成。‘域’组成了文档，集合可以被‘索引’，从而提高了查找和排序的性能。最后，我们从 MongoDB 读取数据的时候是通过‘游标’进行的，除非需要，游标不会真正去作读的操作。

您也许已经觉得奇怪，为什么要用新的术语（表换成集合，行换成文档，列换成域），这不是越弄越复杂了么？这是因为虽然这些概念和那些关系数据库中的相应概念很相似，但是还是存在差异的。关键的差异在于关系数据库是在‘表’这一层次定义‘列’的，而一个面向文档的数据库则是在‘文档’这一层次定义‘域’的。也就是说，集合中的每个文档都可以有独立的域。因此，虽说集合相对于表来说是一个简化了的容器，而文档则包含了比行要多得多的信息。

虽然这些异同很重要，但是如果您现在还没搞清楚也不必担心。以后试着插入几次（数据）就知道我们这里说的是什么了。最后，集合对其中储存的内容并没有严格的要求（它是无模式的（schema-less）），域是与其所在的文档绑定的。当中的优缺点我们会在后续的章节中继续探讨。

开始动手实践吧。如果您还没有运行 Mongo，现在就可以启动 `mongod` 服务器以及 Mongo 的 shell。Mongo 的 shell 运行在 JavaScript 之上，您可以执行一些全局的指令，如 `help` 或者 `exit`。操作对象 `db` 来执行针对当前数据库的操作，例如 `db.help()` 或是 `db.stats()`。操作对象 `db.COLLECTION_NAME` 来执行针对某一给集合的操作，比如说 `db.unicorns.help()` 或是 `db.unicorns.count()`。我们以后将会有许多针对集合的操作。

试试输入 `db.help()`，您会看到一串命令列表，这些命令都可以用来操作 `db` 对象。

顺带说一句，因为我们用的是 JavaScript 的 shell，如果您执行一个命令而忘了加上 `()`，您看到的将是这个命令的实现而并没有执行它。知道这个，您在这么做并看到以 `function(...){` 开头的信息的时候就不会觉得惊讶了。比如说如果您输入 `db.help`（后面没有括弧），你就将看到 `help` 命令的具体实现。

首先我们用全局命令 `use` 来切换数据库。输入 `use learn`。这个数据库是否存在并没有关系，我们创建第一个集合后这个 `learn` 数据库就会生成的。现在您应该已经在一个数据库里面了，可以执行一些诸如 `db.getCollectionNames()` 的数据库命令了。如果您现在就这么做，将会看到一个空的数组（`[]`）。因为集合是无模式的，我们不需要专门去创建它。我们要做的只是把一个文档插入一个新的集合，这个集合就生成了。您可以像下面一样调用 `insert` 命令去插入一个文档：

	db.unicorns.insert({name: 'Aurora', gender: 'f', weight: 450})

以上命令对 `unicorns` 对象执行 `insert` 操作，并传入一个参数。在 MongoDB 内部，数据是以二进制的串行 JSON 格式存储的。这对外部的用户而言，意味着 JSON 的大量应用，就如同上面的参数一样。如果我们现在执行 `db.getCollectionNames()`，将看到两个集合：`unicorns` 以及 `system.indexes`。`system.indexes` 在每个数据库中都会创建，它包含了数据库中的索引信息。

现在您可以对 `unicorns` 对象执行 `find` 命令，得到一个文档列表:

	db.unicorns.find()

请注意，除了您在文档中输入的各个域，还有一个一个叫做 `_id` 的域。每一个文档都必须有一个独立的 `_id` 域。您可以自己创建，也可以让 MongoDB 为您生成这个 ObjectId。大部分情况下您还是会让 MongoDB 为您生成 ID 的。默认情况下，`_id` 域是被索引了的——这就是为什么会有 `system.indexes` 创建出来的原因。看看 `system.indexes` 有什么：

	db.system.indexes.find()

在结果中您会看到该索引的名字，它所绑定的数据库和集合的名字，还有包含这个索引的域。

回到我们前面关于无模式集合的讨论。现在往 `unicorns` 插入一个完全不同的文档，比如说这样：

	db.unicorns.insert({name: 'Leto', gender: 'm', home: 'Arrakeen', worm: false})

再次用 `find` 可以列出所有的文档。学习到后面，我们将继续讨论 MongoDB 无模式的这一有趣的行为，不过我希望您已经开始慢慢了解为什么传统的那些术语不适合用在这里了。

### 掌握选择器（selector） ###

除了刚才介绍过的 6 个概念，MongoDB 还有一个很实用的概念：查询选择器（query selector），在进入更高阶的内容之前，您也需要很好的了解它是什么。
MongoDB 的查询选择器就像 SQL 代码中的 `where` 语句。因此您可以用它在集合中查找，统计，更新或是删除文档。选择器就是一个 JSON 对象，最简单的形式就是 `{}`，用来匹配所有的文档(`null` 也可以）。如果我们需要找到所有雌性的独角兽(unicorn)，我们可以用选择器 `{gender:'f'}` 来匹配。

在深入选择器之前，我们先输入一些数据以备后用。首先用 `db.unicorns.remove()` 删除之前我们在 `unicorns` 集合中输入的所有数据。（由于在这条命令中我们没有指定选择器，于是所有的文档都将被清除）。然后用下面的插入命令准备一些数据（建议拷贝粘帖这些命令）：

	db.unicorns.insert({name: 'Horny', dob: new Date(1992,2,13,7,47), loves: ['carrot','papaya'], weight: 600, gender: 'm', vampires: 63});
	db.unicorns.insert({name: 'Aurora', dob: new Date(1991, 0, 24, 13, 0), loves: ['carrot', 'grape'], weight: 450, gender: 'f', vampires: 43});
	db.unicorns.insert({name: 'Unicrom', dob: new Date(1973, 1, 9, 22, 10), loves: ['energon', 'redbull'], weight: 984, gender: 'm', vampires: 182});
	db.unicorns.insert({name: 'Roooooodles', dob: new Date(1979, 7, 18, 18, 44), loves: ['apple'], weight: 575, gender: 'm', vampires: 99});
	db.unicorns.insert({name: 'Solnara', dob: new Date(1985, 6, 4, 2, 1), loves:['apple', 'carrot', 'chocolate'], weight:550, gender:'f', vampires:80});
	db.unicorns.insert({name:'Ayna', dob: new Date(1998, 2, 7, 8, 30), loves: ['strawberry', 'lemon'], weight: 733, gender: 'f', vampires: 40});
	db.unicorns.insert({name:'Kenny', dob: new Date(1997, 6, 1, 10, 42), loves: ['grape', 'lemon'], weight: 690,  gender: 'm', vampires: 39});
	db.unicorns.insert({name: 'Raleigh', dob: new Date(2005, 4, 3, 0, 57), loves: ['apple', 'sugar'], weight: 421, gender: 'm', vampires: 2});
	db.unicorns.insert({name: 'Leia', dob: new Date(2001, 9, 8, 14, 53), loves: ['apple', 'watermelon'], weight: 601, gender: 'f', vampires: 33});
	db.unicorns.insert({name: 'Pilot', dob: new Date(1997, 2, 1, 5, 3), loves: ['apple', 'watermelon'], weight: 650, gender: 'm', vampires: 54});
	db.unicorns.insert({name: 'Nimue', dob: new Date(1999, 11, 20, 16, 15), loves: ['grape', 'carrot'], weight: 540, gender: 'f'});
	db.unicorns.insert({name: 'Dunx', dob: new Date(1976, 6, 18, 18, 18), loves: ['grape', 'watermelon'], weight: 704, gender: 'm', vampires: 165});

现在我们有足够的数据，我们可以来掌握选择器了。`{field: value}` 用来查找所有 `field` 等于 `value` 的文档。通过 `{field1: value1, field2: value2}` 的形式可以实现 `与` 操作。`$lt`、`$lte`、`$gt`、`$gte` 以及 `$ne` 分别表示小于、小于或等于、大于、大于或等于以及不等于。举个例子，查找所有体重超过 700 磅的雄性独角兽的命令是：

	db.unicorns.find({gender: 'm', weight: {$gt: 700}})
	//或者 (效果并不完全一样，仅用来为了演示不同的方法)
	db.unicorns.find({gender: {$ne: 'f'}, weight: {$gte: 701}})

`$exists` 操作符用于匹配一个域是否存在，比如下面的命令：

	db.unicorns.find({vampires: {$exists: false}})

会返回单个文档（译者：只有这个文档没有vampires域）。如果需要*或*而不是*与*，可以用 `$or` 操作符并作用于需要进行或操作的数组：

	db.unicorns.find({gender: 'f', $or: [{loves: 'apple'}, {loves: 'orange'}, {weight: {$lt: 500}}]})

以上命令返回所有或者喜欢苹果，或者喜欢橙子，或者体重小于 500 磅的雌性独角兽。

您可能已经注意到了，在最后的一个例子中有一个非常棒的特性：`loves` 域是一个数组。在MongoDB 中数组是一级对象(first class object)。这是非常非常有用的功能。一旦用过，没有了它你可能都不知道怎么活下去。更有意思的是基于数组的选择是非常简单的：`{loves: 'watermelon'}` 就会找到 `loves` 中有 `watermelon` 这个值的所有文档。

除了我们目前所介绍过的，还有更多的操作符
可以使用。最灵活的是 `$where`，允许输入 JavaScript 并在服务器端运行。这些都在 MongoDB 网站的 [Advanced Queries](http://www.mongodb.org/display/DOCS/Advanced+Queries#AdvancedQueries) 部分有详细介绍。不过这里介绍的都是基本的命令，了解了这些您就可以开始使用 Mongo 了。而这些命令也往往是您大多数时间会用到的所有命令。

我们已经介绍过怎样在 `find` 命令中使用选择器了。此外选择器还可以用在 `remove` 命令中，我们已经大致提过；还有 `count` 命令中，我们并没有介绍不过您自己可以去试试看；还有 `update` 命令，我们在后面还会提到。

MongoDB 为 `_id` 域生成的 `ObjectId` 也是可以被选择的，就像这样：

	db.unicorns.find({_id: ObjectId("TheObjectId")})

### 本章小结 ###

我们还没有介绍 `update` 命令以及 `find` 的更华丽的功能。不过我们已经让 MongoDB 运行起来，并且执行了 `insert` 和 `remove` 命令（这些命令看过本章的介绍也差不多了）。我们还介绍了 `find` 命令并见识了什么是 MongoDB 的选择器。一个好的开头为后面的深入奠定了坚实的基础。不管你信不信，您事实上已经了解了关于 MongoDB 所需要知道的知识——它本来就是易学易用的。我强烈建议您在继续读下去之前多多在 MongoDB 练习尝试一下。试着插入不同的文档，可以插入到新的集合中，并且熟悉不同的选择器表达式。多用 `find`，`count` 和 `remove`。经过您自己的实践，那些初看很别扭的东西最后都会变得好用起来的。

## 第二章 - 更新 ##

在第一章中我们介绍了 CRUD（Create、Read、Update、Delete）中的三个操作。本章专门用来介绍前面跳过的第四个操作：`update`。`update` 有一些出人意料的行为，这就是为什么我们专门在这章当中讨论它。

### update: replace 与 $set ###

`update` 最简单的执行方式有两个参数：一个是选择器(选择更新的范围)，一个是需要更新的域。如果 Roooooodles 长胖了，我们就需要：

	db.unicorns.update({name: 'Roooooodles'}, {weight: 590})

（如果您用的是自己创建的 `unicorns` 集合，原来的数据都丢失了，那么就用 `remove` 删除掉所有文档，重新插入第一章中的数据）

在实际的代码中，您也许会基于 `_id` 来更新记录，不过既然我不知道 MongoDB 给您分配的 `_id` 是什么，我们就用 `name` 好了。如果我们看看更新过的记录：

	db.unicorns.find({name: 'Roooooodles'})

您就会发现 `update` 第一个出人意料的地方：上面的命令找不到任何文档。这是因为命令中输入的第二个参数是用来**替换（replace）**原来的文档的。换句话说，`update` 先是根据 `name` 找到一个文档，然后用新的文档（也就是第二个参数）去覆盖找到的整个文档。这和 SQL 中的 `update` 的行为是不一样的。在某些情况下，这一行为非常理想，可以用于实现完全动态的更新。然而当您需要的仅是改变某个文档的某个值或者几个域，最好还是用 MongoDB 的 `$set` 修改符（modifier）：

	db.unicorns.update({weight: 590}, {$set: {name: 'Roooooodles', dob: new Date(1979, 7, 18, 18, 44), loves: ['apple'], gender: 'm', vampires: 99}})

这样做就会重设那些丢失的域。新的 `weight` 值不会被覆盖，因为我们没有在命令中指定它。如果现在执行：

	db.unicorns.find({name: 'Roooooodles'})

得到的就是预想的结果。所以在一开始时正确的更新体重的方法应该是：

	db.unicorns.update({name: 'Roooooodles'}, {$set: {weight: 590}})

### 更新修改符 ###

除了 `$set`，还有其他的修改符可以用来非常漂亮地完成一些任务。所有的更新修改符都作用于域上——这样您的文档就不会被整个改写。比如说，`$inc` 可以用来将一个域的值增加一个正的或负的数值。举个例子，如果由于失误，Pilot 多获得了一些吸血的技能(vampire skill。译者：这里的独角兽可以理解为游戏中的某个角色，而这个角色也许可以通过升级打怪的方式提升某些技能，比如说吸血技能。)，我们可以用下面的命令来纠正这个错误：

	db.unicorns.update({name: 'Pilot'}, {$inc: {vampires: -2}})

如果 Aurora 忽然长出了一颗可爱的牙（译者：可以吃糖了），可以用 `$push` 修改符为她的 `loves` 域添加一个新的值：

	db.unicorns.update({name: 'Aurora'}, {$push: {loves: 'sugar'}})

MongoDB 网站上的 [Updating](http://www.mongodb.org/display/DOCS/Updating) 部分可以找到其他更新修改符的更多信息。

### 插新（Upsert） ###

> 译者：[Upsert](http://en.wikipedia.org/wiki/Upsert) 的意思是 update if present; insert if not。是 update 和 insert 合体的产物。没有找到一个合适的词作为翻译，于是我斗胆发明了“插新”这个词，取或插入或更新之意。如有更好的办法，还请指点。

`update` 的一个比较讨喜的出人意料之处就是它完全支持插新(`upsert`)。当目标文档存在的时候，插新操作会更新该文档，否则就插入该新文档。插新在某些情况下是很方便的，当您碰到这种情况的时候就会知道了。为了打开插新的功能，我们在使用 `update` 时把第三个参数设为 `true`。

一个很常见的例子就是网站的点击计数器。如果需要得到实时的点击累计数值，我们需要知道这个页面的点击记录是否存在，然后决定是要更新点击数还是插入。如果忽略第三个参数（或者是设置为 false），下面的命令什么也不做：

	db.hits.update({page: 'unicorns'}, {$inc: {hits: 1}});
	db.hits.find();

如果打开了插新，结果就不一样了：

	db.hits.update({page: 'unicorns'}, {$inc: {hits: 1}}, true);
	db.hits.find();

这一次，因为没有文档有域 `page` 的值为 `unicorns`，就插入一个新的文档。再执行一次上面的命令，创建好的文档就会被更新，而 `hits` 的值就会增加为2。

	db.hits.update({page: 'unicorns'}, {$inc: {hits: 1}}, true);
	db.hits.find();

### 多重更新 ###

`update` 最后的一个惊喜是，它会默认地只更新一个文档。到目前为止就我们所见到的例子来看，这样做是合理的。不过如果执行下面的命令：

	db.unicorns.update({}, {$set: {vaccinated: true }});
	db.unicorns.find({vaccinated: true});

您想要做的应该是找出所有已经注射过疫苗（vaccinated）的独角兽，但为了达到这样的目的，需要把第四个参数设为 true：

	db.unicorns.update({}, {$set: {vaccinated: true }}, false, true);
	db.unicorns.find({vaccinated: true});

### 本章小结 ###

本章完成了对基础的集合操作，CRUD 的介绍。我们细致的了解了 `update` 以及它的三个有意思的行为：第一，和 SQL 的 update 不同，MongoDB 的 `update` 会替换实际的文档。因此 `$set` 修改符就显得很有用了。第二，`update` 支持直观的 `插新`，这在和 `$inc` 修改符结合起来的时候特别有用。最后，`update` 的默认行为是只更新第一个找到的文档。

一定要记住的是，我们是在 MongoDB 的 shell 中介绍它的。实际应用时您所采用的驱动或是库有可能会修改这些默认的行为，或是提供一个不同的编程接口（API）。例如：Ruby 的驱动把最后的两个参数合并为一个哈希表：`{:upsert => false, :multi => false}`。类似地，PHP的驱动把最后的两个参数合并到了一个数组中：`array('upsert' => false, 'multiple' => false)`。