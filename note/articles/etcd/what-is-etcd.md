# etcd 是什么

etcd 这个名字由两部分组成：etc 和 d ，即 UNIX Linux 操作系统的 "etc" 目录和分布式（ distributed ）首字母的 "d"。我们都知道，"/etc" 目录一般用于存 UNIX/Linux 操作系统的配置信息，因此 etc 和 d 合起来就是一个分布式的 "/etc" 目录。由此可见，etcd 的寓意是为大规模分布式系统存储配置信息。

etcd 官方：

> A distributed, reliable key-value store for the most critical data of a distributed system.
> 
> etcd is a strongly consistent, distributed key-value store that provides a reliable way to store data that needs to be accessed by a distributed system or cluster of machines. It gracefully handles leader elections during network partitions and can tolerate machine failure, even in the leader node.

etcd 是一个高度一致的分布式键值存储，它提供了一种可靠的方式来存储需要由分布式系统或机器集群访问的数据。它可以优雅地处理网络分区期间的领导者选举，即使在领导者节点中也可以容忍机器故障。

从简单应用程序到 Kubernetes 到任何复杂性的应用程序都可以从 etcd 中读写数据。您的应用程序可以读取和写入 etcd 中的数据。一个简单的用例是将数据库连接详细信息或功能标志存储在 etcd 中作为键值对。可以观察这些值，使您的应用在更改时可以重新配置自己。高级用途利用 etcd 的一致性保证来实施数据库领导者选举或跨一组工作人员执行分布式锁定。