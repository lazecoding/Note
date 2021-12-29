# Jmeter

JMeter 是 Apache 组织开发的基于 Java 的压力测试工具。用于对软件做压力测试，它最初被设计用于 Web 应用测试，但后来扩展到其他测试领域。
它可以用于测试静态和动态资源，例如静态文件、Java 小服务程序、CGI 脚本、Java 对象、数据库、FTP 服务器，等等。

### 下载

在安装 Jmeter 之前，需要先安装并配置 JDK 环境。目前最新版的 JMeter 5.4.1，要求 JDK 8 以上的版本。

JDK 安装略。

Jmeter 下载地址：https://jmeter.apache.org/download_jmeter.cgi

下载：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/test/Jmeter下载地址.png" width="600px">
</div>

这里我们下载 `Binaries apache-jmeter-5.4.3.zip`。

### 安装和配置

解压 apache-jmeter-5.4.3.zip。

配置系统变量 —— `JMETER_HOME`。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/test/Jmeter系统变量.png" width="600px">
</div>

这里 `系统变量` —— `JMETER_HOME` 配置的是 Jmeter 解压包根目录。

执行 `根目录\lib\jmeter.bat` 启动 Jmeter。

### 插件

Jmeter 需要安装插件管理器 —— `jmeter-plugins-manager-1.6.jar`。

插件下载地址：https://jmeter-plugins.org/install/Install/

插件存放地址：`根目录/lib/ext/..`。

启动 Jmeter，在窗口选项菜单下回出现一个 `Plugins Manager` 选项。
