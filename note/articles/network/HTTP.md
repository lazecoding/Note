# HTTP

- 目录
    - [起源](#起源)
    - [HTTP 协议](#HTTP-协议)
        - [HTTP 报文](#HTTP-报文)
        - [HTTP 方法](#HTTP-方法)
        - [HTTP 状态码](#HTTP-状态码)
        - [HTTP 首部](#HTTP-首部)
    - [HTTP 演进](#HTTP-演进)
        - [HTTP 0.9](#HTTP-0.9)
        - [HTTP 1.0](#HTTP-1.0)
        - [HTTP 1.1](#HTTP-1.1)
        - [HTTP 2](#HTTP-2)
        - [HTTPS](#HTTPS)
        - [HTTP 3](#HTTP-3)
    - [特性和应用](#特性和应用)
        - [短连接与长连接](#短连接与长连接)
        - [流水线](#流水线)
        - [Cookie](#Cookie)
        - [缓存](#缓存)
        - [内容编码](#内容编码)
        - [虚拟主机](#虚拟主机)    
        - [隧道](#隧道)
    - [通信安全](#通信安全)
        - [加密技术](#加密技术)
        - [数字签名](#数字签名)
        - [数字证书](#数字证书)
        - [HTTPS 通信安全](#HTTPS-通信安全)
    - [攻击技术](#通信安全)
        - [输入输出引发的缺陷](#输入输出引发的缺陷)
            - [跨站脚本攻击](#跨站脚本攻击)
            - [SQL 注入](#SQL-注入)
            - [OS 注入](#OS-注入)
            - [HTTP 首部注入](#HTTP-首部注入)
            - [目录遍历攻击](#目录遍历攻击)
            - [远程文件包含漏洞](#远程文件包含漏洞)
        - [设置或设计引发的缺陷](#设置或设计引发的缺陷)
            - [不正确的消息提示](#不正确的消息提示)
            - [开放重定向](#开放重定向)
        - [会话管理引发的缺陷](#会话管理引发的缺陷)
            - [会话劫持](#会话劫持)
            - [会话固定攻击](#会话固定攻击)
            - [跨站点请求伪造](#跨站点请求伪造)
        - [其他缺陷](#其他缺陷)  
            - [密码破解](#密码破解)
            - [点击劫持](#点击劫持)
            - [DOS 攻击](#DOS-攻击)
            - [后门程序](#后门程序)


HTTP（Hypertext Transfer Protocol，超文本传输协议）是一个简单的请求 - 响应协议，它通常运行在 TCP（大多如此，并不是协议规定，也有基于 UDP 的实现 ） 之上。它指定了客户端可能发送给服务器什么样的消息以及得到什么样的响应。请求和响应消息的头以 ASCII 形式给出，而消息内容则具有一个类似 MIME 的格式。

### 起源

万维网（World Wide Web，WWW）发源于欧洲日内瓦量子物理实验室 CERN，正是 WWW 技术的出现使得因特网得以超乎想象的速度迅猛发展。WWW 的成功归结于它的简单、实用，在它的背后有一系列的协议和标准支持它完成如此宏大的工作，即 Web 协议族，其中就包括 HTTP 超文本传输协议。

HTTP 是应用层协议，同其他应用层协议一样，是为了实现某一类具体应用的协议，并由某一运行在用户空间的应用程序来实现其功能。HTTP 是一种协议规范，这种规范记录在文档上，为真正通过 HTTP 进行通信的 HTTP 的实现程序。

HTTP 是基于 B/S 架构进行通信的，而 HTTP 的服务器端实现程序有 httpd、nginx 等，其客户端的实现程序主要是 Web 浏览器，例如 Firefox、InternetExplorer、Google chrome、Safari、Opera 等，此外，客户端的命令行工具还有 elink、curl 等。

HTTP 的发展经历了多个版本：HTTP/0.9、HTTP/1.0、HTTP/1.1、HTTP/2、HTTP/3。

### HTTP 协议

HTTP 协议用于客户端和服务器中间的通信，客户端程序和服务器程序通过交换 HTTP 报文进行会话，HTTP 定义了这些报文的结构以及客户和服务器进行报文交换的方式。

HTTP 协议规定客户端发送一个请求报文给服务器，服务器根据请求报文中的信息进行处理，并将处理结果放入响应报文中返回给客户端。

#### HTTP 报文

用于 HTTP 协议交互的信息被称为 HTTP 报文，HTTP 报文本身是由多行数据构成的字符串文本。HTTP 报文大致可以分为报文首部和报文主体两部分，两者由空行划分。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/HTTP报文基本格式.png" width="600px">
</div>

HTTP 报文有两种：请求报文和响应报文。

- 请求报文

请求报文结构：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/请求报文结构.png" width="600px">
</div>

请求报文实例：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/请求报文实例.png" width="600px">
</div>

- 响应报文

响应报文结构：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/响应报文结构.png" width="600px">
</div>

响应报文实例：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/响应报文实例.png" width="600px">
</div>

请求报文和响应报文的首部内容由以下数据组成：

- `请求行`：包含用于请求的方法，请求的 URI 和 HTTP 版本。
- `状态行`：包含表明响应结果的状态码、原因短语和 HTTP 版本。
- `首部字段`：包含表示请求和响应的各种条件和属性的各类首部（一般有四种首部：通用首部、请求首部、响应首部、实体首部）。
- `其他`：可能包含 HTTP 的 RFC 中未定义的首部（如 Cookie）。

报文主体用于传输请求或响应的实体主体。通常报文主体等于实体主体，只有当传输中进行编码操作，实体主体的内容发送变化，报文主体和实体主体才会不一致。

- `报文`：是 HTTP 通信的基本单位，由 8 位字节流组成，通过 HTTP 通信传输。
- `实体`：作为请求或响应的有效载荷数据被传输，其内容由实体首部和实体主体组成。

报文主体并不是必须的，如 GET 类型的请求报文。

#### HTTP 方法

客户端发送的   **请求报文**   第一行为请求行，包含了方法字段。

- GET

> 获取资源

当前网络请求中，绝大部分使用的是 GET 方法。

- HEAD

> 获取报文首部

和 GET 方法类似，但是不返回报文实体主体部分。

主要用于确认 URL 的有效性以及资源更新的日期时间等。

- POST

> 传输实体主体

POST 主要用来传输数据，而 GET 主要用来获取资源。

- PUT

> 上传文件

由于自身不带验证机制，任何人都可以上传文件，因此存在安全性问题，一般不使用该方法。

```html
PUT /new.html HTTP/1.1
Host: example.com
Content-type: text/html
Content-length: 16

<p>New File</p>
```

-PATCH

> 对资源进行部分修改

PUT 也可以用于修改资源，但是只能完全替代原始资源，PATCH 允许部分修改。

```html
PATCH /file.txt HTTP/1.1
Host: www.example.com
Content-Type: application/example
If-Match: "e0023aa4e"
Content-Length: 100

[description of changes]
```

- DELETE

> 删除文件

与 PUT 功能相反，并且同样不带验证机制。

```html
DELETE /file.html HTTP/1.1
```

- OPTIONS

> 查询支持的方法

查询指定的 URL 能够支持的方法。

会返回 `Allow: GET, POST, HEAD, OPTIONS` 这样的内容。

- CONNECT

> 要求在与代理服务器通信时建立隧道

使用 SSL（Secure Sockets Layer，安全套接层）和 TLS（Transport Layer Security，传输层安全）协议把通信内容加密后经网络隧道传输。

```html
CONNECT www.example.com:443 HTTP/1.1
```

- TRACE

> 追踪路径

服务器会将通信路径返回给客户端。

发送请求时，在 Max-Forwards 首部字段中填入数值，每经过一个服务器就会减 1，当数值为 0 时就停止传输。

通常不会使用 TRACE，并且它容易受到 XST 攻击（Cross-Site Tracing，跨站追踪）。

[rfc2616：9 Method Definitions](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html)

#### HTTP 状态码

服务器返回的   **响应报文**   中第一行为状态行，包含了状态码以及原因短语，用来告知客户端请求的结果。

| 状态码 | 类别 | 含义 |
| :---: | :---: | :---: |
| 1XX | Informational（信息性状态码） | 接收的请求正在处理 |
| 2XX | Success（成功状态码） | 请求正常处理完毕 |
| 3XX | Redirection（重定向状态码） | 需要进行附加操作以完成请求 |
| 4XX | Client Error（客户端错误状态码） | 服务器无法处理请求 |
| 5XX | Server Error（服务器错误状态码） | 服务器处理请求出错 |

- 1XX 信息

>   100 Continue：表明到目前为止都很正常，客户端可以继续发送请求或者忽略这个响应。

- 2XX 成功

>   200 OK
<br><br>
204 No Content：请求已经成功处理，但是返回的响应报文不包含实体的主体部分。一般在只需要从客户端往服务器发送信息，而不需要返回数据时使用。
<br><br>
206 Partial Content：表示客户端进行了范围请求，响应报文包含由 Content-Range 指定范围的实体内容。

- 3XX 重定向

> 301 Moved Permanently：永久性重定向
<br><br>
302 Found：临时性重定向
<br><br>
303 See Other：和 302 有着相同的功能，但是 303 明确要求客户端应该采用 GET 方法获取资源。
<br><br>
304 Not Modified：如果请求报文首部包含一些条件，例如：If-Match，If-Modified-Since，If-None-Match，If-Range，If-Unmodified-Since，如果不满足条件，则服务器会返回 304 状态码。
<br><br>
307 Temporary Redirect：临时重定向，与 302 的含义类似，但是 307 要求浏览器不会把重定向请求的 POST 方法改成 GET 方法。


注：虽然 HTTP 协议规定 301、302 状态下重定向时不允许把 POST 方法改成 GET 方法，但是大多数浏览器都会在 301、302 和 303 状态下的重定向把 POST 方法改成 GET 方法。

- 4XX 客户端错误

> 400 Bad Request：请求报文中存在语法错误。
<br><br>
401 Unauthorized：该状态码表示发送的请求需要有认证信息（BASIC 认证、DIGEST 认证）。如果之前已进行过一次请求，则表示用户认证失败。
<br><br>
403 Forbidden：请求被拒绝。
<br><br>
404 Not Found

#### HTTP 首部

HTTP 协议的请求和响应报文必定包含 HTTP 首部，首部内容为客户端和服务器分别处理请求和响应提供所需的信息。

在请求报文中，报文首部由方法、URI、HTTP 版本、HTTP 首部字段等部分构成。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/HTTP请求报文首部组成.png" width="600px">
</div>

在响应报文中，报文首部由HTTP 版本、状态码、HTTP 首部字段等部分构成。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/HTTP响应报文首部组成.png" width="600px">
</div>

实际上，HTTP 首部字段分成四种类型：通用首部字段、请求首部字段、响应首部字段、实体首部字段。

- 通用首部字段：可以应用于请求和响应中，但是与在消息主体中的数据无关。
- 请求首部字段：含有与所要获取的资源或者客户端自身相关的附加信息。
- 响应首部字段：含有与响应相关的附加信息，比如它的位置或者与服务器相关的信息（名称、版本号等）。
- 实体首部字段: 含有与消息主体相关的附加信息，比如长度或者MIME类型。

具体内容访问：[首部字段信息](https://developer.mozilla.org/zh-CN/docs/Glossary/HTTP_header)

#### URI 和 URL

URI 是统一资源标识符，它在于能够唯一标识。URL 是统一资源定位符，它在于能够定位资源路径。

可见，URL 是 URI 的子集，URI 只在于能够唯一标识，如身份证。

### HTTP 演进

HTTP 的发展经历了多个版本：HTTP/0.9、HTTP/1.0、HTTP/1.1、HTTP/2、HTTP/3。

#### HTTP 0.9

最初版本的HTTP协议并没有版本号，后来它的版本号被定位在 0.9 以区分后来的版本。 HTTP /0.9 极其简单：请求由单行指令构成，以唯一可用方法 GET 开头，其后跟目标资源的路径（一旦连接到服务器，协议、服务器、端口号这些都不是必须的）。

```HTML
 GET /index.html
```

响应也极其简单的：只包含响应文档本身。

```HTML
<HTML>
    这是一个 HTML 页面
</HTML>
```

跟后来的版本不同，HTTP /0.9 的响应内容并不包含 HTTP 头，这意味着只有 HTML 文件可以传送，无法传输其他类型的文件；也没有状态码或错误代码：一旦出现问题，一个特殊的包含问题描述信息的 HTML 文件将被发回，供人们查看。

#### HTTP 1.0

由于 HTTP /0.9 协议的应用十分有限，浏览器和服务器迅速扩展内容使其用途更广：

- 协议版本信息现在会随着每个请求发送（HTTP /1.0 被追加到了 GET 行）。
- 状态码会在响应开始时发送，使浏览器能了解请求执行成功或失败，并相应调整行为（如更新或使用本地缓存）。
- 引入了 HTTP 头的概念，无论是对于请求还是响应，允许传输元数据，使协议变得非常灵活，更具扩展性。
- 在新 HTTP 头的帮助下，具备了传输除纯文本 HTML 文件以外其他类型文档的能力。

一个典型的请求看起来就像这样：

```HTML
GET /mypage.html HTTP/1.0
User-Agent: NCSA_Mosaic/2.0 (Windows 3.1)

200 OK
Date: Tue, 15 Nov 1994 08:12:31 GMT
Server: CERN/3.0 libwww/2.17
Content-Type: text/html
<HTML>
    一个包含图片的页面
     <IMG SRC="/myimage.gif">
</HTML>
```

接下来是第二个连接，请求获取图片：

```HTML
GET /myimage.gif HTTP/1.0
User-Agent: NCSA_Mosaic/2.0 (Windows 3.1)

200 OK
Date: Tue, 15 Nov 1994 08:12:32 GMT
Server: CERN/3.0 libwww/2.17
Content-Type: text/gif
(这里是图片内容)    
```

在 1991-1995 年，这些新扩展并没有被引入到标准中以促进协助工作，而仅仅作为一种尝试：服务器和浏览器添加这些新扩展功能，但出现了大量的互操作问题。直到 1996 年 11 月，为了解决这些问题，
一份新文档（RFC 1945）被发表出来，用以描述如何操作实践这些新扩展功能。文档 RFC 1945 定义了 HTTP /1.0，但它是狭义的，并不是官方标准。

#### HTTP 1.1

HTTP /1.0 多种不同的实现方式在实际运用中显得有些混乱，自 1995 年开始， 即HTTP /1.0 文档发布的下一年，就开始修订 HTTP 的第一个标准化版本。在 1997 年初，HTTP /1.1 标准发布，就在 HTTP /1.0 发布的几个月后。

HTTP /1.1 消除了大量歧义内容并引入了多项改进：

- 连接可以复用，节省了多次打开 TCP 连接加载网页文档资源的时间。
- 增加管线化技术，允许在第一个应答被完全发送之前就发送第二个请求，以降低通信延迟。
- 支持响应分块。
- 引入额外的缓存控制机制。
- 引入内容协商机制，包括语言，编码，类型等，并允许客户端和服务器之间约定以最合适的内容进行交换。
- 引入 Host 头，能够使不同域名配置在同一个 IP 地址的服务器上。

一个典型的请求流程， 所有请求都通过一个连接实现，看起来就像这样：

```HTML
GET /en-US/docs/Glossary/Simple_header HTTP/1.1
Host: developer.mozilla.org
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10.9; rv:50.0) Gecko/20100101 Firefox/50.0
Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
Accept-Language: en-US,en;q=0.5
Accept-Encoding: gzip, deflate, br
Referer: https://developer.mozilla.org/en-US/docs/Glossary/Simple_header

200 OK
Connection: Keep-Alive
Content-Encoding: gzip
Content-Type: text/html; charset=utf-8
Date: Wed, 20 Jul 2016 10:55:30 GMT
Etag: "547fa7e369ef56031dd3bff2ace9fc0832eb251a"
Keep-Alive: timeout=5, max=1000
Last-Modified: Tue, 19 Jul 2016 00:59:33 GMT
Server: Apache
Transfer-Encoding: chunked
Vary: Cookie, Accept-Encoding

(content)


GET /static/img/header-background.png HTTP/1.1
Host: developer.mozilla.org
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10.9; rv:50.0) Gecko/20100101 Firefox/50.0
Accept: */*
Accept-Language: en-US,en;q=0.5
Accept-Encoding: gzip, deflate, br
Referer: https://developer.mozilla.org/en-US/docs/Glossary/Simple_header

200 OK
Age: 9578461
Cache-Control: public, max-age=315360000
Connection: keep-alive
Content-Length: 3077
Content-Type: image/png
Date: Thu, 31 Mar 2016 13:34:46 GMT
Last-Modified: Wed, 21 Oct 2015 18:27:50 GMT
Server: Apache

(image content of 3077 bytes)
```

HTTP /1.1 在 1997 年 1 月以 RFC 2068 文件发布。

#### HTTP 2

这些年来，网页愈渐变得的复杂，甚至演变成了独有的应用，可见媒体的播放量，增进交互的脚本大小也增加了许多：更多的数据通过 HTTP 请求被传输。HTTP /1.1 链接需要请求以正确的顺序发送，
理论上可以用一些并行的链接（尤其是 5 到 8 个），带来的成本和复杂性堪忧。比如，HTTP 管线化（pipelining）就成为了 Web 开发的负担。

在 2010 年到 2015 年，谷歌通过实践了一个实验性的 SPDY 协议，证明了一个在客户端和服务器端交换数据的另类方式。其收集了浏览器和服务器端的开发者的焦点问题。明确了响应数量的增加和解决复杂的数据传输，
SPDY 成为了 HTTP /2 协议的基础。

HTTP /2 和 HTTP /1.1 有几处基本的不同:

- HTTP /2 是二进制协议而不是文本协议。不再可读，也不可无障碍的手动创建，改善的优化技术现在可被实施。
- 这是一个复用协议。并行的请求能在同一个链接中处理，移除了 HTTP/1.x 中顺序和阻塞的约束。
- 压缩了 headers，因为  headers 在一系列请求中常常是相似的，其移除了重复和传输重复数据的成本。
- 其允许服务器在客户端缓存中填充数据，通过一个叫服务器推送的机制来提前请求。

随着 HTTP /2 的发布，就像先前的 HTTP /1.x 一样，HTTP 没有停止进化，HTTP 的扩展性依然被用来添加新的功能。

- 对 Alt-Svc 的支持允许了给定资源的位置和资源鉴定，允许了更智能的 CDN 缓冲机制。
- Client-Hints  的引入允许浏览器或者客户端来主动交流它的需求，或者是硬件约束的信息给服务端。
- 在 Cookie 头中引入安全相关的的前缀，现在帮助保证一个安全的cookie没被更改过。

#### HTTPS

一般的 HTTP 通信具有以下问题：

- 通信明文传输，容易被窃听截取。
- 未校验数据完整性，容易被篡改。
- 没有验证对方身份，存在伪装风险。。

为了解决上述 HTTP 存在的问题，产生了 HTTPS。HTTPS不是一种新的协议，只是协议的组合HTTPS 协议（HyperText Transfer Protocol over Secure Socket Layer）：一般理解为 HTTP + SSL/TLS，
通过 SSL  证书来验证服务器的身份，并为浏览器和服务器之间的通信进行加密。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/HTTP和HTTPS协议结构.png" width="600px">
</div>

SSL（Secure Socket Layer，安全套接字层）协议位于 TCP/IP 协议与各种应用层协议之间，为数据通讯提供安全支持。TLS（Transport Layer Security，传输层安全）的前身是 SSL，大致与 SSL 类似。

HTTPS 在一定程度上保证了通信安全，但也产生了一些问题：

- HTTPS 协议需要七次握手，导致页面的加载时间延长近 50%；
- HTTPS 连接缓存不如 HTTP 高效，会增加数据开销和功耗；
- SSL 证书需要金钱成本，功能越强大的证书费用越高。
- SSL 安全算法消耗 CPU 资源，增加服务器压力。

#### HTTP 3

HTTP/3 是基于 QUIC 协议，QUIC 协议是 Google 提出的一套开源协议，是基于 UDP 来实现。

QUIC 协议层实现了可靠的数据传输，拥塞控制，加密，多路数据流，相当于将不可靠传输的 UDP 协议变成了 "可靠 " 的协议，因此不用担心数据包丢失的问题。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/HTTP2和HTTP3协议结构.png" width="600px">
</div>

我们先了解一下 RTT，RTT 是 Round Trip Time 的缩写，通俗地说，就是通信一来一回的时间。

如 TCP 需要三次握手，建立了TCP虚拟通道，这样 `一去（SYN）`>`二回（SYN+ACK）`>`三去（ACK）`,故 TCP 建立连接需要 1.5 个 RTT。HTTP 建立连接后，开始进行数据传输，`一去（HTTP Request）`>`二回 （HTTP Responses）`，故 HTTP 数据传输需要 1 个 RTT。那么 `HTTP 通信时间总和 = TCP 连接时间 + HTTP 数据传输时间 = 2.5 RTT`。
HTTPS 相比 HTTP 多出来一个 TLS（TLS 1.2） 连接时间，TCP 和 TLS 是分层的，需要分批次来握手，先 TCP 握手，再 TLS 握手，TLS 握手需要 2 RTT，其中 TCP "三去" 可以携带 TLS 信息，节省了 0.5 RTT，这意味着 `HTTPS 通信时间总和 = TCP 连接时间 + SSL 连接时间 + HTTP 数据传输时间 = 4 RTT`。

相比它们，QUIC 是基于 UDP 协议的，UDP 不需要连接，不会带来附加的 RTT 时间。

QUIC 协议并不与 TLS 分层，而是 QUIC 内部包含了 TLS，它在自己的帧会携带 TLS 的 "记录"，并且 QUIC 使用的 TLS 1.3 仅需 1 个 RTT 就可以完成建立连接与密钥协商，此外，完成 QUIC 连接的 Session ID 会缓存在浏览器内存里，如果用户再次打开该页面，无需建立 TLS 连接，直接使用缓存 Session ID 对应的加密参数，
服务器可以根据 Session ID 在缓存里查找对应的加密参数，并完成加密。这意味着，重连 TLS 连接是一个 0 RTT 事件，`用户所要等待的页面加载事件 = HTTP 数据传输时间 = 1 RTT`。

HTTP /3 本质不是对 HTTP 协议本身的改进，它主要是集中在如何提高传输效率。

- HTTP /3 使用 stream 进一步扩展了 HTTP /2 的多路复用。在 HTTP /3 模式下，一般传输多少个文件就会产生对应数量的  stream。当这些文件中的其中一个发生丢包时，你只需要重传丢包文件的对应 stream 即可。
- HTTP /3 不再是基于 TCP 建立的，而是通过 UDP 建立，在用户空间保证传输的可靠性，相比 TCP，UDP 之上的 QUIC 协议提高了连接建立的速度，降低了延迟。
- 通过引入 Connection ID，使得 HTTP /3 支持连接迁移以及 NAT 的重绑定。
- HTTP /3 含有一个包括验证、加密、数据及负载的 built-in 的 TLS 安全机制。
- 拥塞控制。TCP 是在内核区实现的，而 HTTP /3 将拥塞控制移出了内核，通过用户空间来实现。这样做的好处就是不再需要等待内核更新可以实现很方便的进行快速迭代。
- 头部压缩。HTTP /2 使用的 HPACK，HTTP /3 更换成了兼容 HPACK 的 QPACK 压缩方案。QPACK 优化了对乱序发送的支持，也优化了压缩率。

### 特性和应用

#### 短连接与长连接

当浏览器访问一个包含多张图片的 HTML 页面时，除了请求访问的 HTML 页面资源，还会请求图片资源。如果每进行一次 HTTP 通信就要新建一个 TCP 连接，那么开销会很大。

长连接只需要建立一次 TCP 连接就能进行多次 HTTP 通信。

- 从 HTTP /1.1 开始默认是长连接的，如果要断开连接，需要由客户端或者服务器端提出断开，使用 `Connection : close`。
- 在 HTTP /1.1 之前默认是短连接的，如果需要使用长连接，则使用 `Connection : Keep-Alive`。

#### 流水线

默认情况下，HTTP 请求是按顺序发出的，下一个请求只有在当前请求收到响应之后才会被发出。由于受到网络延迟和带宽的限制，在下一个请求被发送到服务器之前，可能需要等待很长时间。

流水线是在同一条长连接上连续发出请求，而不用等待响应返回，这样可以减少延迟。

#### Cookie

HTTP 协议是无状态的，主要是为了让 HTTP 协议尽可能简单，使得它能够处理大量事务。HTTP /1.1 引入 Cookie 来保存状态信息。

Cookie 是服务器发送到用户浏览器并保存在本地的一小块数据，它会在浏览器之后向同一服务器再次发起请求时被携带上，用于告知服务端两个请求是否来自同一浏览器。由于之后每次请求都会需要携带 Cookie 数据，因此会带来额外的性能开销（尤其是在移动环境下）。

Cookie 曾一度用于客户端数据的存储，因为当时并没有其它合适的存储办法而作为唯一的存储手段，但现在随着现代浏览器开始支持各种各样的存储方式，Cookie 渐渐被淘汰。新的浏览器 API 已经允许开发者直接将数据存储到本地，如使用 Web storage API（本地存储和会话存储）或 IndexedDB。

#### 缓存

缓存通常位于内存中，读取缓存的速度更快。并且缓存服务器在地理位置上也有可能比源服务器来得近，甚至是浏览器缓存。

#### 内容编码

内容编码将实体主体进行压缩，从而减少传输的数据量。

常用的内容编码有：gzip、compress、deflate、identity。

浏览器发送 Accept-Encoding 首部，其中包含有它所支持的压缩算法，以及各自的优先级。服务器则从中选择一种，使用该算法对响应的消息主体进行压缩，并且发送 Content-Encoding 首部来告知浏览器它选择了哪一种算法。由于该内容协商过程是基于编码类型来选择资源的展现形式的，响应报文的 Vary 首部字段至少要包含 Content-Encoding。

#### 虚拟主机

HTTP /1.1 引入 Host 头，能够使不同域名配置在同一个 IP 地址的服务器上，即虚拟主机技术，使得一台服务器拥有多个域名，并且在逻辑上可以看成多个服务器。

#### 隧道

使用 SSL 等加密手段，在客户端和服务器之间建立一条安全的通信线路。

### 通信安全

信息安全主要包括以下五方面的内容，即保证信息的保密性、真实性、完整性、未授权拷贝和所寄生系统的安全性。

加密技术是最常用的安全保密手段，利用技术手段把重要的数据变为乱码（加密）传送，到达目的地后再用相同或不同的手段还原（解密）。

#### 加密技术

加密技术主要分为三种：对称加密、非对称加密和单向加密。

- 对称加密，也叫共享密钥，顾名思义，这种加密方式用相同的密钥进行加密和解密。
- 非对称加密，又称公开密钥加密，它的思路就是把加密密钥和解密密钥分开，而且加密算法是可逆的(公钥加密，私钥解密；私钥加密，公钥解密)。
- 单向加密，又称为不可逆加密算法，在加密过程中不使用密钥，明文由系统加密处理成密文，密文无法解密。

优秀的对称加密算法，拥有巨大的密钥空间，基本无法暴力破解，加密过程相对快速，但是加密和解密用同一个密钥，发送方必须把密钥发送给接收方，当窃取者窃取密文、密钥，对称加密将失去安全保障。

非对称加密，发送方只需要把公钥传送给对方，接收方用公钥加密数据并传输给发送方，发送方用私钥解密获取密文，但是非对称加密的运算速度要比对称加密慢很多的，传输大量数据时，一般不会用公钥直接加密数据，而是通过非对称加密将对称密钥加密，传输给对方，然后双方使用对称加密传输数据。

严格意义上单向加密并不是加密算法，而是一种摘要算法，用于验证数据的完整性，如 MD5、SHA1。如果一种算法是加密算法，就应该不仅能够加密数据，而且应该能够还原数据。

#### 数字签名

数字签名就是使用非对称加密的私钥对数据摘要进行加密并发布。通过公钥可以解密数字签名，他的意义不是为了保密，而且为了证明身份，证明这些数据确实是由你本人发出的。

加密数据仅仅是一个签名，签名应该和数据一同发出，具体流程应该是：

- Bob 生成公钥和私钥，然后把公钥公布出去，私钥自己保留。
- Bob 用私钥加密数据作为签名，然后将数据附带着签名一同发布出去。
- Alice 收到数据和签名，需要检查此份数据是否是 Bob 所发出，于是用 Bob 之前发出的公钥尝试解密签名，将收到的数据和签名解密后的结果作对比，如果完全相同，说明数据没被篡改，且确实由 Bob 发出。

有意思的来了，数字签名就是验证对方身份的一种方式，但是前提是对方的身份必须是真的。这似乎陷入死循环，要想确定对方的身份，必须有一个信任的源头，否则的话，再多的流程也只是在转移问题，而不是真正解决问题。

#### 数字证书

CA 是 CertificateAuthority 的缩写，也叫 "证书授权中心"。它是负责管理和签发证书的第三方机构，作用是检查证书持有者身份的合法性，并签发证书，以防证书被伪造或篡改。CA 证书其实就是`公钥 + 签名`，引入可信任的第三方，是终结信任循环的一种可行方案。

证书认证的流程大致如下：

- Bob 去可信任的认证机构证实本人真实身份，并提供自己的公钥。
- Alice 想跟 Bob 通信，首先向认证机构请求 Bob 的公钥，认证机构会把一张证书（Bob 的公钥以及机构对其公钥的签名）发送给 Alice。
- Alice 检查签名，确定该公钥确实由这家认证机构发送，中途未被篡改。
- Alice 通过这个公钥加密数据，开始和 Bob 通信。

CA 证书很广泛，SSL/TLS 是 CA 证书中的一种。SSL/TLS 建立连接需要四次握手。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/TLS四次握手.png" width="600px">
</div>

客户端发出请求:

- 首先，客户端先向服务器发出加密通信的请求，这被叫做 ClientHello 请求。在这一步，客户端主要向服务器提供以下信息。
    1. 支持的协议版本，比如 TLS 1.2 版。
    2. 一个客户端生成的随机数，稍后用于生成 "对话密钥"。
    3. 支持的加密方法，比如RSA公钥加密。
    4. 支持的压缩方法。

服务器回应:

- 服务器收到客户端请求后，向客户端发出回应，这叫做 SeverHello。服务器的回应包含以下内容。
    1. 确认使用的加密通信协议版本，比如 TLS 1.2 版本。如果浏览器与服务器支持的版本不一致，服务器关闭加密通信。
    2. 一个服务器生成的随机数，稍后用于生成"对话密钥"。
    3. 确认使用的加密方法，比如 RSA 公钥加密，返回加密公钥
    4. 服务器证书

客户端回应:

- 验证证书的合法性（颁发证书的机构是否合法，证书中包含的网站地址是否与正在访问的地址一致等），如果证书受信任，则浏览器栏里面会显示一个小锁头，否则会给出证书不受信的提示。
- 如果证书受信任，或者是用户接受了不受信的证书，浏览器会生成一串随机数的密码，并用证书中提供的公钥加密。
- 使用约定好的 HASH 计算握手消息，并使用生成的随机数对消息进行加密，最后将之前生成的所有信息发送给网站。

服务器回应:

- 使用自己的私钥将信息解密取出密码，使用密码解密浏览器发来的握手消息，并验证 HASH 是否与浏览器发来的一致。
- 使用密码加密一段握手消息，发送给浏览器。

#### HTTPS 通信安全

HTTPS 的整体过程分为证书验证和数据传输阶段，HTTPS 在内容传输的加密上使用的是对称加密，非对称加密只作用在证书验证阶段具体的交互过程如下:

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/HTTPS建立连接.png" width="600px">
</div>

证书验证阶段：

- 浏览器发起 HTTPS 请求。
- 服务端返回 CA 证书。
- 客户端验证证书是否合法，如果不合法则提示告警。

数据传输阶段：

- 当证书验证合法后，在本地生成随机数。
- 通过公钥加密随机数，并把加密后的随机数传输到服务端。
- 服务端通过私钥对随机数进行解密。
- 服务端通过客户端传入的随机数构造对称加密算法，对返回结果内容进行加密后传输。

和 HTTPS 相比，HTTP /3 基于 QUIC 协议，QUIC 协议并不与 TLS 分层，而是 QUIC 内部包含了 TLS，这在保障通信安全的基础上，还减少了 RTT，加快响应速度。

### 攻击技术

攻击模式主要分为两种：主动攻击和被动攻击。

- 主动攻击

主动攻击是具有攻击破坏性的一种攻击行为，攻击者是在主动地做一些不利于系统的事情，会造成直接的影响。

主动攻击按照攻击方法不同，可分为中断、篡改和伪造。中断是指截获由原站发送的数据，将有效数据中断，使目的站点无法接收到原站发送的数据；篡改是指将原站发送的目的站点的数据进行篡改，从而影响目的站点所受的的信息；伪造是指在原站未发送数据的情况下，伪造数据发送的目的站点，从而影响目标站点。

- 被动攻击

被动攻击中攻击者不对数据信息做任何修改，截取/窃听是指在未经用户同意和认可的情况下攻击者获得了信息或相关数据。通常包括窃听、流量分析、破解弱加密的数据流等攻击方式。

#### 输入输出引发的缺陷

##### 跨站脚本攻击

跨站脚本攻击（Cross-Site Scripting， XSS）是指通过在用户的浏览器内运行非法的 HTML 标签或 JavaScript 向存在安全漏洞的 Web 网站进行的一种攻击。

常见的 XSS 攻击比如虚假输入表单骗取用户个人信息、窃取用户 Cookie 发送恶意请求 等。

常见的预防 XSS 攻击的手段比如对 HTML 标签、JavaScript 进行转义处理、禁止 JavaScript 读取 Cookie 等。

##### SQL 注入

SQL注入（SQL Injection） 是指针对 Web 应用使用的数据库，通过运行非法的 SQL 而产生的攻击。简单点来说，就是通过表单输入的内容，诱使服务器拼接成一个非法的 SQL。比如有一个正常的 SQL 语句如下：

```sql
SELECT * FROM user WHERE name= '张三' and password = '1'
```

注入后：

```sql
SELECT * FROM user WHERE name= '张三' -- and password = '1'
```

常见的预防 SQL 注入的手段就是 SQL 语句预编译处理。

##### OS 注入

OS 命令注入攻击（OS Command Injection）是指通过 Web 应用，执行非法的操作系统命令达到攻击的目的。OS 命令注入攻击可以向 Shell 发送命令，让 Windows 或 Linux 操作系统的命令行启动程序。也就是说，通过 OS 注入攻击可执行 OS 上安装着的各种程序。

OS 命令注入和 SQL 注入类似，SQL 注入伪造的是非法 SQL，OS 命令注入伪造的是非法 shell 命令。

常见的预防 OS 注入的手段是对 shell 执行的符号进行转码替换（比如 &&、&、| 等）。

##### HTTP 首部注入

HTTP 首部注入攻击（HTTP Header Injection）是指攻击者通过在响应首部字段内插入换行，添加任意响应首部或主体的一种攻击。比如重定向至任意的 URL、替换掉要返回的主体内容等。

比如存在某个需要重定向的页面，本来的 header 信息是这个样子的：

```html
Location: http://example.com/?cat=101
```

因为重定向需要带回参数，攻击者就诱使用户在参数中加入攻击代码 —— 加入或替换任意的 header 信息。（下面这个 Location 可能不会生效，不同的浏览器对重复的 header 字段有不同的处理方式）

```html
Location: http://example.com/?cat=101（%0D%0A：换行符）
Location: http://xxx.com
```

常见的预防 header 注入攻击的方式就是添加 SSL/TLS 认证，也就是启用 HTTPS。

##### 目录遍历攻击

目录遍历攻击是指对本无意公开的文件目录，通过非法截断其目录路径或者通过 `../` 操作来回溯目录，达成访问目的的一种攻击。

预防目录遍历攻击一般需要在应用中对目录跳转符、dir 符号等进行过滤，服务器应该对不应开发的目录限制权限。

##### 远程文件包含漏洞

是指当部分脚本内容需要从其他文件读入时，攻击者利用指定外部服务器的URL充当依赖文件，让脚本读取之后，就可运行任意脚本的一种攻击。

这主要是PHP存在的安全漏洞，对 PHP 的 include 或 require 来说，通过指定外部服务器的 URL 作为文件名的功能。PHP 5.2.0 之后默认设定此功能无效。

#### 设置或设计引发的缺陷

##### 不正确的消息提示

不正确的消息提示是指 Web 应用的错误信息内包含对攻击者有用的信息，与 Web 应用有关的主要错误信息如下所示：

- Web 应用抛出的错误信息
- 数据库等系统抛出的错误信息

Web 应用不应该在用户的浏览页面上展现详细的错误信息，对攻击者来说，详细的错误消息有可能给它们下一次攻击以提示。

##### 开放重定向

开发重定向（Open Redirect）是一种对指定的任意URL作重定向跳转的功能，如指定重定向 URL 到某个具有恶意的 Web 网站。

#### 会话管理引发的缺陷

##### 会话劫持

会话劫持（Session Hijack）攻击是指攻击者通过某种手段拿到了用户的会话 ID，并非法使用此会话 ID 伪装成用户，达到攻击的目的。

常见的预防会话劫持的手段比如：将会话 ID 和用户设备信息绑定在一起，当用户在其他设备上使用该会话 ID 时，就会提示被盗用风险，要求用户重新登录。

##### 会话固定攻击

对以窃取目标会话 ID 为主动攻击手段的会话劫持而言，会话固定攻击（Session Fixation）攻击会强制用户使用攻击者指定的会话 ID，属于被动攻击。案例步骤：

- 攻击者访问需要认证的页面
- 服务器发布一个未认证状态的会话 ID（http://xxx/login?SID=xxx）
- 攻击者将上面未认证的会话 ID URL 作为陷阱，诱导用户前去认证，认证后，会话 ID 变为已认证状态
- 攻击者即可访问网站

##### 跨站点请求伪造

跨站点请求伪造（Cross-Site Request Forgeries，CSRF）攻击是指攻击者通过设置好的陷阱，强制对已完成认证的用户进行非预期的个人信息或设定信息等某些状态更新。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/跨站请求伪造示意图.png" width="400px">
</div>

常见的预防 CSRF 攻击的手段比如：验证 Referer + POST 提交、增加 token 认证等。

#### 其他缺陷

##### 密码破解

密码破解（Password Cracking）即算出密码，突破认证。攻击不限于 Web 应用，还包括其他的系统（如 FTP 或 SSH 等），密码破解有以下两种手段：

- 通过网络的密码试错（穷举法，字典攻击）
- 对已加密密码的破解（指攻击者入侵系统，以获取加密或散列处理的密码数据的情况）

除了突破认证的攻击手段，还有 SQL 注入攻击逃避认证，跨站脚本攻击窃取密码信息等方法。

##### 点击劫持

点击劫持是指通过透明的按钮或者连接做成的陷阱，覆盖在 Web 页面之上，诱使用户点击。这种行为又称界面伪装，当用户点击到透明按钮时，实际上点击率已经指定了透明元素的 iframe 页面。

##### DOS 攻击

DoS 攻击（Denial of Service attack）是一种让运行中的服务呈停止状态的攻击。有时也叫做服务停止攻击或拒绝服务攻击。DoS 攻击的对象不仅限于 Web 网站，还包括网络设备及服务器等。

Dos 攻击简单点理解就是发送大量的合法请求，造成服务器资源过载耗尽，从而使服务器停止服务。（由于服务器很难分辨何为正常请求，何为攻击请求，因此很难防止 DoS 攻击。）

Dos 攻击还可通过攻击安全漏洞使服务停止。

##### 后门程序

后面程序是指开发设置的隐藏入口，可不按正常步骤使用受限功能。

可通过监视进程的通信状态发现被植入的后门程序，但往往 Web 应用中的后面程序和正常程序没有明显区别，通常很难被发现。