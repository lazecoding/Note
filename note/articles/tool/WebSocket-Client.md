### WebSocket 客户端

基于浏览器的 WebSocket 客户端。

源码稳健地址：[WebSocket-Client.html](https://github.com/lazecoding/Note/blob/main/note/sourcecode/tool/WebSocket-Client.html)

- WebSocket 客户端源码：

```javascript
<!DOCTYPE html>
<html lang="en">

<head>
    <!-- @author lazecoding -->
    <meta charset="UTF-8">
    <title>WebSocket 客户端</title>
</head>

<body>
<script type="text/javascript">
    var socket;

    // 建立连接
    function content() {
        //如果浏览器支持WebSocket
        if (window.WebSocket) {

            var url = document.getElementById("wsurl").value;

            // 如果链接已经开启，就不创建新的连接
            if (socket != undefined && socket != null && socket.readyState == WebSocket.OPEN) {
                alert("连接已经开启，不要重复创建");
                return;
            }

            //参数就是与服务器连接的地址
            socket = new WebSocket(url);

            //客户端收到服务器消息的时候就会执行这个回调方法
            socket.onmessage = function (event) {
                var ta = document.getElementById("responseText");
                ta.value = ta.value + "\n" + event.data;
            }

            //连接建立的回调函数
            socket.onopen = function (event) {
                var ta = document.getElementById("responseText");
                ta.value = ta.value + "\n" + "连接开启";
            }

            //连接断掉的回调函数
            socket.onclose = function (event) {
                var ta = document.getElementById("responseText");
                ta.value = ta.value + "\n" + "连接关闭";
            }

            //连接异常的回调函数
            socket.onerror = function (event) {
                var ta = document.getElementById("responseText");
                ta.value = ta.value + "\n" + "连接异常";
            }
        } else {
            alert("浏览器不支持WebSocket！");
        }
    }

    // 建立连接
    function closed() {
        //如果浏览器支持WebSocket
        if (window.WebSocket) {
            //参数就是与服务器连接的地址
            socket.close();
            alert("连接已断开");
        } else {
            alert("浏览器不支持WebSocket！");
        }
    }


    //发送数据
    function send(message) {
        if (!window.WebSocket) {
            return;
        }
        console.log(socket);
        //当websocket状态打开
        if (socket != undefined && socket != null && socket.readyState == WebSocket.OPEN) {
            socket.send(message);
        } else {
            alert("连接没有开启");
        }
    }
</script>


<form onsubmit="return false">

    <h3>连接管理：</h3>
    <input type="text" id="wsurl" value="ws://localhost:9911/chat">
    <button onclick="content();">建立链接</button>
    <button onclick="closed();">断开链接</button>

    <h3>客户端输入：</h3>
    <textarea name="message" style="width: 400px;height: 200px"></textarea>
    <input type="button" value="发送数据" onclick="send(this.form.message.value);">

    <h3>服务器输出：</h3>
    <textarea id="responseText" style="width: 400px;height: 300px;"></textarea>
    <input type="button" onclick="javascript:document.getElementById('responseText').value=''" value="清空数据">

</form>
</body>

</html>
```