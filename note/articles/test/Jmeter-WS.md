# Jmeter 压测 —— WebSocket

我们可以用 Jmeter 压测 WebSocket 服务器，点击 [Jmeter](https://github.com/lazecoding/Note/blob/main/note/articles/test/Jmeter.md)
查看 Jmeter 安装和配置。

### 安装插件

使用 Jmeter 压测 WebSocket 需要先安装插件 —— `WebSocket Samplers by Peter Doornbosch`。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/test/Jmeter安装WebSocket插件.png" width="600px">
</div>

### 压测

`压测规则`：建立 N 条连接并保持，每条连接每个三秒与服务端通信一次。

`制订脚本`：

- 创建 ThreadGroup。
    - WebSocket Open Connection：建立链接。
    - Loop Controller：建立连接后，在 Loop Controller 中保持心跳。
        - WebSocket request-response Sampler 复用 WebSocket Open Connection 中的 连接，发送心跳包。
        - Constant Timer ：设置 Loop 周期。
    - View Results Tree ：查看通信结果。
    - Summary Report：查看汇总报告。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/test/Jmeter-WS-制订压测脚本.png" width="600px">
</div>

- 创建 ThreadGroup

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/test/Jmeter-WS-创建ThreadGroup.png" width="600px">
</div>

`Numbers of Threads` 数量等价于连接数量。

- 设置 WebSocket Open Connection

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/test/Jmeter-WS-设置OpenConnection.png" width="600px">
</div>

配置 Server URL。

- 设置 Loop Controller

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/test/Jmeter-WS-设置LoopController.png" width="600px">
</div>

Loop Count 设置为 Infinite。Loop Controller 里面涉及循环体，用于持续通信，保持心跳。

- 设置 WebSocket request-response Sampler

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/test/Jmeter-WS-设置request-response.png" width="600px">
</div>

设置 `use existing connection` 复用 `WebSocket Open Connection` 中创建的连接。

- 设置 Constant Timer

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/test/Jmeter-WS-设置ConstantTimer.png" width="600px">
</div>

用于设置 `Loop Controller` 的循环周期，这里设置的是 3S。

运行脚本，可以在 `View Results Tree` 和 `Summary Report` 中查看通信结果和压测报告。

### 压测环境

处理器：Intel(R) Core(TM) i7-10510U CPU @ 1.80GHz   2.30 GHz（拉跨的办公电脑）

应用 JVM 参数：

```C
-Xms6G
-Xmx6G
-XX:MaxDirectMemorySize=2G
-XX:+UseG1GC
```

### 压测结果

并发支持 5W 接入的持续通信的链接。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/test/Jmeter-WS-压测报告.png" width="600px">
</div>

　　