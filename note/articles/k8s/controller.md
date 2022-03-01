# 控制器

- 目录
  - [ReplicationController 和 ReplicaSet](#ReplicationController-和-ReplicaSet)
  - [Deployment](#Deployment)
  - [StatefulSets](#StatefulSets)
    - [Pod 标识](#Pod-标识)
      - [有序索引](#有序索引)
      - [稳定的网络 ID](#稳定的网络-ID)
      - [稳定的存储](#稳定的存储)
      - [Pod 名称标签](#Pod-名称标签)
  - [DaemonSet](#DaemonSet)
  - [Job](#Job)
  - [CronJob](#CronJob)
    - [CronJob Spec](#CronJob-Spec)
    - [Cron 时间表语法](#Cron-时间表语法)

Kubernetes 中内建了很多 Controller（控制器），这些相当于一个状态机，用来控制 Pod 的具体状态和行为。一个控制器至少追踪一种类型的 Kubernetes 资源。这些对象有一个代表期望状态的 spec 字段，该资源的控制器负责确保其当前状态接近期望状态。

### ReplicationController 和 ReplicaSet

副本控制器（Replication Controller，RC）是 Kubernetes 集群中最早的保证 Pod 高可用的 API 对象。通过监控运行中的 Pod 来保证集群中运行指定数目的 Pod 副本。指定的数目可以是多个也可以是 1 个；少于指定数目，RC 就会启动运行新的 Pod 副本；多于指定数目，RC 就会杀死多余的 Pod 副本。即使在指定数目为 1 的情况下，通过 RC 运行 Pod 也比直接运行 Pod 更明智，因为 RC 也可以发挥它高可用的能力，保证永远有 1 个 Pod 在运行。RC 是 Kubernetes 较早期的技术概念，只适用于长期伺服型的业务类型，比如控制小机器人提供高可用的 Web 服务。

副本集（Replica Set，RS）是新一代 RC，提供同样的高可用能力，区别主要在于 RS 后来居上，能支持更多种类的匹配模式。ReplicaSet 可确保指定数量的 Pod replicas 在任何设定的时间运行。然而，Deployment 是一个更高层次的概念，它管理 ReplicaSet，并提供对 Pod 的声明性更新以及许多其他的功能。因此，建议使用 Deployment 而不是直接使用 ReplicaSet，除非需要自定义更新编排或根本不需要更新。

ReplicaSet 的目的是维护一组在任何时候都处于运行状态的 Pod 副本的稳定集合。因此，它通常用来保证给定数量的、完全相同的 Pod 的可用性。

RepicaSet 是通过一组字段来定义的，包括一个用来识别可获得的 Pod 的集合的选择算符、一个用来标明应该维护的副本个数的数值、一个用来指定应该创建新 Pod 以满足副本个数条件时要使用的 Pod 模板等等。每个 ReplicaSet 都通过根据需要创建和删除 Pod 以使得副本个数达到期望值， 进而实现其存在价值。当 ReplicaSet 需要创建新的 Pod 时，会使用所提供的 Pod 模板。ReplicaSet 通过 Pod 上的 `metadata.ownerReferences` 字段连接到附属 Pod，该字段给出当前对象的属主资源。ReplicaSet 所获得的 Pod 都在其 ownerReferences 字段中包含了属主 ReplicaSet 的标识信息。正是通过这一连接，ReplicaSet 知道它所维护的 Pod 集合的状态，并据此计划其操作行为。

ReplicaSet 示例：

```yaml
apiVersion: apps/v1
kind: ReplicaSet
metadata:
  name: frontend
  labels:
    app: guestbook
    tier: frontend
spec:
  # modify replicas according to your case
  replicas: 3
  selector:
    matchLabels:
      tier: frontend
  template:
    metadata:
      labels:
        tier: frontend
    spec:
      containers:
      - name: php-redis
        image: gcr.io/google_samples/gb-frontend:v3
```

ReplicaSet 的信息被设置在 metadata 的 ownerReferences 字段中：

```yaml
apiVersion: v1
kind: Pod
metadata:
  creationTimestamp: "2020-02-12T07:06:16Z"
  generateName: frontend-
  labels:
    tier: frontend
  name: frontend-b2zdv
  namespace: default
  ownerReferences:
  - apiVersion: apps/v1
    blockOwnerDeletion: true
    controller: true
    kind: ReplicaSet
    name: frontend
    uid: f391f6db-bb9b-4c09-ae74-6a1f77f3d5cf
...
```

更多内容查看 [ReplicaSet](https://kubernetes.io/zh/docs/concepts/workloads/controllers/replicaset/) 。

### Deployment

部署（Deployment）表示用户对 Kubernetes 集群的一次更新操作。部署是一个比 RS 应用模式更广的 API 对象，可以是创建一个新的服务，更新一个新的服务，也可以是滚动升级一个服务。滚动升级一个服务，实际是创建一个新的 RS，然后逐渐将新 RS 中副本数增加到理想状态，将旧 RS 中的副本数减小到 0 的复合操作；这样一个复合操作用一个 RS 是不太好描述的，所以用一个更通用的 Deployment 来描述。以 Kubernetes 的发展方向，未来对所有长期伺服型的的业务的管理，都会通过 Deployment 来管理。

典型的应用场景包括：

- 创建 Deployment 以将 ReplicaSet 上线，ReplicaSet 在后台创建 Pods。检查 ReplicaSet 的上线状态，查看其是否成功。
- 通过更新 Deployment 的 PodTemplateSpec，声明 Pod 的新状态 。新的 ReplicaSet 会被创建，Deployment 以受控速率将 Pod 从旧 ReplicaSet 迁移到新 ReplicaSet。每个新的 ReplicaSet 都会更新 Deployment 的修订版本。
- 如果 Deployment 的当前状态不稳定，回滚到较早的 Deployment 版本。每次回滚都会更新 Deployment 的修订版本。
- 扩大 Deployment 规模以承担更多负载。
- 暂停 Deployment 以应用对 PodTemplateSpec 所作的多项修改，然后恢复其执行以启动新的上线版本。
- 使用 Deployment 状态 来判定上线过程是否出现停滞。
- 清理较旧的不再需要的 ReplicaSet。

下面是 Deployment 示例。其中创建了一个 ReplicaSet，负责启动三个 nginx Pods：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  labels:
    app: nginx
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
        - name: nginx
          image: nginx:1.14.2
          ports:
            - containerPort: 80
```

在该例中：

- 创建名为 nginx-deployment（由 .metadata.name 字段标明）的 Deployment。
- 该 Deployment 创建三个（由 replicas 字段标明）Pod 副本。
- selector 字段定义 Deployment 如何查找要管理的 Pods。在这里，你选择在 Pod 模板中定义的标签（app: nginx）。不过，更复杂的选择规则是也可能的，只要 Pod 模板本身满足所给规则即可。
- template 字段包含以下子字段：
  - Pod 被使用 labels 字段打上 app: nginx 标签。
  - Pod 模板规约（即 .template.spec 字段）指示 Pods 运行一个 nginx 容器，该容器运行版本为 1.14.2 的 nginx Docker Hub镜像。
  - 创建一个容器并使用 name 字段将其命名为 nginx

扩容：

```C
kubectl scale deployment nginx-deployment --replicas 10
```

如果集群支持 horizontal pod autoscaling 的话，还可以为 Deployment 设置自动扩展：

```C
kubectl autoscale deployment nginx-deployment --min=10 --max=15 --cpu-percent=80
```

更新镜像也比较简单：

```C
kubectl set image deployment/nginx-deployment nginx=nginx:1.9.1
```

回滚：

```C
kubectl rollout undo deployment/nginx-deployment
```

更多内容查看 [Deployment](https://kubernetes.io/zh/docs/concepts/workloads/controllers/deployment/) 。

### StatefulSets

StatefulSet 是用来管理有状态应用的工作负载 API 对象，它可以管理某 Pod 集合的部署和扩缩，并为这些 Pod 提供持久存储和持久标识符。

和 Deployment 类似，StatefulSet 管理基于相同容器规约的一组 Pod。但和 Deployment 不同的是，StatefulSet 为它们的每个 Pod 维护了一个有粘性的 ID。这些 Pod 是基于相同的规约来创建的， 但是不能相互替换：无论怎么调度，每个 Pod 都有一个永久不变的 ID。

StatefulSets 对于需要满足以下一个或多个需求的应用程序很有价值：

- 稳定的、唯一的网络标识符。
- 稳定的、持久的存储。
- 有序的、优雅的部署和缩放。
- 有序的、自动的滚动更新。

在上面描述中，"稳定的" 意味着 Pod 调度或重调度的整个过程是有持久性的。 如果应用程序不需要任何稳定的标识符或有序的部署、删除或伸缩，则应该使用 由一组无状态的副本控制器提供的工作负载来部署应用程序，比如 Deployment 或者 ReplicaSet 可能更适用于你的无状态应用部署需要。

下面的示例演示了 StatefulSet 的组件。

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx
  labels:
    app: nginx
spec:
  ports:
  - port: 80
    name: web
  clusterIP: None
  selector:
    app: nginx
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: web
spec:
  selector:
    matchLabels:
      app: nginx # has to match .spec.template.metadata.labels
  serviceName: "nginx"
  replicas: 3 # by default is 1
  template:
    metadata:
      labels:
        app: nginx # has to match .spec.selector.matchLabels
    spec:
      terminationGracePeriodSeconds: 10
      containers:
      - name: nginx
        image: k8s.gcr.io/nginx-slim:0.8
        ports:
        - containerPort: 80
          name: web
        volumeMounts:
        - name: www
          mountPath: /usr/share/nginx/html
  volumeClaimTemplates:
  - metadata:
      name: www
    spec:
      accessModes: [ "ReadWriteOnce" ]
      storageClassName: "my-storage-class"
      resources:
        requests:
          storage: 1Gi
```

上述例子中：

- 名为 nginx 的 Headless Service 用来控制网络域名。
- 名为 web 的 StatefulSet 有一个 Spec，它表明将在独立的 3 个 Pod 副本中启动 nginx 容器。
- volumeClaimTemplates 将通过 PersistentVolumes 驱动提供的 PersistentVolumes 来提供稳定的存储。

#### Pod 标识

StatefulSet Pod 具有唯一的标识，该标识包括顺序标识、稳定的网络标识和稳定的存储。该标识和 Pod 是绑定的，不管它被调度在哪个节点上。

##### 有序索引

对于具有 N 个副本的 StatefulSet，StatefulSet 中的每个 Pod 将被分配一个整数序号，从 0 到 N-1，该序号在 StatefulSet 上是唯一的。

##### 稳定的网络 ID

StatefulSet 中的每个 Pod 根据 StatefulSet 的名称和 Pod 的序号派生出它的主机名，组合主机名的格式为 `$(StatefulSet 名称)-$(序号)`。上例将会创建三个名称分别为 web-0、web-1、web-2 的 Pod。 StatefulSet 可以使用 无头服务 控制它的 Pod 的网络域。管理域的这个服务的格式为：`$(服务名称).$(命名空间).svc.cluster.local`，其中 `cluster.local` 是集群域。一旦每个 Pod 创建成功，就会得到一个匹配的 DNS 子域，格式为：`$(pod 名称).$(所属服务的 DNS 域名)`，其中所属服务由 StatefulSet 的 serviceName 域来设定。

##### 稳定的存储

对于 StatefulSet 中定义的每个 VolumeClaimTemplate，每个 Pod 接收到一个 PersistentVolumeClaim。在上面的 nginx 示例中，每个 Pod 将会得到基于 StorageClass my-storage-class 提供的 1 Gib 的 PersistentVolume。如果没有声明 StorageClass，就会使用默认的 StorageClass。当一个 Pod 被调度（重新调度）到节点上时，它的 volumeMounts 会挂载与其 PersistentVolumeClaims 相关联的 PersistentVolume。

注意，当 Pod 或者 StatefulSet 被删除时，与 PersistentVolumeClaims 相关联的 PersistentVolume 并不会被删除，要删除它必须通过手动方式来完成。

##### Pod 名称标签

当 StatefulSet 控制器（Controller）创建 Pod 时，它会添加一个标签 `statefulset.kubernetes.io/pod-name`，该标签值设置为 Pod 名称。 这个标签允许你给 StatefulSet 中的特定 Pod 绑定一个 Service。

更多内容查看 [StatefulSets](https://kubernetes.io/zh/docs/concepts/workloads/controllers/statefulset/) 。


### DaemonSet

DaemonSet 确保全部（或者某些）节点上运行一个 Pod 的副本。当有节点加入集群时，也会为他们新增一个 Pod。当有节点从集群移除时这些 Pod 也会被回收。删除 DaemonSet 将会删除它创建的所有 Pod。

DaemonSet 的一些典型用法：

- 在每个节点上运行集群守护进程。
- 在每个节点上运行日志收集守护进程。
- 在每个节点上运行监控守护进程。

一种简单的用法是为每种类型的守护进程在所有的节点上都启动一个 DaemonSet。一个稍微复杂的用法是为同一种守护进程部署多个 DaemonSet，每个具有不同的标志，并且对不同硬件类型具有不同的内存、CPU 要求。

更多内容查看 [DaemonSet](https://kubernetes.io/zh/docs/concepts/workloads/controllers/daemonset/) 。

### Job

Job 负责批处理任务，即仅执行一次的任务，它保证批处理任务的一个或多个 Pod 成功结束。

Job 会创建一个或者多个 Pods，并将继续重试 Pods 的执行，直到指定数量的 Pods 成功终止。随着 Pods 成功结束，Job 跟踪记录成功完成的 Pods 个数。当数量达到指定的成功个数阈值时，任务（即 Job）结束。删除 Job 的操作会清除所创建的全部 Pods。挂起 Job 的操作会删除 Job 的所有活跃 Pod，直到 Job 被再次恢复执行。

一种简单的使用场景下，你会创建一个 Job 对象以便以一种可靠的方式运行某 Pod 直到完成。当第一个 Pod 失败或者被删除（比如因为节点硬件失效或者重启）时，Job 对象会启动一个新的 Pod。

你也可以使用 Job 以并行的方式运行多个 Pod。

更多内容查看 [Job](https://kubernetes.io/zh/docs/concepts/workloads/controllers/job/) 。

### CronJob

Cron Job 管理基于时间的 Job，即：

- 在给定时间点只运行一次。
- 周期性地在给定时间点运行。

一个 CronJob 对象类似于 crontab（cron table）文件中的一行，它根据指定的预定计划周期性地运行一个 Job。

#### CronJob Spec

- `.spec.schedule`：调度，必需字段，指定任务运行周期，格式同 Cron。
- `.spec.jobTemplate`：Job 模板，必需字段，指定需要运行的任务，格式同 Job。
- `.spec.startingDeadlineSeconds`：启动 Job 的期限（秒级别），该字段是可选的。如果因为任何原因而错过了被调度的时间，那么错过执行时间的 Job 将被认为是失败的。如果没有指定，则没有期限。
- `.spec.concurrencyPolicy`：并发策略，该字段也是可选的。它指定了如何处理被 Cron Job 创建的 Job 的并发执行。只允许指定下面策略中的一种：
  - `Allow（默认）`：允许并发运行 Job。
  - `Forbid`：禁止并发运行，如果前一个还没有完成，则直接跳过下一个。
  - `Replace`：取消当前正在运行的 Job，用一个新的来替换。
- `.spec.suspend`：挂起，该字段也是可选的。如果设置为 true，后续所有执行都会被挂起。它对已经开始执行的 Job 不起作用。默认值为 false。
- `.spec.successfulJobsHistoryLimit` 和 `.spec.failedJobsHistoryLimit` ：历史限制，是可选的字段。它们指定了可以保留多少完成和失败的 Job。默认情况下，它们分别设置为 3 和 1。设置限制的值为 0，相关类型的 Job 完成后将不会被保留。

#### Cron 时间表语法

```C
# ┌───────────── 分钟 (0 - 59)
# │ ┌───────────── 小时 (0 - 23)
# │ │ ┌───────────── 月的某天 (1 - 31)
# │ │ │ ┌───────────── 月份 (1 - 12)
# │ │ │ │ ┌───────────── 周的某天 (0 - 6)（周日到周一；在某些系统上，7 也是星期日）
# │ │ │ │ │
# │ │ │ │ │
# │ │ │ │ │
# * * * * *
```

| 输入 | 描述 | 语法 |
| --- | --- | --- |
| @yearly (or @annually) | 每年 1 月 1 日的午夜运行一次 | 0 0 1 1 * |
| @monthly | 每月第一天的午夜运行一次 | 0 0 1 * * |
| @weekly | 每周的周日午夜运行一次 | 0 0 * * 0 |
| @daily (or @midnight) | 每天午夜运行一次 | 0 0 * * * |
| @hourly | 每小时的开始一次 | 0 * * * * |

例如，下面这行指出必须在每个星期五的午夜以及每个月 13 号的午夜开始任务：

```C
0 0 13 * 5
```

更多内容查看 [CronJob](https://kubernetes.io/zh/docs/concepts/workloads/controllers/cron-jobs/) 。