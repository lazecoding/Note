# 异步下载

它的核心是 fileTag 和 filePath，fileTag 提供给前端定位到这个文件，filePath 是真实路径，也就是之后要下载文件的地址。

注意点：

- 在开始组织文件前，先初始化文件状态，之后异步地组织文件，为了主线程将 fileTag 返回给前端。
- 在文件组织完毕，更新文件状态为已完成，前端开始下载文件。
- 为了防止文件没有主动下载，提供延迟队列删除文件。

前端代码：

```javascript
$.ajax({
    url: "下载请求",
    type: "get",
    async: false,
    success: function (result) {
        if (result.success) {
            var fileTag;
            fileTag = result.code;
            checkFileState(fileTag);
        }
    }
});


// 检查删除状态并下载文件
function checkFileState(fileTag) {
    var fileTag = fileTag;
    $.ajax({
        url: contextPath + '/manager/filedownload/filestate_check.do',
        type: "get",
        data: {fileTag: fileTag},
        async: false,
        success: function (result) {
            if (result.success) {
                // 开始下载
                iframeSubmit(contextPath + '/manager/filedownload/file_download.do?fileTag=' + fileTag);
            } else {
                // 轮询
                if(result.code == 0){
                    setTimeout(function(){checkFileState(fileTag)}, 500);
                } else {
                    alert(result.message);
                }
            }
        }
    });
}
```

后端源码：

看 `./sourcecode` 文件夹下源码。