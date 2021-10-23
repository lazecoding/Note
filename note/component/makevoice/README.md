# 语音播报

文字转语音，并将返回的音频文件显示在页面上。

效果源码：

```javascript
<div>
  <audio controls id="${id}">
    <source src="" type="audio/mpeg">
  </audio>
  <script type="text/javascript">
    $.ajax({
      url: '获取语音文件的请求接口',
      type: 'GET',
      success: function (path) {
        console.log('path:' + path);
        var obj = document.getElementById("${id}");
        obj.src = path;
      }
    })
  </script>
</div>
```