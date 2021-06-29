# HTTP

- 目录
    - [起源](#起源)
    - [HTTP 协议](#HTTP-协议)
        - [HTTP 报文](#HTTP-报文)
        - [HTTP 方法](#HTTP-方法)
        - [HTTP 状态码](#HTTP-状态码)

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

- `报文`：是 HTTP 通信的基本单位，由 8 为字节流组成，通过 HTTP 通信传输。
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
