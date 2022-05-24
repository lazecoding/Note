# Go 环境安装

Golang 是一款比较 `简单` 的语言。

### 安装包下载及安装

[Golang 安装包地址](https://golang.google.cn/dl/) ：https://golang.google.cn/dl/ 。

下载对应平台的安装包，在某路径下安装。

### 环境变量

GOROOT：

GOROOT 就是 Go 的安装目录。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/go/GO-GOROOT.png" width="600px">
</div>

GOPATH:

GO 1.13+ 版本使用 GO MODULE 管理项目,不强制配置 GOPATH。

系统变量 PATH：

系统变量添加 `%GOROOT%/bin`。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/go/PATH-GO-bin.png" width="600px">
</div>

> GOROOT 就是 Go 的安装目录；GOPATH 是我们的工作空间,保存 GO 项目代码和第三方依赖包。

### 设置

go mod 配置：

```C
go env -w GOBIN=D:\devpath\Golang\Go 1.6\bin
go env -w GO111MODULE=on
go env -w GOPROXY=https://goproxy.cn,direct
```

> 当 modules 功能启用时，依赖包的存放位置变更为 `$GOPATH/pkg`，允许同一个 package 多个版本并存，且多个项目可以共享缓存的 module。

DEBUG 设置：

```C
go env -w GOARCH=amd64
```

> $go env  查看 Go 环境属性。