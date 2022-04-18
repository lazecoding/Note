# 日志和快照管理

- 目录
  - [数据的持久化和复制](#数据的持久化和复制)
  - [日志管理](#日志管理)
    - [WAL 数据结构](#WAL-数据结构)
    - [WAL 文件物理格式](#WAL-文件物理格式)
    - [WAL 文件的初始化](#WAL-文件的初始化)
    - [WAL 追加日志项](#WAL-追加日志项)
    - [WAL 日志回放](#WAL-日志回放)
    - [Master 向 Slave 推送日志](#Master-向-Slave-推送日志)
    - [Slave 日志追加](#Slave-日志追加)

etcd 对数据的持久化，采用的是 binlong（日志，也称 AWL，即 Write-Ahead-Log）加 Snapshot（快照）的方式。

在计算机科学中，预写式日志（Write-Ahead-Log,WAL）是关系数据库系统中用于提供原子性和持久性（ACID 中的两个特性）的一系列技术。在使用 WAL 系统中，所有修改在提交之前都要写入 log 文件中。

log 文件中通常包括 redo 信息和 undo 信息。假设一个程序在执行某些操作过程中机器掉电了，在重新启动时，程序可能需要直到当时执行得操作是完全成功了还是部分成功或者完全失败。如果使用了 WAL，那么程序就可以检查 log 文件，并对突然掉电时计划执行的操作内容与实际上执行的操作内容进行比较。在这个比较的基础上，程序就可以决定是撤销已做的还是继续完成已做的操作，或者保持原样。

etcd 数据库的所有更新操作都需要先写入到 binlog 中，而 binlog 是实时写到磁盘上的，因此这样就可以保证不会丢失数据，即使机器断电，重新启动以后 etcd 也能通过读取并重放 binlog 里面的操作记录来重新建立数据库。

etcd 数据的高可用性和一致性是通过 Raft 算法实现的，Master 节点会通过 Raft 协议向 Slave 节点复制 binlog，Slave 节点根据 binlog 对操作进行重放，以维持数据的多个副本的一致性。也就是说 binlog 不仅仅是实现数据库持久化的一种手段，其实还是实现不同副本间一致性协议的重要手段。客户端对数据库发起所有写操作都会记录在 binlog 中，待主节点将更新日志在集群多数节点之间完成同步后，以便内存中的数据库中应用该日志项的内容，进而完成一次客户端的写请求。

### 数据的持久化和复制

当我们，通过以下命令向 etcd 中插入一个键值对：

```C
$ /etcdctl set /foo bar
```

etcd 会在默认的工作目录下生成两个子目录：snap 和 wal。

- `snap`：用于存放快照数据。etcd 为了防止 WAL 文件过多就会创建快照，snap 用于存储 etcd 的快照数据状态。
- `wal`：用于存放预写式日志，其最大的作用是记录整个数据变化的全部历程。在 etcd 中，所有数据的修改在提交之前，都要写入 WAL 中。使用 WAL 进行数据的存储使得 etcd 拥有故障快速恢复和数据回滚两个重要的功能。

故障快速恢复：如果你的数据遭到破坏，就可以通过执行所有 WAL 中记录的修改操作，快速从原始的数据恢复到数据损坏之前的状态。

数据回滚（undo）/重做（redo）：因为所有的修改操作都被记录在 WAL 中，所以进行回滚或者重做时，只需要反响或者正向执行日志即可。

既然有了 WAL 事实日志，为什么还需要做快照？因为随着操作积累，WAL 文件会很大，不仅占用大量磁盘空间，而且在重做数据时会有很多中间值，大可不必。etcd 默认每 10000 条记录做一次快照，做完快照的 WAL 文件就可以删除了。

首次启动时，etcd 会把启动的配置信息存储到 data-dir 参数指定的数据目录中。配置信息包括本地节点的 ID、集群 ID 和初始时集群信息。用户需要避免 etcd 从一个过期的数据目录中重新启动，因为使用过期的数据目录启动的节点会与集群中的其他节点产生不一致。所以，为了最大化集群的安全性，一旦有任何数据损坏或丢失的可能性，你就应该把这个节点从集群中移除，然后加入一个不带数据目录的新节点。

### 日志管理

etcd 提供一个 WAL 的日志库，日志追加等功能均由该库完成。

#### WAL 数据结构

WAL 数据结构定义如下：

```C
type WAL struct{
	dir string
	dirFile *os.File
	metadata []byte
	state raftpb.HardState
	start walpb.Snapshot
	decoder *decoder
	readClose func() error
	mu sync.Mutex
	enti uint64
	encoder *encoder
	locks []*fileutil.LockedFile
	fp *filePipeline
}
```

WAL 管理所有的更新日志，主要处理日志的追加，日志文件的切换，日志的回放等操作。

#### WAL 文件物理格式

etcd 所有的日志项最终都会被追加存储到 WAL 文件中，日志项有很多类型，具体如下：

- metadataType :这是一个特殊的日志项，被写在每个 WAL 文件的头部。
- entryType:应用的更新数据，也是日志中存储的最关键数据。
- stateType：代表日志项中存储的内容时快照。
- crcType：前一个 WAL 文件里面的数据的 crc，也是 WAL 文件的第一个记录项 snapshotType：当前快照的索引 {term，index}，即当前快照位于哪个日志记录，不同于 stateType，这里只是记录快照的索引，而非快照的数据。

每个日志项都由四部分组成：

- type:日志项类。
- crc：校验和。
- data：根据日志项类型存储的实际数据也不尽相同，如果 snapshotType 类型的日志项存储的是快照的日志索引，crcType 类型的日志项中则无数据项，其 crc 字段便充当了数据项。
- padding：为了保持数据项 8 子节对其而填充的数据。

#### WAL 文件的初始化

etcd 的 wal 库提供了初始化方法，应用需要显示调用初始化方法来完成日志初始化功能，初始化方法主要有两个 API：Create 与 Open。

Create API:

```C
func Create(dirpath string, metadata []byte) (*WAL, error) {
    if Exist(dirpath) {
        return nil, os.ErrExist
    }
    tmpdirpath := filepath.Clean(dirpath) + ".tmp" 
    if fileutil.Exist(tmpdirpath) {
        if err := os.RemoveAll(tmpdirpath); err != nil {
            return nil, err
        }
    }

    if err := fileutil.CreateDirAll(tmpdirpath); err != nil {
        return nil, err
    }
    p := filepath.Join(tmpdirpath, walName(0, 0))
    f, err := fileutil.LockFile(p, os.O_WRONLY|os.O_CREATE, fileutil.PrivateFileMode)
    if err != nil {
        return nil, err
    }
    if _, err = f.Seek(0, io.SeekEnd); err != nil {
        return nil, err
    }

    if err = fileutil.Preallocate(f.File, SegmentSizeBytes, true); err != nil {
        return nil, err
    }
    w := &WAL{
       dir:      dirpath,
       metadata: metadata,
    }
    w.encoder, err = newFileEncoder(f.File, 0)
    if err != nil {
        return nil, err
    }

    w.locks = append(w.locks, f)
    if err = w.saveCrc(0); err != nil {
        return nil, err
    }
    if err = w.encoder.encode(&walpb.Record{Type: metadataType, Data: metadata}); err != nil {
        return nil, err
    }

    if err = w.SaveSnapshot(walpb.Snapshot{}); err != nil {
        return nil, err
    }
    if w, err = w.renameWal(tmpdirpath); err != nil {
        return nil, err
    }
    pdir, perr := fileutil.OpenDir(filepath.Dir(w.dir))
    if perr != nil {
        return nil, perr
    }

    if perr = fileutil.Fsync(pdir); perr != nil {
        return nil, perr
    }
    if perr = pdir.Close(); err != nil {
        return nil, perr
    }
    return w, nil 
}
```

Create 做的事情也比较简单：

- 创建 WAL 目录，用于存储 WAL 日志文件以及 snapshot 索引。
- 预分配第一个 WAL 日志文件，默认是 64MB，使用预分配机制可以提高写入性能。
- 其他，包括使用临时目录并最终重命名为正式目录名等 trick，可以忽略。

Open 则是在 Create 完成以后被调用，主要是用于打开 WAL 目录下的日志文件，Open 的主要作用是找到当前 Snapshot 以后的所有 WAL 日志，这是因为当前的 Snapshot 之前的日志我们不再关心了，因为日志的内容肯定都已经被更新至 Snapshot 了，这些日志也是在后面日志回收中可以被删除的部分。

Open API：

```C
func Open(dirpath string, snap walpb.Snapshot) (*WAL, error) {
    w, err := openAtIndex(dirpath, snap, true)
    if err != nil {
        return nil, err
    }
    if w.dirFile, err = fileutil.OpenDir(w.dir); err != nil {
        return nil, err
    }
    return w, nil 
}
```

其中最重要的就是 openAtIndex 了，该函数用于寻找寻找最新的快照之后的日志文件并打开。

#### WAL 追加日志项

日志项的追加是通过调用 etcd 的 wal 库的 Save() 方法实现，具体如下：

Save API：

```C
func (w *WAL) Save(st raftpb.HardState, ents []raftpb.Entry) error {
    w.mu.Lock()
    defer w.mu.Unlock()
    // short cut, do not call sync 
    if raft.IsEmptyHardState(st) && len(ents) == 0 {
        return nil 
    }
    mustSync := raft.MustSync(st, w.state, len(ents))
    for i := range ents {
        if err := w.saveEntry(&ents[i]); err != nil {
            return err
        }
    }
    if err := w.saveState(&st); err != nil {
        return err
    }
    curOff, err := w.tail().Seek(0, io.SeekCurrent)
    if err != nil {
        return err
    }
    if curOff < SegmentSizeBytes {
        if mustSync {
            return w.sync()
        }
        return nil 
    }
    return w.cut()
}
```

该函数的核心是：

- 调用 saveEntry() 将日志项存储到 WAL 文件中。
- 如果追加后日志文件超过既定的 SegmentSizeBytes 大小，需要调用 w.cut() 进行 WAL 文件切换，即：关闭当前 WAL 日志，创建新的 WAL 日志，继续用于日志追加。

saveEntry 日志项数据进行编码并追加至WAL文件，存储在 WAL 文件中的日志项有多种类型，对于普通的应用更新请求，类型为 entryType。

而具体的编码写入方法则由专门的编码结构实现，称为 struct encoder，该结构实现了日志项的编码和将日志项编码后的数据写入日志文件的功能。感兴趣的读者可以结合上面的日志项结构自行阅读。

cut 则实现了WAL文件切换的功能，每个WAL文件的预设大小是 64MB，一旦超过该大小，便创建新的 WAL 文件，这样做的好处是便于 WAL 文件的回收，我们在后面会说明。

#### WAL 日志回放

WAL 的日志回放的主要流程：

- 加载最新的 Snapshot。
- 打开 WAL 文件目录，根据上面的描述，这里主要目的是找到最新 Snapshot 以后的日志文件，这些是需要被回放的日志。
- 在 2 的基础上读出所有的日志项（会不会日志项太多？导致内存装不下？）。
- 将 3 读出的日志项应用到内存中，这里的内存指的是上面我们说过的 Storage，给 raft 协议核心处理层提供的内存日志存储中，这样，raft 核心协议处理层就可以将日志同步给其他节点了。

所以，对于 WAL 日志回放功能，底层的 WAL 日志库只需要给上层应用提供一个读取所有日志项的功能即可，这由 ReadAll() 实现。

#### Master 向 Slave 推送日志

Master 向 Slave 进行日志同步的函数是 bcastAppend()，sendAppend() 向特定的 Slave 发送日志同步命令。该方法首先会找到该
Slave 一次已同步过的日志位置，然后从 raftLog 中获取该位置以后的日志项，当然每次同步的数量不宜太多，由 maxMsgSize 限制。如果无法从 raftLog 中获取想要的日志项，则需要考虑发送快照，这是因为对应的日志项可能由于被提交而丢弃。

当 Master 在收到来自 Slave 对于日志复制消息 MsgApp 的响应之后进行如下操作：调用 maybeCommit() 查看是否可以继续提交；如果可以，则继续向 Follower 发送日志同步消息。继续提交其实就是将 raftLog 的 commit 位置设置为新的值。

#### Slave 日志追加

Slave 节点在收到 Master 的日志同步消息时，Slave 节点的日志追加过程与 Master 节点完全一致，不同之处是日志来源：Leader 节点的日志来自于客户端的写请求（MsgProp），而 Slave 的日志则是来自于 Leader 的日志复制消息（MsgApp）。