# 代码提交规范

对于 Git 的提交日志，我们有非常明确而详细的提交规范。这将有助于我们在查看项目历史时，更容易明确每一次提交的内容。另一方面，我们还直接使用了Git提交日志来生成变更日志。

### 提交消息格式

每个提交消息都由一个标题、一个正文和一个页脚组成。而标题又具有特殊格式，包括修改类型、影响范围和内容主题：

```C
修改类型(影响范围): 标题
<--空行-->
[正文]
<--空行-->
[页脚]
```

标题是 `强制性` 的，但标题的范围是 `可选` 的。

提交消息的任何一行都不能超过 100 个字符！这是为了让消息在 GitHub 以及各种 Git 工具中都更容易阅读。

### 修改类型

每个类型值都表示了不同的含义，类型值必须是以下的其中一个：

- feat：提交新功能。
- fix：修复了bug。
- log：添加日志（临时排查问题的日志）。
- tool：提交工具代码。
- docs：只修改了文档。
- style：调整代码格式，未修改代码逻辑（比如修改空格、格式化、缺少分号等）。
- refactor：代码重构，既没修复bug也没有添加新功能。
- perf：性能优化，提高性能的代码更改。
- test：添加或修改代码测试。
- chore：对构建流程或辅助工具和依赖库（如文档生成等）的更改。

### 代码回滚

代码回滚比较特殊，如果本次提交是为了恢复到之前的某个提交，那提交消息应该以 "revert:" 开头，后跟要恢复到的那个提交的标题。然后在消息正文中，应该写上 "This reverts commit <hash>"，其中 "<hash>" 是要还原的那个提交的 SHA 值。

### 影响范围

范围不是固定值，它可以是你提交代码实际影响到的任何内容。例如 $location、$browser、$compile、$rootScope、ngHref、ngClick、ngView 等，唯一需要注意的是它必须足够简短。

当修改影响多个范围时，也可以使用 "*"。

### 标题

标题是对变更的简明描述：

- 使用祈使句，现在时态：是 "change" 不是 "changed" 也不是 "changes"。
- 不要大写首字母。
- 结尾不要使用句号。

### 正文

正文是对标题的补充，但它不是必须的。和标题一样，它也要求使用祈使句且现在时态，正文应该包含更详细的信息，如代码修改的动机，与修改前的代码对比等。

### 页脚

任何 Breaking Changes（破坏性变更，不向下兼容）都应该在页脚中进行说明，它经常也用来引用本次提交解决的 GitHub Issue。

Breaking Changes 应该以 "BREAKING CHANGE:" 开头，然后紧跟一个空格或两个换行符，其他要求与前面一致。

