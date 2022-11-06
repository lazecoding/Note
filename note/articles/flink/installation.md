# 安装

运行 Flink 需要安装 Java 7.x 或更高的版本，操作系统需要 windows 7 或更高版本。

### 下载

下载地址: [https://flink.apache.org/zh/downloads.html](https://flink.apache.org/zh/downloads.html) 。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/flink/Flink下载.png" width="600px">
</div>

### 运行启动脚本

新版没有 bat 文件，我们需要手动创建。

start-cluster.bat：

```C
@echo off
setlocal EnableDelayedExpansion
 
SET bin=%~dp0
SET FLINK_HOME=%bin%..
SET FLINK_LIB_DIR=%FLINK_HOME%\lib
SET FLINK_PLUGINS_DIR=%FLINK_HOME%\plugins
SET FLINK_CONF_DIR=%FLINK_HOME%\conf
SET FLINK_LOG_DIR=%FLINK_HOME%\log
 
SET JVM_ARGS=-Xms1024m -Xmx1024m
 
SET FLINK_CLASSPATH=%FLINK_LIB_DIR%\*
 
SET logname_jm=flink-%username%-jobmanager.log
SET logname_tm=flink-%username%-taskmanager.log
SET log_jm=%FLINK_LOG_DIR%\%logname_jm%
SET log_tm=%FLINK_LOG_DIR%\%logname_tm%
SET outname_jm=flink-%username%-jobmanager.out
SET outname_tm=flink-%username%-taskmanager.out
SET out_jm=%FLINK_LOG_DIR%\%outname_jm%
SET out_tm=%FLINK_LOG_DIR%\%outname_tm%
 
SET log_setting_jm=-Dlog.file="%log_jm%" -Dlogback.configurationFile=file:"%FLINK_CONF_DIR%/logback.xml" -Dlog4j.configuration=file:"%FLINK_CONF_DIR%/log4j.properties"
SET log_setting_tm=-Dlog.file="%log_tm%" -Dlogback.configurationFile=file:"%FLINK_CONF_DIR%/logback.xml" -Dlog4j.configuration=file:"%FLINK_CONF_DIR%/log4j.properties"
 
:: Log rotation (quick and dirty)
CD "%FLINK_LOG_DIR%"
for /l %%x in (5, -1, 1) do ( 
SET /A y = %%x+1 
RENAME "%logname_jm%.%%x" "%logname_jm%.!y!" 2> nul
RENAME "%logname_tm%.%%x" "%logname_tm%.!y!" 2> nul
RENAME "%outname_jm%.%%x" "%outname_jm%.!y!"  2> nul
RENAME "%outname_tm%.%%x" "%outname_tm%.!y!"  2> nul
)
RENAME "%logname_jm%" "%logname_jm%.0"  2> nul
RENAME "%logname_tm%" "%logname_tm%.0"  2> nul
RENAME "%outname_jm%" "%outname_jm%.0"  2> nul
RENAME "%outname_tm%" "%outname_tm%.0"  2> nul
DEL "%logname_jm%.6"  2> nul
DEL "%logname_tm%.6"  2> nul
DEL "%outname_jm%.6"  2> nul
DEL "%outname_tm%.6"  2> nul
for %%X in (java.exe) do (set FOUND=%%~$PATH:X)
if not defined FOUND (
    echo java.exe was not found in PATH variable
    goto :eof
)
echo Starting a local cluster with one JobManager process and one TaskManager process.
echo You can terminate the processes via CTRL-C in the spawned shell windows.
echo Web interface by default on http://localhost:8081/.
start java %JVM_ARGS% %log_setting_jm% -cp "%FLINK_CLASSPATH%"; org.apache.flink.runtime.entrypoint.StandaloneSessionClusterEntrypoint --configDir "%FLINK_CONF_DIR%" > "%out_jm%" 2>&1
start java %JVM_ARGS% %log_setting_tm% -cp "%FLINK_CLASSPATH%"; org.apache.flink.runtime.taskexecutor.TaskManagerRunner --configDir "%FLINK_CONF_DIR%" > "%out_tm%" 2>&1
endlocal
```

flink.bat：

```C
@echo off
setlocal
 
SET bin=%~dp0
SET FLINK_HOME=%bin%..
SET FLINK_LIB_DIR=%FLINK_HOME%\lib
SET FLINK_PLUGINS_DIR=%FLINK_HOME%\plugins
 
SET JVM_ARGS=-Xmx512m
 
SET FLINK_JM_CLASSPATH=%FLINK_LIB_DIR%\*
 
java %JVM_ARGS% -cp "%FLINK_JM_CLASSPATH%"; org.apache.flink.client.cli.CliFrontend %*
 
endlocal
```

运行 `start-cluster.bat` 即可。

### Flink UI

Flink UI：[http://localhost:8081/](http://localhost:8081/) 。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/flink/FlinkUI.png" width="600px">
</div>
