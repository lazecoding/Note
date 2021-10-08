# Cluster 模块

- 目录
    - [集群状态](#集群状态)
    - [ClusterService](#ClusterService)
        - [MasterService](#MasterService)
        - [ClusterApplierService](#ClusterApplierService)
    
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

### 