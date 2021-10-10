# Cluster 模块

- 目录
    - [集群状态](#集群状态)
    - [ClusterService](#ClusterService)
        - [MasterService](#MasterService)
        - [ClusterApplierService](#ClusterApplierService)
        - [线程池](#线程池)
    - [任务的提交和执行](#任务的提交和执行)
        - [提交任务](#提交任务)
        - [执行任务](#执行任务)
          
Cluster 模块封装了在集群层面要执行的任务 ，主要功能如下：

- 管理集群状态，将新生成的集群状态发布到集群所有节点。
- 调用 allocation 模块执行分片分配，决策哪些分片应该分配到哪个节点。
- 在集群各节点中直接迁移分片，保持数据平衡。

### 集群状态

集群状态在 ElasticSearch 中封装为 ClusterState 类。可以通过 `cluster/state API` 来获取集群状态。

```C
curl -X GET "localhost: 9200/_cluster/state"
```

响应内容：

```json
{
    "cluster_name" : "elasticsearch",
    "compressed_size_in_bytes" : 1383, //压缩后的字节数
    "version" : 5, //当前集群状态的版本号
    "state_uuid" : "MMXpwaedThCVDIkzn9vpgA",
    "master_node" : " fc6s0S0hRi2yJvMo54qt_g",
    "blocks" : { }, //阻塞信息
    "nodes" : {
        " fc6s0S0hRi2yJvMo54qt_g" : {
            //节点名称、监听地址和端口等信息
        }
    }
    "metadata" : {//元数据
        "cluster_uuid" : "olrqNUxhTC20VVG8KyXJ_w",
        "templates" : {
            //全部模板的具体内容
        }，
        "indices" : {//索引列表
            "website" : {
                "state" : "open",
                "settings" : {
                    //setting的具体内容
                },
                "mappings": {
                    //mapping的具体内容
                }
                "aliases" : [ ],//索引别名
                "primary_ terms" : {
                    //某个分片被选为主分片的次数，用于区分新旧主分片(具体请参考数据模型一章)
                    "0" : 4,
                    "1" : 5
                }
                "in_sync_allocations" : {
                    //同步分片列表，代表某个分片中拥有最新数据的分片列表
                    "1":[
                        "jalbPWjJST2bDPCU008ScQ" //这个值是allocation_id
                    ],
                    "0":[
                        "1EjTXE1CSZ-C1DYlEFRXtw"
                    ]
                }
            }
        },
        "repositories" : {
            //为存储快照而创建的仓库列表
        }，
        "index-graveyard" : {
            //索引墓碑。记录已删除的索引，并保存一段时间。索引删除是主节点通过下发
            //集群状态来执行的
            //各节点处理集群状态是异步的过程。例如，索引分片分布在5个节点上，删除
            //索引期间，某个节点是“down”掉的，没有执行删除逻辑
            //当这个节点恢复的时候，其存储的已删除的索引会被当作孤立资源加入集群,
            //索引死而复活。墓碑的作用就是防止发生这种情况
            "tombstones" : [
                //已删除的索引列表
            ]
        }
    },
    "routing_table" : { //内容路由表。存储某个分片位于哪个节点的信息。通过分片
        //找到节点
        "indices" : { //全部索引列表
            "website" : {//索引名称
                "shards" : {//该索引的全部分片列表 .
                    "1" : [//分片 1
                        {
                            "state" : "STARTED",    //分片可能的状态: UNASSIGNED、INITIALIZING、
                                                    //STARTED、RELOCATING
                             "primary" : true, //是否是主分片
                             "node" : "fc6s0S0hRi2yJvMo54qt_g", //所在分片
                             "relocating_node" : null, //正在“relocation”到哪个节点
                             "shard" : 1, // 分片1
                             "index" : "website", // 索引名
                             "allocation_ id" : {
                                "id" : "jalbPWj JST2bDPCUO08ScQ" //分片唯一的allocation_id配合in_sync_allocations使用
                             }
                         }
                     ]
                 }
             }
         }
     },
     "routing nodes" : {//存储某个节点存储了哪些分片的信息。通过节点找到分片
        "unassigned" : [//未分配的分片列表
                {//某个分片的信息
                    "state" : "UNASSIGNED",
                    "primary" : true,
                    "node" : null,
                    "relocating_ node" : null,
                    "shard" : 0,
                    "index" : "website",
                    " recovery_ source" : {
                    "type" : "EXISTING_ STORE"
                },
                "unassigned_ info" : {//未分配的具体信息
                    "reason" : "CLUSTER RECOVERED",
                    "at" : "2018-05-27T08:17:56.381Z",
                    "delayed" : false,
                    "allocation status" : "no_ valid_ shard copy"
                }
            }
        ],
        "nodes" : {//节点列表
        "fc6s0S0hRi2yJvMo54qt_g" : [//某个节点上的分片列表        
            {      
                "state" : "STARTED", //分片信息，同上
                "primary" : true,
                "node" : " fc6s0S0hRi2yJvMo54qt_g",
                "relocating_ node" : null,
                "shard" : 1,
                "index" : "website",
                "allocation_id" : {
                    "id" : "jalbPWjJST2bDPCU008ScQ"
                },
                "snapshot_deletions" : {//请求删除快照的信息
                    "snapshot_deletions" :[ ]
                },
                "snapshots" : {//请求创建快照的信息
                    "snapshots" : [ ]
                },
                "restore" : {//请求恢复快照的信息
                    "snapshots" : [ ]  
                }
            }
        }
    }
}
```

### ClusterService

ClusterService 是对集群管理的封装，它内部注入了 MasterService 和 ClusterApplierService 完成集群管理。

ClusterService 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/ClusterService类图.png" width="600px">
</div>

在 ElasticSearch 启动阶段，`Node#start` 中执行了 `clusterService.start();`，其实是 clusterService 调用了 clusterApplierService 和 masterService 的 start 方法。

```java
@Override
protected synchronized void doStart() {
    // 处理启动过程中注册的任务
    clusterApplierService.start();
    // 处理集群状态更新任务
    masterService.start();
}
```

#### MasterService

MasterService 类负责集群任务管理、执行等工作，它内部维护一个线程池执行任务，并对外提供了提交任务的接口。

MasterService 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/MasterService类图.png" width="600px">
</div>

方法：

- numberOfPendingTasks：获取待执行的任务数量。
- pendingTasks：获取待执行的任务数量。
- submitStateUpdateTask：提交集群状态更新任务。

成员：

- Batcher：Batcher 内部类负责管理和执行任务，继承自 TaskBatcher 类并继承了父类的 submitTasks 方法用于提交任务并交给线程池执行（submitStateUpdateTask 就是调用的该方法）。
- PrioritizedEsThreadPoolExecutor：执行任务的线程池，本质使用优先级队列作为工作队列的线程池执行器。

#### ClusterApplierService

ClusterApplierService 类负责管理集群状态，以及通知各个 Applier 应用集群状态。
主节点和从节点都会应用集群状态，如果某个模块需要处理集群状态，则调用 addStateApplier 方法添加一个处理器；如果想监听集群状态的变化，则通过 addListener 添加一个监听器。

ClusterApplierService 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/ClusterApplierService类图.png" width="600px">
</div>

方法：

- addListener：添加一个集群状态监听器。
- removeListener：删除一个集群状态监听器。
- addStateApplier：添加一个集群状态处理器。
- removeApplier：删除一个集群状态处理器。
- state：获取集群状态。
- onNewClusterState：收到新的集群状态。

成员：

- UpdateTask：UpdateTask 内部类继承自 SourcePrioritizedRunnable，用于处理集群状态更新任务，与 `MasterService.Batcher` 类似。

#### 线程池

MasterService 和 ClusterApplierService 中使用的线程池都是 PrioritizedEsThreadPoolExecutor，corePoolSize、maximumPoolSize 都是 1, keepAliveTime 为 0，
继续跟踪到 PrioritizedEsThreadPoolExecutor 的构造函数，可以看到线程池使用带优先级的阻塞队列 PriorityBlockingQueue。

```java
// org/elasticsearch/common/util/concurrent/EsExecutors.java#newSinglePrioritizing
public static PrioritizedEsThreadPoolExecutor newSinglePrioritizing(String name, ThreadFactory threadFactory,
                                                                    ThreadContext contextHolder, ScheduledExecutorService timer) {
    return new PrioritizedEsThreadPoolExecutor(name, 1, 1, 0L, TimeUnit.MILLISECONDS, threadFactory, contextHolder, timer);
}

// org/elasticsearch/common/util/concurrent/PrioritizedEsThreadPoolExecutor.java#PrioritizedEsThreadPoolExecutor
public PrioritizedEsThreadPoolExecutor(String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                ThreadFactory threadFactory, ThreadContext contextHolder, ScheduledExecutorService timer) {
    super(name, corePoolSize, maximumPoolSize, keepAliveTime, unit, new PriorityBlockingQueue<>(), threadFactory, contextHolder);
    this.timer = timer;
}
```

### 任务的提交和执行

当集群状态发送变化，就需要提交和执行集群任务。

#### 提交任务

前面提到 submitStateUpdateTask 方法用于提交任务，最终它会调用 submitStateUpdateTasks 方法批量提交任务。

- MasterService#submitStateUpdateTasks

```java
// org/elasticsearch/cluster/service/MasterService.java#submitStateUpdateTasks
public <T> void submitStateUpdateTasks(final String source,
                                       final Map<T, ClusterStateTaskListener> tasks, final ClusterStateTaskConfig config,
                                       final ClusterStateTaskExecutor<T> executor) {
    if (!lifecycle.started()) {
        return;
    }
    final ThreadContext threadContext = threadPool.getThreadContext();
    final Supplier<ThreadContext.StoredContext> supplier = threadContext.newRestorableContext(true);
    try (ThreadContext.StoredContext ignore = threadContext.stashContext()) {
        threadContext.markAsSystemContext();
        // 对 XXXTask 做安全校验并转变为 UpdateTask，UpdateTask 是个包含优先级的任务，是 PrioritizedRunnable 的子类
        List<Batcher.UpdateTask> safeTasks = tasks.entrySet().stream()
            .map(e -> taskBatcher.new UpdateTask(config.priority(), source, e.getKey(), safe(e.getValue(), supplier), executor))
            .collect(Collectors.toList());
        // 批量提交任务
        taskBatcher.submitTasks(safeTasks, config.timeout());
    } catch (EsRejectedExecutionException e) {
        // ignore cases where we are shutting down..., there is really nothing interesting
        // to be done here...
        if (!lifecycle.stoppedOrClosed()) {
            throw e;
        }
    }
}
```

这里首先对 XXXTask 做安全校验并转变为 UpdateTask，UpdateTask 是个包含优先级的任务，是 PrioritizedRunnable 的子类，接着调用 `TaskBatcher#submitTasks` 提交任务。

- TaskBatcher#submitTasks

```java
// org/elasticsearch/cluster/service/TaskBatcher.java#submitTasks
public void submitTasks(List<? extends BatchedTask> tasks, @Nullable TimeValue timeout) throws EsRejectedExecutionException {
    if (tasks.isEmpty()) {
        return;
    }
    final BatchedTask firstTask = tasks.get(0);
    // 如果一次提交多个任务，则必须有相同的 batchingKey，这些任务将被批量执行
    assert tasks.stream().allMatch(t -> t.batchingKey == firstTask.batchingKey) :
        "tasks submitted in a batch should share the same batching key: " + tasks;
    // convert to an identity map to check for dups based on task identity
    // 转化为 Map，key 为 task 对象，例如 ClusterStateUpdateTask
    // 如果提交的任务列表存在重复则抛出异常
    final Map<Object, BatchedTask> tasksIdentity = tasks.stream().collect(Collectors.toMap(
        BatchedTask::getTask,
        Function.identity(),
        (a, b) -> { throw new IllegalStateException("cannot add duplicate task: " + a); },
        IdentityHashMap::new));
   
    synchronized (tasksPerBatchingKey) {
        // 如果不存在 batchingKey ,则添加进去，如果存在则不操作
        // 并获取对应的 value
        LinkedHashSet<BatchedTask> existingTasks = tasksPerBatchingKey.computeIfAbsent(firstTask.batchingKey,
            k -> new LinkedHashSet<>(tasks.size()));
        for (BatchedTask existing : existingTasks) {
            // check that there won't be two tasks with the same identity for the same batching key
            // 检查对于同一个批任务，不可两个具有相同 identity 的任务
            BatchedTask duplicateTask = tasksIdentity.get(existing.getTask());
            if (duplicateTask != null) {
                throw new IllegalStateException("task [" + duplicateTask.describeTasks(
                    Collections.singletonList(existing)) + "] with source [" + duplicateTask.source + "] is already queued");
            }
        }
        // 如果没异常，就添加新任务
        existingTasks.addAll(tasks);
    }
    // 将任务放入线程池中执行
    if (timeout != null) {
        threadExecutor.execute(firstTask, timeout, () -> onTimeoutInternal(tasks, timeout));
    } else {
        threadExecutor.execute(firstTask);
    }
}
```

submitTasks 方法第一个参数为任务列表，第二个参数为超时时间；提交的任务本质上是一个 Runnable。submitTasks 方法会对任务进行去重，批量提交的任务会被添加到 existingTasks 集合中，之后会将提交的任务放到线程池中执行。

虽然这里只将任务列表的第一个任务交个线程池执行，但是任务列表的全部任务被添加到 tasksPerBatchingKey 中，线程池执行任务时，根据任务的 batchingKey 从 tasksPerBatchingKey 中获取任务列表，
然后批量执行这个任务列表。

#### 执行任务