# Pod 与 Service 的 DNS

- 目录
  - [resolv.conf](#resolv.conf)
  - [Service 的 Namespaces](#Service-的-Namespaces)
  - [DNS 配置和策略](#DNS-配置和策略)
    - [DNS Config](#DNS-Config)
    - [DNS Policy](#DNS-Policy)

DNS 服务就是将输入的域名转化为 IP 地址，Kubernetes 为服务和 Pods 创建 DNS 记录，你可以使用一致的 DNS 名称而非 IP 地址来访问服务。

Kubernetes DNS 在集群上调度 DNS Pod 和服务，并配置 kubelet 以告知各个容器 使用 DNS 服务的 IP 来解析 DNS 名称。

集群中定义的每个 Service （包括 DNS 服务器自身）都被赋予一个 DNS 名称。 默认情况下，客户端 Pod 的 DNS 搜索列表会包含 Pod 自身的名字空间和集群 的默认域。

### resolv.conf

`/etc/resolv.conf` 用来指定 DNS 客户端方面的配置：

```config
nameserver 8.8.8.8
search keeper.org
options edns0
```

- `nameserver`：定义 DNS 服务器地址，可以最多指定 MAXNS 个 nameserver，目前 MAXNS 是 3。
- `search`：定义域名的搜索列表。
- `options`：定义某些内部解析器变量选项。

当我们再浏览器中输入 http://x99 的时候，会到 8.8.8.8 的 DNS 服务器去查询 x99.keeper.org 的 IP。

更多内容查看 [resolv.conf](https://www.man7.org/linux/man-pages/man5/resolv.conf.5.html) 。

### Service 的 Namespaces

DNS 查询可能因为执行查询的 Pod 所在的 Namespaces 而返回不同的结果。不指定 Namespaces 的 DNS 查询会被限制在 Pod 所在的 Namespaces 内，要访问其他 Namespaces 中的服务，需要在 DNS 查询中给出 Namespaces。

例如，假定 Namespaces test 中存在一个 Pod，Namespaces prod 中存在一个服务 data。

Pod 查询 data 时没有返回结果，因为使用的是 Pod 的 Namespaces test；Pod 查询 data.prod 时则会返回预期的结果，因为查询中指定了 Namespaces。

DNS 查询可以使用 Pod 中的 `/etc/resolv.conf` 展开，kubelet 会为每个 Pod 生成此文件。例如，对 data 的查询可能被展开为 `data.test.svc.cluster.local`。

```config
nameserver 10.32.0.10
search <namespace>.svc.cluster.local svc.cluster.local cluster.local
options ndots:5
```

概括起来，Namespaces test 中的 Pod 可以成功地解析 `data.prod` 或者 `data.prod.svc.cluster.local`。

### DNS 配置和策略

在 Kubernetes 的 yaml 中，和 DNS 相关的选项有两个：DNSConfig 和 DNSPolicy，它们分别是 DNS 配置和策略。

#### DNS Config

Pod 的 dnsConfig 选项可让用户对 Pod 的 DNS 设置进行更多控制。dnsConfig 字段是可选的，它可以与任何 dnsPolicy 设置一起使用。但是，当 Pod 的 dnsPolicy 设置为 "None" 时，必须指定 dnsConfig 字段。

用户可以在 dnsConfig 字段中指定以下属性：

- `nameservers`：将用作于 Pod 的 DNS 服务器的 IP 地址列表，最多可以指定 3 个 IP 地址。当 Pod 的 dnsPolicy 设置为 "None" 时， 列表必须至少包含一个 IP 地址，否则此属性是可选的。所列出的服务器将合并到从指定的 DNS 策略生成的基本名称服务器，并删除重复的地址。
- `searches`：用于在 Pod 中查找主机名的 DNS 搜索域的列表，此属性是可选的。指定此属性时，所提供的列表将合并到根据所选 DNS 策略生成的基本搜索域名中，重复的域名将被删除。Kubernetes 最多允许 6 个搜索域。
- `options`：可选的对象列表，其中每个对象可能具有 name 属性（必需）和 value 属性（可选）。此属性中的内容将合并到从指定的 DNS 策略生成的选项，重复的条目将被删除。

以下是具有自定义 DNS 设置的 Pod 示例：

```yaml
apiVersion: v1
kind: Pod
metadata:
  namespace: default
  name: dns-example
spec:
  containers:
    - name: test
      image: nginx
  dnsPolicy: "None"
  dnsConfig:
    nameservers:
      - 1.2.3.4
    searches:
      - ns1.svc.cluster-domain.example
      - my.dns.search.suffix
    options:
      - name: ndots
        value: "2"
      - name: edns0
```

创建上面的 Pod 后，容器 test 会在其 /etc/resolv.conf 文件中获取以下内容：

```config
nameserver 1.2.3.4
search ns1.svc.cluster-domain.example my.dns.search.suffix
options ndots:2 edns0
```

#### DNS Policy

DNS 策略可以逐个 Pod 来设定。目前 Kubernetes 支持以下特定 Pod 的 DNS 策略，这些策略可以在 Pod 规约中的 dnsPolicy 字段设置：

- "Default": Pod 从运行所在的节点继承名称解析配置。
- "ClusterFirst": 与配置的集群域后缀不匹配的任何 DNS 查询（例如 "www.kubernetes.io"） 都将转发到从节点继承的上游名称服务器。集群管理员可能配置了额外的存根域和上游 DNS 服务器。
- "ClusterFirstWithHostNet"：对于以 hostNetwork 方式运行的 Pod，应显式设置其 DNS 策略 "ClusterFirstWithHostNet"。
- "None": 此设置允许 Pod 忽略 Kubernetes 环境中的 DNS 设置。Pod 会使用其 dnsConfig 字段 所提供的 DNS 设置。

> 注意："Default" 不是默认的 DNS 策略。如果未明确指定 dnsPolicy，则使用 "ClusterFirst"。

下面的示例显示了一个 Pod，其 DNS 策略设置为 "ClusterFirstWithHostNet"，因为它已将 hostNetwork 设置为 true。

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: busybox
  namespace: default
spec:
  containers:
  - image: busybox:1.28
    command:
      - sleep
      - "3600"
    imagePullPolicy: IfNotPresent
    name: busybox
  restartPolicy: Always
  hostNetwork: true
  dnsPolicy: ClusterFirstWithHostNet
```