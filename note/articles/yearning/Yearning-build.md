# Yearning 源码环境构建

- 目录
  - [开源仓库](#开源仓库)
  - [技术栈](#技术栈)
  - [项目结构](#项目结构)
  - [源码环境](#源码环境)
    - [编译前端](#编译前端)
    - [编译后端](#编译后端)
    - [数据库准备](#数据库准备)
    - [配置 conf.toml](#配置-conf.toml)
    - [初始化及安装](#初始化及安装)
    - [Juno](#Juno)
    - [DEBUG](#DEBUG)
  - [外链](#外链)
  
Yearning 是 Go 开发的 SQL 审核平台。

### 开源仓库

后端仓库：https://github.com/cookieY/Yearning 。

前端仓库：https://github.com/cookieY/gemini-next 。

### 技术栈

后端：

golang + yee(写法类似echo) + gorm + net/rpc

前端：

js + vue3.2 + vite2 + antDesignVue3.2.0

### 项目结构

后端：

```C
|-- Yearning
    |-- .gitignore
    |-- Juno
    |-- LICENSE
    |-- README.md
    |-- README_EN.md
    |-- conf.toml.template
    |-- go.mod
    |-- go.sum
    |-- logo_s.png
    |-- main.go
    |-- cmd  # cli指令
    |   |-- cli.go
    |   |-- cmd.go
    |   |-- migrate.go
    |-- docker
    |   |-- Dockerfile
    |   |-- README.md
    |   |-- docker-compose.yml
    |-- img
    |   |-- audit.png
    |   |-- dash.png
    |   |-- login.png
    |   |-- logo.jpeg
    |   |-- query.png
    |   |-- record.png
    |-- migrate
    |   |-- main.go
    |-- src
        |-- apis
        |   |-- dash.go
        |   |-- fetch.go
        |   |-- query.go
        |-- engine
        |   |-- engine.go
        |-- handler # 接口核心逻辑
        |   |-- dashboard.go
        |   |-- commom # 公共方法
        |   |   |-- error.go
        |   |   |-- expr.go
        |   |   |-- types.go
        |   |   |-- util.go
        |   |-- fetch # 通用获取接口
        |   |   |-- fetch.go
        |   |   |-- fetch_test.go
        |   |   |-- impl.go
        |   |-- login # 登录及个人信息接口
        |   |   |-- login.go
        |   |   |-- profile.go
        |   |-- manage # 管理接口
        |   |   |-- board.go
        |   |   |-- autoTask
        |   |   |   |-- autoTask.go
        |   |   |   |-- impl.go
        |   |   |   |-- route.go
        |   |   |-- db
        |   |   |   |-- dbmanage.go
        |   |   |   |-- impl.go
        |   |   |   |-- route.go
        |   |   |-- group
        |   |   |   |-- group.go
        |   |   |   |-- impl.go
        |   |   |   |-- route.go
        |   |   |-- roles
        |   |   |   |-- roles.go
        |   |   |   |-- route.go
        |   |   |-- settings
        |   |   |   |-- route.go
        |   |   |   |-- setting.go
        |   |   |   |-- setting_test.go
        |   |   |-- tpl
        |   |   |   |-- impl.go
        |   |   |   |-- route.go
        |   |   |   |-- tpl.go
        |   |   |   |-- tpl_test.go
        |   |   |-- user
        |   |       |-- impl.go
        |   |       |-- route.go
        |   |       |-- user.go
        |   |       |-- user_test.go
        |   |-- order # 工单接口
        |   |   |-- audit
        |   |   |   |-- audit.go
        |   |   |   |-- impl.go
        |   |   |   |-- route.go
        |   |   |-- osc
        |   |   |   |-- impl.go
        |   |   |   |-- osc.go
        |   |   |   |-- route.go
        |   |   |-- query
        |   |   |   |-- query.go
        |   |   |   |-- route.go
        |   |   |-- record
        |   |       |-- record.go
        |   |-- personal # 用户工单提交/查询 接口
        |       |-- impl.go
        |       |-- order.go
        |       |-- post.go
        |       |-- query.go
        |       |-- query_test.go
        |       |-- route.go
        |       |-- util.go
        |-- lib #公共库
        |   |-- ding.go
        |   |-- encrypt.go
        |   |-- encrypt_test.go
        |   |-- jwtAuth.go
        |   |-- rpc.go
        |   |-- sendMail.go
        |   |-- sendMail_test.go
        |   |-- toolbox.go
        |   |-- toolbox_test.go
        |   |-- wrapper.go
        |-- model # 数据模型
        |   |-- db.go
        |   |-- db_test.go
        |   |-- global.go
        |   |-- impl.go
        |   |-- modal.go
        |-- router # 后端路由表
        |   |-- router.go
        |-- service # Yearning启动函数
        |   |-- migrate.go
        |   |-- migrate_test.go
        |   |-- yearning.go
        |-- test
            |-- testCore.go
```

前端：

```C
|-- gemini-next
    |-- .gitignore
    |-- README.md
    |-- index.html
    |-- package.json
    |-- tsconfig.json
    |-- vite.config.ts
    |-- yarn.lock
    |-- img
    |   |-- audit.png
    |   |-- dash.png
    |   |-- login.png
    |   |-- logo.jpeg
    |   |-- query.png
    |   |-- record.png
    |-- public
    |   |-- favicon.ico
    |   |-- icon.png
    |-- src
        |-- App.vue
        |-- global.d.ts
        |-- main.ts
        |-- router.ts
        |-- shims-vue.d.ts
        |-- vite-env.d.ts
        |-- apis
        |   |-- autotask.ts
        |   |-- board.ts
        |   |-- dash.ts
        |   |-- db.ts
        |   |-- fetchSchema.ts
        |   |-- flow.ts
        |   |-- homeApis.ts
        |   |-- listAppApis.ts
        |   |-- loginApi.ts
        |   |-- orderPostApis.ts
        |   |-- policy.ts
        |   |-- query.ts
        |   |-- record.ts
        |   |-- rules.ts
        |   |-- setting.ts
        |   |-- user.ts
        |-- assets
        |   |-- comment
        |   |   |-- 1.svg
        |   |   |-- 2.svg
        |   |   |-- 3.svg
        |   |   |-- comment.svg
        |   |   |-- rockets.svg
        |   |-- login
        |       |-- 1.mp4
        |       |-- logo.png
        |-- components # 组件库
        |   |-- chartCard
        |   |   |-- chart.less
        |   |   |-- chartCard.vue
        |   |   |-- miniArea.vue
        |   |   |-- miniBar.vue
        |   |   |-- miniCol.vue
        |   |-- editor
        |   |   |-- editor.vue
        |   |   |-- impl.ts
        |   |   |-- keyword.ts
        |   |   |-- work.ts
        |   |-- listApp
        |   |   |-- listApp.vue
        |   |   |-- queryApp.vue
        |   |   |-- queryBanner.vue
        |   |   |-- queryOrder.vue
        |   |-- menu
        |   |   |-- menu.vue
        |   |-- orderProfile
        |   |   |-- comment.vue
        |   |   |-- orderProfile.vue
        |   |   |-- osc.vue
        |   |   |-- rejectModal.vue
        |   |   |-- results.vue
        |   |-- pageHeader
        |   |   |-- pageHeader.vue
        |   |-- queryProfile
        |   |   |-- queryProfile.vue
        |   |-- steps
        |   |   |-- steps.vue
        |   |-- table
        |   |   |-- index.ts
        |   |   |-- orderTable.vue
        |   |   |-- orderTableSearch.vue
        |   |   |-- stateTags.vue
        |   |   |-- table.vue
        |   |-- user
        |       |-- changePassword.vue
        |       |-- registerForm.vue
        |       |-- userRules.vue
        |-- config # 通用配置
        |   |-- request.ts
        |   |-- vars.ts
        |-- lang # i18n相关
        |   |-- en-us.ts
        |   |-- index.ts
        |   |-- zh-cn.ts
        |   |-- en-us
        |   |   |-- autoTask
        |   |   |   |-- index.ts
        |   |   |-- common
        |   |   |   |-- index.ts
        |   |   |-- menu
        |   |   |   |-- index.ts
        |   |   |-- order
        |   |   |   |-- index.ts
        |   |   |-- query
        |   |   |   |-- index.ts
        |   |   |-- record
        |   |   |   |-- index.ts
        |   |   |-- setting
        |   |   |   |-- index.ts
        |   |   |-- user
        |   |       |-- index.ts
        |   |-- zh-cn
        |       |-- autoTask
        |       |   |-- index.ts
        |       |-- common
        |       |   |-- index.ts
        |       |-- menu
        |       |   |-- index.ts
        |       |-- order
        |       |   |-- index.ts
        |       |-- query
        |       |   |-- index.ts
        |       |-- record
        |       |   |-- index.ts
        |       |-- setting
        |       |   |-- index.ts
        |       |-- user
        |           |-- index.ts
        |-- lib
        |   |-- index.ts
        |-- mixins 
        |   |-- common.ts
        |   |-- db.ts
        |   |-- fetch.ts
        |   |-- juno.ts
        |-- socket
        |   |-- index.ts
        |-- store
        |   |-- index.ts
        |   |-- types.ts
        |   |-- module
        |       |-- common.ts
        |       |-- highlight.ts
        |       |-- menu.ts
        |       |-- order.ts
        |       |-- user.ts
        |-- style
        |   |-- theme.less
        |-- types
        |   |-- index.ts
        |-- views # 页面
            |-- apply
            |   |-- apply.vue
            |   |-- order.vue
            |-- common
            |   |-- announce.vue
            |   |-- auditLayout.vue
            |   |-- sponsor.vue
            |   |-- subLayout.vue
            |-- home
            |   |-- home.vue
            |   |-- profile.vue
            |-- layout
            |   |-- layout.vue
            |-- login
            |   |-- login-form.vue
            |   |-- login.vue
            |-- manager
            |   |-- autotask
            |   |   |-- autotask.vue
            |   |   |-- autotaskModal.vue
            |   |   |-- autotaskTable.vue
            |   |-- board
            |   |   |-- board.vue
            |   |-- db
            |   |   |-- db.vue
            |   |   |-- dbForm.vue
            |   |   |-- dbModal.vue
            |   |   |-- dbTable.vue
            |   |   |-- dbTableSearch.vue
            |   |-- flow
            |   |   |-- flow.vue
            |   |   |-- flowModal.vue
            |   |   |-- flowTable.vue
            |   |-- policy
            |   |   |-- policy.vue
            |   |   |-- policyModal.vue
            |   |   |-- policyTable.vue
            |   |-- rules
            |   |   |-- rules.ts
            |   |   |-- rules.vue
            |   |-- setting
            |   |   |-- setting.vue
            |   |-- user
            |       |-- user.vue
            |       |-- userTable.vue
            |       |-- userTableSearch.vue
            |-- query
            |   |-- clip.vue
            |   |-- clipBoard.vue
            |   |-- history.vue
            |   |-- input.vue
            |   |-- modal.vue
            |   |-- query.vue
            |   |-- table.vue
            |   |-- tree.vue
            |-- record
            |   |-- order.vue
            |   |-- query.vue
            |   |-- record.vue
            |   |-- libs
            |       |-- tips.vue
            |-- server
                |-- order
                |   |-- list.vue
                |-- query
                    |-- list.vue
                    |-- querySearch.vue
```

### 源码环境

下面开始构建源码环境

#### 编译前端

首先要先编译前端，打包到 dist 文件夹下。

#### 编译后端

在配置好 go mod 属性的前提下引入依赖，命令如下：

```C
go mod tidy
```

引来引入完毕了，会发现 src/service/yearning.go 中 go:embed 报错，未解析到相应文件。

这时候需要将前端编译的 dist 文件夹放到 `src/service/` 文件夹下，即可。

#### 数据库准备

Yearning 不依赖于任何第三方 SQL 审核工具作为审核引擎,内部已自己实现审核/回滚相关逻辑，仅依赖 MySQL 数据库。

MySQL 版本必须为5.7及以上版本(8.0 及以上请将 sql_mode 设置为空)并已事先自行安装完毕且创建 Yearning 库,字符集应为 UTF8mb4 (仅 Yearning 所需 MySQL 版本)。

#### 配置 conf.toml

```C
[Mysql]
Db = "Yearning"
Host = "127.0.0.1"
Port = "3306"
Password = "xxxx"
User = "root"

[General] # 数据库加解密key，只可更改一次。
SecretKey = "dbcjqheupqjsuwsm"
Hours = 4
Concurrent = 4
RpcAddr = "111.111.111.111:50001"
```

SecretKey 是 token/数据库密码加密/解密的 salt，建议所有用户在初次安装 Yearning 之前将 SecretKey 更改(不更改将存在安全风险)。

格式: 大小写字母均可, 长度必须为 16 位 如长度不是 16 位将会导致无法新建数据源

特别注意:此 key 仅可在初次安装时更改！之后不可再次更改！如再次更改会导致之前已存放的数据源密码无法解密，最终导致无法获取相关数据源信息。

#### 初始化及安装

在 Goland 中执行 go build，会编译出一个 exe 文件，执行安装：

```C
xxx/Yearning.exe install
```

初始化结束后就可以正常运行了：

```C
xxx/Yearning.exe run
```

打开浏览器：http://127.0.0.1:8000

默认账号/密码：admin/Yearning_admin

help 可以查看帮助信息：

```C
xxx/Yearning.exe --help
```

#### Juno

知道上面还没有完全正常运行 Yearning，还需要启动一个 Juno 项目，Juno 是 Yearning 的审核引擎。

> Juno 作者未开源，只提供了二进制文件，需要我们使用 Linux 部署。

`conf.toml#RpcAddr` 配置的就是 Juno 项目的端口。

#### DEBUG

在 Golang 中 DeBUG,需要在 go build 中勾选 `run after build`，而且 Yearning 项目启动需要命令行键入 run，所以还需要再 go build 中为 `Program arguments` 属性设置字符 `run`。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/go/yearning-debug-setting.png" width="600px">
</div>

这样就可以愉快的 DEBUG 了。

### 外链

[Yearning 安装指南](https://guide.yearning.io/): https://guide.yearning.io  