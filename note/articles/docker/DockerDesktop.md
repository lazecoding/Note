# Docker Desktop

Docker Desktop 是 Docker 在 Windows 10 和 macOS 操作系统上的官方安装方式，这个方法依然属于先在虚拟机中安装 Linux 然后再安装 Docker 的方法。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/docker/DockerDesktopForWindowsMac.png" width="600px">
</div>

我们以 Windows 为例，安装。

### 下载地址

下载地址：[Docker Desktop for Windows](https://hub.docker.com/editions/community/docker-ce-desktop-windows) 。

### 安装

下载完，一键安装，真就一键，安装中...

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/docker/DockerDesktopInstalling.png" width="600px">
</div>

安装完毕，点击重启 Windows，记得保存好正在干的事情，电脑要重启了。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/docker/DockerDesktopInstalled.png" width="600px">
</div>

### 启动 Docker Desktop

首先，我要接受用户条款。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/docker/UserTerms.png" width="600px">
</div>

然后进入 Docker Desktop，就报错了。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/docker/WSL2Incomplete.png" width="600px">
</div>

它说 `WSL 2` 安装不完整，同时告诉我们地址去安装。

地址：[WSL 2 kernel](https://docs.microsoft.com/zh-cn/windows/wsl/install-manual#step-4---download-the-linux-kernel-update-package) 。

### 安装 WSL 2

就两步：

- 下载安装包：<a href="https://wslstorestorage.blob.core.windows.net/wslblob/wsl_update_x64.msi" data-linktype="external">适用于 x64 计算机的 WSL2 Linux 内核更新包</a> 。
- 运行上一步中下载的更新包。 

安装完就 OK，重启 Docker Desktop。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/docker/DockerDesktopOK.png" width="600px">
</div>

### 换源

为了加速，换国内源，比如网易。

`setting > Docker Engine`：

```C
{
  "registry-mirrors": [
    "http://hub-mirror.c.163.com",
    "https://docker.mirrors.ustc.edu.cn"
  ],
  "insecure-registries": [],
  "debug": true,
  "experimental": false
}
```

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/docker/DockerEngine.png" width="600px">
</div>

### 测试

CMD 输入 `docker run hello-world`，这行命令会让 Docker 从官方仓库中拉去 hello-world 的镜像到本地,并且自动将其实例化成容器。

出现下面效果意味着环境正常：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/docker/DockerHelloWorld.png" width="600px">
</div>

### 禁止自启

任务管理器禁止自启即可。