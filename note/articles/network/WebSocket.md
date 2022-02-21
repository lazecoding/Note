# WebSocket

- 目录
    - [WebSocket 和 HTTP 和 TCP](#WebSocket-和-HTTP-和-TCP)
      - [HTTP 轮询](#HTTP-轮询)
      - [WebSocket 全双工通信](#WebSocket-全双工通信)
      - [优劣](#优劣)
    - [工作原理](#工作原理)
      - [建立连接](#建立连接)
      - [数据通信](#数据通信)
        - [数据帧格式](#数据帧格式)
        - [数据分片](#数据分片)
      - [断开连接](#断开连接)
    - [补充](#补充)
      - [心跳](#心跳)
      - [Sec-WebSocket-Key 和 Sec-WebSocket-Accept 的作用](#Sec-WebSocket-Key-和-Sec-WebSocket-Accept-的作用)
      - [WebSocket 掩码](#WebSocke-掩码)
      - [状态码](#状态码)

WebSocket 协议是基于 TCP 的一种新的网络协议。它实现了浏览器与服务器全双工(full-duplex)通信——允许服务器主动发送信息给客户端。WebSocket 通信协议于 2011 年被 IETF 定为标准 RFC 6455，并被 RFC7936 所补充规范。

在 WebSocket 出现之前，客户端为了获取最近的数据一般是通过固定时间间隔来轮询请求，大部分情况下数据状态并未发生改变，反而白白浪费了带宽和服务器资源。在这种环境下，WebSocket 应运而生，它实现了客户端与服务器全双工通信，能更好的节省服务器和带宽资源并达到实时通讯的目的。

WebSocket 协议主要为了解决基于 HTTP/1.x 的 Web 应用无法实现服务端向客户端主动推送的问题, 为了兼容现有的设施, WebSocket 协议使用与 HTTP 协议相同的端口, 并使用 HTTP Upgrade 机制来进行 WebSocket 握手, 当握手完成之后, 通信双方便可以按照 WebSocket 协议的方式进行交互。

WebSocket 使用 TCP 作为传输层协议, 与 HTTP 类似, WebSocket 也支持在 TCP 上层引入 TLS 层, 以建立加密数据传输通道, 即 WebSocket over TLS, WebSocket 的 URI 与 HTTP URI 的结构类似, 对于使用 80 端口的 WebSocket over TCP, 其 URI 的一般形式为 `ws://host:port/path/query` 对于使用 443 端口的 WebSocket over TLS, 其 URI 的一般形式为 `wss://host:port/path/query`。

在 WebSocket 协议中, 帧 (frame) 是通信双方数据传输的基本单元, 与其它网络协议相同, frame 由 Header 和 Payload 两部分构成, frame 有多种类型, frame 的类型由其头部的 Opcode 字段 (将在下面讨论) 来指示, WebSocket 的 frame 可以分为两类, 一类是用于传输控制信息的 frame (如通知对方关闭 WebSocket 连接), 一类是用于传输应用数据的 frame, 使用 WebSocket 协议通信的双方都需要首先进行握手, 只有当握手成功之后才开始使用 frame 传输数据。

### WebSocket 和 HTTP 和 TCP

WebSocket 与 HTTP 协议都是基于 TCP 协议的，因此它们都是可靠协议，调用的 WebSocket 的 send 函数最终都是通过 TCP 的系统接口进行传输的。WebSocket 和 HTTP 协议都属于应用层的协议，WebSocket 在建立握手连接时，数据是通过 HTTP 协议传输的，但是在建立连接之后，真正的数据传输阶段是不需要 HTTP 协议参与的。具体关系如下图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/WebSocket和HTTP和TCP的关系.png" width="400px">
</div>

#### HTTP 轮询

HTTP 实现实时推送用到的轮询，轮询分两种：长轮询和短轮询（传统轮询）。

**短轮询**：浏览器定时向服务器发送请求，服务器收到请求不管是否有数据到达都直接响应 请求，隔特定时间，浏览器又会发送相同的请求到服务器， 获取数据响应，如图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/HTTP短轮询.png" width="600px">
</div>

缺点：数据交互的实时性较低，服务端到浏览器端的数据反馈效率低。

**长轮询**：浏览器发起请求到服务器，服务器一直保持连接打开，直到有数据可发送。发送完数据之后，浏览器关闭连接，随即又发起一个到服务器的新请求。这一过程在页面打开期间一直持续不断。如图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/HTTP长轮询.png" width="600px">
</div>

缺点：服务器没有数据到达时，HTTP 连接会停留一段时间，造成服务器资源浪费，数据交互的实时性也很低。

> 无论是长轮询还是短轮询，客户端都要先建立和服务器的连接，才能接收数据，并且实时交互性很低。

#### WebSocket 全双工通信

WebSocket 的出现解决了轮询实时交互性和全双工的问题。

当客户端想要使用 WebSocket 协议与服务端进行通信时, 首先需要确定服务端是否支持 WebSocket 协议, 因此 WebSocket 协议的第一步是进行握手。WebSocket 握手采用 HTTP Upgrade 机制，若服务端支持 WebSocket 协议, 并同意与客户端握手, 则应返回 101 的 HTTP 状态码, 表示同意协议升级, 同时应设置 Upgrade 字段并将值设置为 websocket, 并将 Connection 字段的值设置为 Upgrade, 这些都是与标准 HTTP Upgrade 机制完全相同的。客户端在发起握手后必须处于阻塞状态, 换句话说, 客户端必须等待服务端发回响应，客户端对服务端返回的握手响应做校验, 若校验成功, 则 WebSocket 握手成功, 之后双方就可以开始进行双向的数据传输。

一旦 WebSocket 连接建立后，后续数据都以帧序列的形式传输。在客户端断开 WebSocket 连接或 Server 端中断连接前，不需要客户端和服务端重新发起连接请求。在海量并发及客户端与服务器交互负载流量大的情况下，极大的节省了网络带宽资源的消耗，有明显的性能优势，且客户端发送和接受消息是在同一个持久连接上发起，实时性优势明显。

> 当 WebSocket 建立成功，客户端和服务端其实处于一个对等状态。

#### 优劣

相比 HTTP 协议，WebSocket 优点概括地说就是：支持双向通信，更灵活，更高效，可扩展性更好。

- 支持双向通信，实时性更强。
- 更好的二进制支持。
- 较少的控制开销。连接创建后，WebSocket 客户端、服务端进行数据交换时，协议控制的数据包头部较小。在不包含头部的情况下，服务端到客户端的包头只有 2~10 字节（取决于数据包长度），客户端到服务端的的话，需要加上额外的 4 字节的掩码。而 HTTP 协议每次通信都需要携带完整的头部。
- 支持扩展。WebSocket 协议定义了扩展，用户可以扩展协议，或者实现自定义的子协议。（比如支持自定义压缩算法等）。

WebSocket 的缺点也是存在的，最直接的是对开发人员的要求较高，但这并不是最重要的。我们已经知道，使用 WebSocket 代替 HTTP 可以更轻松地获取实时数据，那么为什么我们不把所有 HTTP 接口都替换成 WebSocket 呢？

的确，WebSocket 避免了客户端轮询，较少了网络带宽消耗，但是并不一定减少了服务器压力。因为，既然 WebSocket 连接建立后可以保持不断开，服务端必然做了特别的处理来维护 WebSocket 连接的生命周期。而 HTTP 是用完即返回的，服务器并不需要维护 HTTP 连接的生命周期。

### 工作原理

一个 WebSocket 连接的生命周期包含三个阶段：建立连接、数据通信、断开连接。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/WebSocket连接的生命周期.png" width="600px">
</div>

> WebSocket 协议不受同源策略影响。

#### 建立连接

当客户端想要使用 WebSocket 协议与服务端进行通信时, 首先需要确定服务端是否支持 WebSocket 协议, 因此 WebSocket 协议的第一步是进行握手, WebSocket 握手采用 HTTP Upgrade 机制发起握手 (WebSocket 握手只允许使用 HTTP GET 方法)。

请求/响应报文：

```C
Handshake Details
    Request URL: ws://127.0.0.1:9999/live?LT=4d5c99e9bd144affb30477ff918772bf
    Request Method: GET
    Status Code: 101 Switching Protocols
Request Headers
    Accept-Encoding: gzip, deflate, br
    Accept-Language: zh-CN,zh;q=0.9
    Cache-Control: no-cache
    Host: 127.0.0.1:9999
    Origin: null
    Pragma: no-cache
    Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits
    Sec-WebSocket-Key: bIgH3aElRssJBCC07fqFgA==
    Sec-WebSocket-Version: 13
    Connection: Upgrade
    Upgrade: websocket
    User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36
Response Headers
    connection: upgrade
    upgrade: websocket
    sec-websocket-accept: 4jX8e0VOsBeIGpBq7Vc3R+eB/R4=
    sec-websocket-extensions: permessage-deflate
```

Handshake Details 字段：

- `Request Method: GET`：WebSocket 握手只允许使用 HTTP GET 方法。
- `HTTP/1.1 101 Switching Protocols`：服务端返回内容如下，状态代码 101 表示协议切换。到此完成协议升级，后续的数据交互都按照新的协议来。

Request Headers 字段：

- `Connection: Upgrade`：表示要升级协议。
- `Upgrade: websocket`：表示要升级到 websocket 协议。
- `Sec-WebSocket-Version: 13`：表示 websocket 的版本。如果服务端不支持该版本，需要返回一个 `Sec-WebSocket-Version` header，里面包含服务端支持的版本号。
- `Sec-WebSocket-Key: bIgH3aElRssJBCC07fqFgA==`：与后面服务端响应首部的 `Sec-WebSocket-Accept` 是配套的，提供基本的防护，比如恶意的连接，或者无意的连接。

> 由于是标准的 HTTP 请求，类似 Host、Origin、Cookie 等请求首部会照常发送。在握手阶段，可以通过相关请求首部进行安全限制、权限校验等。

Response Headers 字段：

- `Connection: Upgrade`：表示要升级协议。
- `Upgrade: websocket`：表示要升级到 websocket 协议。
- `sec-websocket-accept: 4jX8e0VOsBeIGpBq7Vc3R+eB/R4=`：`Sec-WebSocket-Accept` 根据客户端请求首部的 `Sec-WebSocket-Key` 计算出来（将 `Sec-WebSocket-Key` 跟 `258EAFA5-E914-47DA-95CA-C5AB0DC85B11` 拼接，通过 SHA1 计算出摘要，并转成 base64 字符串）。

当客户端想要使用 WebSocket 协议与服务端进行通信时, 首先需要确定服务端是否支持 WebSocket 协议, 因此 WebSocket 协议的第一步是进行握手。WebSocket 握手采用 HTTP Upgrade 机制，若服务端支持 WebSocket 协议, 并同意与客户端握手, 则应返回 101 的 HTTP 状态码, 表示同意协议升级, 同时应设置 Upgrade 字段并将值设置为 websocket, 并将 Connection 字段的值设置为 Upgrade, 这些都是与标准 HTTP Upgrade 机制完全相同的。客户端在发起握手后必须处于阻塞状态, 换句话说, 客户端必须等待服务端发回响应，客户端对服务端返回的握手响应做校验, 若校验成功, 则 WebSocket 握手成功, 之后双方就可以开始进行双向的数据传输。

#### 数据通信

客户端、服务端数据的交换，离不开数据帧格式的定义。因此，在实际讲解数据交换之前，我们先来看下 WebSocket 的数据帧格式。

#### 数据帧格式

WebSocket 以 frame 为单位传输数据, frame 是客户端和服务端数据传输的最小单元, 当一条消息过长时, 通信方可以将该消息拆分成多个 frame 发送, 接收方收到以后重新拼接、解码从而还原出完整的消息, 在 WebSocket 中, frame 有多种类型, frame 的类型由 frame 头部的 Opcode 字段指示, WebSocket frame 的结构如下所示:

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/network/WebSocket-Frame结构.png" width="600px">
</div>

该结构的字段语义如下:

- FIN, 长度为 1 比特, 该标志位用于指示当前的 frame 是消息的最后一个分段, 因为 WebSocket 支持将长消息切分为若干个 frame 发送, 切分以后, 除了最后一个 frame, 前面的 frame 的 FIN 字段都为 0, 最后一个 frame 的 FIN 字段为 1, 当然, 若消息没有分段, 那么一个 frame 便包含了完成的消息, 此时其 FIN 字段值为 1。
- RSV 1 ~ 3, 这三个字段为保留字段, 只有在 WebSocket 扩展时用, 若不启用扩展, 则该三个字段应置为 1, 若接收方收到 RSV 1 ~ 3 不全为 0 的 frame, 并且双方没有协商使用 WebSocket 协议扩展, 则接收方应立即终止 WebSocket 连接。
- Opcode, 长度为 4 比特, 该字段将指示 frame 的类型, RFC 6455 定义的 Opcode 共有如下几种:

    - 0x0, 代表当前是一个 continuation frame。
    - 0x1, 代表当前是一个 text frame。
    - 0x2, 代表当前是一个 binary frame。
    - 0x3 ~ 7, 目前保留, 以后将用作更多的非控制类 frame。
    - 0x8, 代表当前是一个 connection close, 用于关闭 WebSocket 连接。
    - 0x9, 代表当前是一个 ping frame (将在下面讨论)。
    - 0xA, 代表当前是一个 pong frame (将在下面讨论)。
    - 0xB ~ F, 目前保留, 以后将用作更多的控制类 frame。

- Mask, 长度为 1 比特, 该字段是一个标志位, 用于指示 frame 的数据 (Payload) 是否使用掩码掩盖, RFC 6455 规定当且仅当由客户端向服务端发送的 frame, 需要使用掩码覆盖, 掩码覆盖主要为了解决代理缓存污染攻击 (更多细节见 [RFC 6455 Section 10.3](https://datatracker.ietf.org/doc/html/rfc6455#section-10.3) )。
- Payload Len, 以字节为单位指示 frame Payload 的长度, 该字段的长度可变, 可能为 7 比特, 也可能为 7 + 16 比特, 也可能为 7 + 64 比特. 具体来说, 当 Payload 的实际长度在 [0, 125] 时, 则 Payload Len 字段的长度为 7 比特, 它的值直接代表了 Payload 的实际长度; 当 Payload 的实际长度为 126 时, 则 Payload Len 后跟随的 16 位将被解释为 16-bit 的无符号整数, 该整数的值指示 Payload 的实际长度; 当 Payload 的实际长度为 127 时, 其后的 64 比特将被解释为 64-bit 的无符号整数, 该整数的值指示 Payload 的实际长度。
- Masking-key, 该字段为可选字段, 当 Mask 标志位为 1 时, 代表这是一个掩码覆盖的 frame, 此时 Masking-key 字段存在, 其长度为 32 位, RFC 6455 规定所有由客户端发往服务端的 frame 都必须使用掩码覆盖, 即对于所有由客户端发往服务端的 frame, 该字段都必须存在, 该字段的值是由客户端使用熵值足够大的随机数发生器生成, 关于掩码覆盖, 将下面讨论, 若 Mask 标识位 0, 则 frame 中将设置该字段 (注意是不设置该字段, 而不仅仅是不给该字段赋值)。
- Payload, 该字段的长度是任意的, 该字段即为 frame 的数据部分, 若通信双方协商使用了 WebSocket 扩展, 则该扩展数据 (Extension data) 也将存放在此处, 扩展数据 + 应用数据, 它们的长度和便为 Payload Len 字段指示的值。

##### 数据分片

一旦 WebSocket 客户端、服务端建立连接后，后续的操作都是基于数据帧的传递。WebSocket 根据 opcode 来区分操作的类型。比如 0x8 表示断开连接，0x0、0x2 表示数据交互格式。

当要发送的一条消息过长或者消息是实时产生并不能预测具体的长度时, 客户端可将消息进行分片, 构成一个 frame 后便可以发往服务端, 分片的另一个考虑是为了复用底层的 TCP 连接, 当客户端有多份相互独立的数据需要发送时, 消息分片可以实现在一条 TCP 链路上的复用, 多份数据可以并发地发往服务端。

消息分片主要利用 frame Header 的 FIN 和 Opcode 字段来实现。

- 对于未分片的消息, 一个 frame 便承载了完整的消息, 此时它没有后续的 frame, 因此其 FIN 字段为 1, Opcode 根据该消息是文本消息还是二进制消息分别选择 0x1 或 0x2。
- 对于分片的消息, 我们以文本消息为例, 文本消息的 Opcode 为 0x1, 若不进行分片, 则 frame 的 FIN 字段为 1, 同时 Opcode 字段为 0x1, 若进行分片, 则第一个分片的 frame 的 FIN 字段为 0, Opcode 为 0x1, 但从第二个直到倒数第二个分片, 其 FIN 字段为 0, 并且 Opcode 字段的值为 0x0 (0x0 代表这是一个 continuation frame), 对于最后一个分片的消息, 其 FIN 字段为 1, 并且 Opcode 字段的值为 0x1, 对于分片消息, 发送端必须按序发送, 因此 TCP 保证交付给上层的数据是有序的, 因此接收端也将按发送端发送的顺序收到消息, 它可以按序拼接分片得到完整的消息。


示例：

**第一条消息**

- `FIN=1`, 表示是当前消息的最后一个数据帧，第一条消息不需要分片。服务端收到当前数据帧后，可以处理消息。`opcode=0x1`，表示客户端发送的是文本类型，`msg="hello"`。

**第二条消息**

- `FIN=0`，`opcode=0x1`，表示发送的是文本类型，且消息还没发送完成，还有后续的数据帧。
- `FIN=0`，`opcode=0x0`，表示消息还没发送完成，还有后续的数据帧，当前的数据帧需要接在上一条数据帧之后。
- `FIN=1`，`opcode=0x0`，表示消息已经发送完成，没有后续的数据帧，当前的数据帧需要接在上一条数据帧之后。服务端可以将关联的数据帧组装成完整的消息。

```C
Client: FIN=1, opcode=0x1, msg="hello"
Server: (process complete message immediately) Hi.
Client: FIN=0, opcode=0x1, msg="and a"
Server: (listening, new message containing text started)
Client: FIN=0, opcode=0x0, msg="happy new"
Server: (listening, payload concatenated to previous message)
Client: FIN=1, opcode=0x0, msg="year!"
Server: (process complete message) Happy new year to you too!
```

#### 断开连接

RFC 6455 将连接关闭表述为 Closing Handshake, 我们通常表述为挥手, 以便与建立连接的握手区分开, WebSocket 的连接关闭分为 CLOSING 和 CLOSED 两个阶段, 当发送完 Close frame 或接收到对方发来的 Close frame 后, WebSocket 连接便从 OPEN 状态转变为 CLOSING 状态, 此时可以称挥手已启动, 通信方接收到 Close frame 后应立即向对方发回 Close frame, 并关闭底层 TCP 连接, 此时 WebSocket 连接处于 CLOSED 状态。

### 补充

#### 心跳

WebSocket为了保持客户端、服务端的实时双向通信，需要确保客户端、服务端之间的TCP通道保持连接没有断开。然而，对于长时间没有数据往来的连接，如果依旧长时间保持着，可能会浪费包括的连接资源。

但不排除有些场景，客户端、服务端虽然长时间没有数据往来，但仍需要保持连接。这个时候，可以采用心跳来实现。

- 发送方->接收方：ping。
- 接收方->发送方：pong。

ping、pong 的操作，对应的是 WebSocket 的两个控制帧（Ping frame、Pong frame），opcode分别是 0x9、0xA。

> 但是实际开发中，我们也经常会通过自定义程序来实现心跳，由服务端自主控制。

#### Sec-WebSocket-Key 和 Sec-WebSocket-Accept 的作用

前面提到了，`Sec-WebSocket-Key` 和 `Sec-WebSocket-Accept` 的主要作用在于提供基础的防护，减少恶意连接、意外连接。

作用大致归纳如下：

- 避免服务端收到非法的 WebSocket 连接（比如 HTTP 客户端不小心请求连接 WebSocket 服务，此时服务端可以直接拒绝连接）。
- 确保服务端理解 WebSocket 连接。因为 ws 握手阶段采用的是 HTTP 协议，因此可能 ws 连接是被一个 HTTP 服务器处理并返回的，此时客户端可以通过 `Sec-WebSocket-Key` 来确保服务端认识ws协议。（并非百分百保险，比如总是存在那么些无聊的 HTTP 服务器，光处理 `Sec-WebSocket-Key`，但并没有实现 ws 协议）。
- 用浏览器里发起 ajax 请求，设置 header 时，`Sec-WebSocket-Key` 以及其他相关的 header 是被禁止的。这样可以避免客户端发送 ajax 请求时，意外请求协议升级（WebSocket Upgrade）。
- 可以防止反向代理（不理解 ws 协议）返回错误的数据。比如反向代理前后收到两次 ws 连接的升级请求，反向代理把第一次请求的返回给 cache 住，然后第二次请求到来时直接把 cache 住的请求给返回（无意义的返回）。
- `Sec-WebSocket-Key` 主要目的并不是确保数据的安全性，因为 `Sec-WebSocket-Key`/`Sec-WebSocket-Accept` 的转换计算公式是公开的，而且非常简单，最主要的作用是预防一些常见的意外情况（非故意的）。

> Sec-WebSocket-Key/Sec-WebSocket-Accept 的换算，只能带来基本的保障，但连接是否安全、数据是否安全、客户端/服务端是否合法的，其实并没有实际性的保证。

#### WebSocket 掩码

WebSocket协议中，数据掩码的作用是增强协议的安全性。但数据掩码并不是为了保护数据本身，因为算法本身是公开的，运算也不复杂。除了加密通道本身，似乎没有太多有效的保护通信安全的办法。


但并不是为了防止数据泄密，而是为了防止早期版本的协议中存在的代理缓存污染攻击（proxy cache poisoning attacks）等问题。

RFC 6455 规定所有由客户端发往服务端的 WebSocket frame 的 Payload 部分都必须使用掩码覆盖, 但数据掩码并不是为了保护数据本身，因为算法本身是公开的，运算也不复杂，它并不是为了防止数据泄密，而是为了避免代理缓存污染攻击 (更多细节见 [RFC 6455 Section 10.3](https://datatracker.ietf.org/doc/html/rfc6455#section-10.3) ), 若服务端接收到没有使用掩码覆盖的 frame, 服务端应立即终止 WebSocket 连接, 掩码覆盖只针对 frame 的 Payload 部分, 掩码覆盖不会改变 Payload 的长度, 掩码覆盖的算法如下:

- 客户端使用熵值足够高的随机数生成器随机生成 32 比特的 Masking-Key。
- 以字节为步长遍历 Payload, 对于 Payload 的第 i 个字节, 首先做 i MOD 4 得到 j, 则掩码覆盖后的 Payload 的第 i 个字节的值为原先 Payload 第 i 个字节与 Masking-Key 的第 j 个字节做按位异或操作。

我们以 original-octet-i 表示未覆盖前的 Payload 的第 i 个字节, 以 transformed-octet-i 表示覆盖后的 Payload 的第 i 个字节, 以 masking-key-octet-j 表示 Masking-Key 的第 j 个字节, 那么上述算法的操作可以用如下两个式子表示:

```C
j                   = i MOD 4
transformed-octet-i = original-octet-i XOR masking-key-octet-j
```

服务端收到客户端的 frame 后, 首先检查 Mask 标志位是否为 1, 若不是则应立即终止握手, 然后根据 Masking-Key 字段的值重复上述操作便可以得到原先的 Payload 数据。

#### 状态码

连接成功状态码：

- 101：HTTP协议切换为WebSocket协议。

连接关闭状态码：

- 1000：正常断开连接。
- 1001：服务器断开连接。
- 1002：websocket 协议错误。
- 1003：客户端接受了不支持数据格式（只允许接受文本消息，不允许接受二进制数据，是客户端限制不接受二进制数据，而不是websocket协议不支持二进制数据）。
- 1006：异常关闭。
- 1007：客户端接受了无效数据格式（文本消息编码不是utf-8）。
- 1009：传输数据量过大。
- 1010：客户端终止连接。
- 1011：服务器终止连接。
- 1012：服务端正在重新启动。
- 1013：服务端临时终止。
- 1014：通过网关或代理请求服务器，服务器无法及时响应。
- 1015：TLS握手失败。
- ...

更多状态码查看 [RFC 6455 Section 7.4.1](https://datatracker.ietf.org/doc/html/rfc6455#section-7.4.1) 。

### 参考文档
 
- [Writing WebSocket servers](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_servers) 。