# 启动流程

- 目录
    - [启动脚本](#启动脚本)
    - [入口](#入口)

ElasticSearch 的启动流程实质上是一个 Node 的启动流程，包括配置解析、环境检查、初始化内部资源、启动子模块等流程。

### 启动脚本

启动 ElasticSearch  可以使用脚本 `bin/elasticsearch`。

bin/elasticsearch：

```C
#!/bin/bash

# CONTROLLING STARTUP:
#
# This script relies on a few environment variables to determine startup
# behavior, those variables are:
#
#   ES_PATH_CONF -- Path to config directory
#   ES_JAVA_OPTS -- External Java Opts on top of the defaults set
#
# Optionally, exact memory values can be set using the `ES_JAVA_OPTS`. Note that
# the Xms and Xmx lines in the JVM options file must be commented out. Example
# values are "512m", and "10g".
#
#   ES_JAVA_OPTS="-Xms8g -Xmx8g" ./bin/elasticsearch

source "`dirname "$0"`"/elasticsearch-env

if [ -z "$ES_TMPDIR" ]; then
  ES_TMPDIR=`"$JAVA" -cp "$ES_CLASSPATH" org.elasticsearch.tools.launchers.TempDirectory`
fi

ES_JVM_OPTIONS="$ES_PATH_CONF"/jvm.options
ES_JAVA_OPTS=`export ES_TMPDIR; "$JAVA" -cp "$ES_CLASSPATH" org.elasticsearch.tools.launchers.JvmOptionsParser "$ES_JVM_OPTIONS"`

# manual parsing to find out, if process should be detached
if ! echo $* | grep -E '(^-d |-d$| -d |--daemonize$|--daemonize )' > /dev/null; then
  exec \
    "$JAVA" \
    $ES_JAVA_OPTS \
    -Des.path.home="$ES_HOME" \
    -Des.path.conf="$ES_PATH_CONF" \
    -Des.distribution.flavor="$ES_DISTRIBUTION_FLAVOR" \
    -Des.distribution.type="$ES_DISTRIBUTION_TYPE" \
    -Des.bundled_jdk="$ES_BUNDLED_JDK" \
    -cp "$ES_CLASSPATH" \
    org.elasticsearch.bootstrap.Elasticsearch \
    "$@"
else
  exec \
    "$JAVA" \
    $ES_JAVA_OPTS \
    -Des.path.home="$ES_HOME" \
    -Des.path.conf="$ES_PATH_CONF" \
    -Des.distribution.flavor="$ES_DISTRIBUTION_FLAVOR" \
    -Des.distribution.type="$ES_DISTRIBUTION_TYPE" \
    -Des.bundled_jdk="$ES_BUNDLED_JDK" \
    -cp "$ES_CLASSPATH" \
    org.elasticsearch.bootstrap.Elasticsearch \
    "$@" \
    <&- &
  retval=$?
  pid=$!
  [ $retval -eq 0 ] || exit $retval
  if [ ! -z "$ES_STARTUP_SLEEP_TIME" ]; then
    sleep $ES_STARTUP_SLEEP_TIME
  fi
  if ! ps -p $pid > /dev/null ; then
    exit 1
  fi
  exit 0
fi

exit $?
```

bin/elasticsearch 主要设置了 JVM 环境属性，最终调用 ElasticSearch 的 main 函数,即 ElasticSearch 的入口。

### 入口

ElasticSearch 的入口位于 `org/elasticsearch/bootstrap/Elasticsearch.java#main`。

ElasticSearch 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/es/Elasticsearch类图.png" width="400px">
</div>

- 构造函数：

```java
// org/elasticsearch/bootstrap/Elasticsearch.java#Elasticsearch
Elasticsearch() {
    super("starts elasticsearch", () -> {}); // we configure logging later so we override the base class from configuring logging
    // --V,--version: 打印版本信息
    versionOption = parser.acceptsAll(Arrays.asList("V", "version"),
        "Prints elasticsearch version information and exits");
    // --d,--daemonize: 后台启动
    daemonizeOption = parser.acceptsAll(Arrays.asList("d", "daemonize"),
        "Starts Elasticsearch in the background")
        .availableUnless(versionOption);
    // --p，--pidfile:启动时在指定路径创建一个 Pid 文件，报错当前进程的 Pid
    pidfileOption = parser.acceptsAll(Arrays.asList("p", "pidfile"),
        "Creates a pid file in the specified path on start")
        .availableUnless(versionOption)
        .withRequiredArg()
        .withValuesConvertedBy(new PathConverter());
    // --q，--quiet:
    quietOption = parser.acceptsAll(Arrays.asList("q", "quiet"),
        "Turns off standard output/error streams logging in console")
        .availableUnless(versionOption)
        .availableUnless(daemonizeOption);
}

// org/elasticsearch/cli/EnvironmentAwareCommand.java#EnvironmentAwareCommand
public EnvironmentAwareCommand(final String description, final Runnable beforeMain) {
    super(description, beforeMain);
    // 设置某项属性
    this.settingOption = parser.accepts("E", "Configure a setting").withRequiredArg().ofType(KeyValuePair.class);
}
```

- 入口函数：

```java
/**
 * elasticsearch 启动入口
 */
// org/elasticsearch/bootstrap/Elasticsearch.java#main
public static void main(final String[] args) throws Exception {
    // 覆盖 DNS 缓存测策略
    overrideDnsCachePolicyProperties();
    /*
     * We want the JVM to think there is a security manager installed so that if internal policy decisions that would be based on the
     * presence of a security manager or lack thereof act as if there is a security manager present (e.g., DNS cache policy). This
     * forces such policies to take effect immediately.
     */
    System.setSecurityManager(new SecurityManager() {

        @Override
        public void checkPermission(Permission perm) {
            // grant all permissions so that we can later set the security manager to the one that we want
        }

    });
    // 注册错误监听器（启动之初就开始注册，为了不遗漏错误日志）
    LogConfigurator.registerErrorListener();
    // 创建 Elasticsearch 实例，处理命令行参数
    final Elasticsearch elasticsearch = new Elasticsearch();
    // 入口
    int status = main(args, elasticsearch, Terminal.DEFAULT);
    if (status != ExitCodes.OK) {
        // 关闭
        exit(status);
    }
}
```

入口函数创建了 Elasticsearch 实例，在构造函数中处理了命令行参数，之后调用 main 方法（重载的方法，非入口函数），执行启动流程。

- Command#main

```java
/**
 * 从 args 中解析该命令的选项并执行它
 */
// org/elasticsearch/cli/Command.java#main
public final int main(String[] args, Terminal terminal) throws Exception {
    // 是否添加钩子：在 JVM 关闭前清理资源
    if (addShutdownHook()) {

        shutdownHookThread = new Thread(() -> {
            try {
                this.close();
            } catch (final IOException e) {
                try (
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw)) {
                    e.printStackTrace(pw);
                    terminal.errorPrintln(sw.toString());
                } catch (final IOException impossible) {
                    // StringWriter#close declares a checked IOException from the Closeable interface but the Javadocs for StringWriter
                    // say that an exception here is impossible
                    throw new AssertionError(impossible);
                }
            }
        });
        // 添加钩子：在 JVM 关闭前清理资源
        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
    }

    beforeMain.run();

    try {
        // Executes the command, but all errors are thrown.
        // 执行命令，但抛出所有错误。
        mainWithoutErrorHandling(args, terminal);
    } catch (OptionException e) {
        // print help to stderr on exceptions
        printHelp(terminal, true);
        terminal.errorPrintln(Terminal.Verbosity.SILENT, "ERROR: " + e.getMessage());
        return ExitCodes.USAGE;
    } catch (UserException e) {
        if (e.exitCode == ExitCodes.USAGE) {
            printHelp(terminal, true);
        }
        terminal.errorPrintln(Terminal.Verbosity.SILENT, "ERROR: " + e.getMessage());
        return e.exitCode;
    }
    return ExitCodes.OK;
}
```

Command 是 Elasticsearch 父类的父类，Elasticsearch 入口函数会调用到继承父类的方法 `Command#main`。该方法中 mainWithoutErrorHandling 负责执行命令，在此之间添加钩子函数用于在 JVM 退出之前清理资源。

- Command#mainWithoutErrorHandling

```java
/**
 * 执行命令，但抛出所有错误。
 */
// org/elasticsearch/cli/Command.java#mainWithoutErrorHandling
void mainWithoutErrorHandling(String[] args, Terminal terminal) throws Exception {
    // 解析 args
    final OptionSet options = parser.parse(args);

    if (options.has(helpOption)) {
        printHelp(terminal, false);
        return;
    }

    if (options.has(silentOption)) {
        terminal.setVerbosity(Terminal.Verbosity.SILENT);
    } else if (options.has(verboseOption)) {
        terminal.setVerbosity(Terminal.Verbosity.VERBOSE);
    } else {
        terminal.setVerbosity(Terminal.Verbosity.NORMAL);
    }

    execute(terminal, options);
}
```

mainWithoutErrorHandling 方法首先将参数解析成 OptionSet 对象并做一定加工，再调用 execute 方法执行业务。

- EnvironmentAwareCommand#execute

```java
// org/elasticsearch/cli/EnvironmentAwareCommand.java#execute
@Override
protected void execute(Terminal terminal, OptionSet options) throws Exception {
    final Map<String, String> settings = new HashMap<>();
    for (final KeyValuePair kvp : settingOption.values(options)) {
        if (kvp.value.isEmpty()) {
            throw new UserException(ExitCodes.USAGE, "setting [" + kvp.key + "] must not be empty");
        }
        if (settings.containsKey(kvp.key)) {
            final String message = String.format(
                Locale.ROOT,
                "setting [%s] already set, saw [%s] and [%s]",
                kvp.key,
                settings.get(kvp.key),
                kvp.value);
            throw new UserException(ExitCodes.USAGE, message);
        }
        settings.put(kvp.key, kvp.value);
    }

    // 确保给定设置存在，如果没有设置，则从系统属性中读取。
    putSystemPropertyIfSettingIsMissing(settings, "path.data", "es.path.data");
    putSystemPropertyIfSettingIsMissing(settings, "path.home", "es.path.home");
    putSystemPropertyIfSettingIsMissing(settings, "path.logs", "es.path.logs");

    // createEnv 创建 Environment
    execute(terminal, options, createEnv(settings));
}
```

接着调用的 execute 方法继承自 EnvironmentAwareCommand 抽象类。该方法对 options 进一步解析，初始化在 settings 变量中，这是一个 Map；接着对 `es.path.data`、`es.path.home` 和 `es.path.logs` 属性配置做校验；
最终执行 `execute(terminal, options, createEnv(settings));`。

- createEnv

```java
// org/elasticsearch/cli/EnvironmentAwareCommand.java#createEnv
protected final Environment createEnv(final Settings baseSettings, final Map<String, String> settings) throws UserException {
    final String esPathConf = System.getProperty("es.path.conf");
    if (esPathConf == null) {
        throw new UserException(ExitCodes.CONFIG, "the system property [es.path.conf] must be set");
    }
    return InternalSettingsPreparer.prepareEnvironment(baseSettings, settings,
        getConfigPath(esPathConf),
        // HOSTNAME is set by elasticsearch-env and elasticsearch-env.bat so it is always available
        () -> System.getenv("HOSTNAME"));
}

// org/elasticsearch/node/InternalSettingsPreparer.java#prepareEnvironment
public static Environment prepareEnvironment(Settings input, Map<String, String> properties,
        Path configPath, Supplier<String> defaultNodeName) {
    // just create enough settings to build the environment, to get the config dir
    Settings.Builder output = Settings.builder();
    initializeSettings(output, input, properties);
    Environment environment = new Environment(output.build(), configPath);

    if (Files.exists(environment.configFile().resolve("elasticsearch.yaml"))) {
        throw new SettingsException("elasticsearch.yaml was deprecated in 5.5.0 and must be renamed to elasticsearch.yml");
    }

    if (Files.exists(environment.configFile().resolve("elasticsearch.json"))) {
        throw new SettingsException("elasticsearch.json was deprecated in 5.5.0 and must be converted to elasticsearch.yml");
    }

    output = Settings.builder(); // start with a fresh output
    Path path = environment.configFile().resolve("elasticsearch.yml");
    if (Files.exists(path)) {
        try {
            output.loadFromPath(path);
        } catch (IOException e) {
            throw new SettingsException("Failed to load settings from " + path.toString(), e);
        }
    }

    // re-initialize settings now that the config file has been loaded
    initializeSettings(output, input, properties);
    checkSettingsForTerminalDeprecation(output);
    finalizeSettings(output, defaultNodeName);

    return new Environment(output.build(), configPath);
}
```

在执行 `execute(terminal, options, createEnv(settings));` 时，会先执行 `createEnv(settings)`。顾名思义，创建环境对象。createEnv 方法校验 `es.path.conf` 属性后调用 prepareEnvironment 方法，
做 `new Environment(output.build(), configPath)` 前的准备工作。

实例化 Environment 对象主要就是对配置属性的注入。

- Elasticsearch#execute

```java
@Override
// org/elasticsearch/bootstrap/Elasticsearch.java#execute
protected void execute(Terminal terminal, OptionSet options, Environment env) throws UserException {
    if (options.nonOptionArguments().isEmpty() == false) {
        throw new UserException(ExitCodes.USAGE, "Positional arguments not allowed, found " + options.nonOptionArguments());
    }
    // JVM 版本信息
    if (options.has(versionOption)) {
        final String versionOutput = String.format(
            Locale.ROOT,
            "Version: %s, Build: %s/%s/%s/%s, JVM: %s",
            Build.CURRENT.getQualifiedVersion(),
            Build.CURRENT.flavor().displayName(),
            Build.CURRENT.type().displayName(),
            Build.CURRENT.hash(),
            Build.CURRENT.date(),
            JvmInfo.jvmInfo().version()
        );
        terminal.println(versionOutput);
        return;
    }

    final boolean daemonize = options.has(daemonizeOption);
    final Path pidFile = pidfileOption.value(options);
    final boolean quiet = options.has(quietOption);

    // a misconfigured java.io.tmpdir can cause hard-to-diagnose problems later, so reject it immediately
    try {
        env.validateTmpFile();
    } catch (IOException e) {
        throw new UserException(ExitCodes.CONFIG, e.getMessage());
    }

    try {
        // 启动类 Init
        init(daemonize, pidFile, quiet, env);
    } catch (NodeValidationException e) {
        throw new UserException(ExitCodes.CONFIG, e.getMessage());
    }
}

// org/elasticsearch/bootstrap/Elasticsearch.java#init
void init(final boolean daemonize, final Path pidFile, final boolean quiet, Environment initialEnv)
      throws NodeValidationException, UserException {
      try {
          // 启动类 Init，开始启动 ElasticSearch
          Bootstrap.init(!daemonize, pidFile, quiet, initialEnv);
      } catch (BootstrapException | RuntimeException e) {
          // format exceptions to the console in a special way
          // to avoid 2MB stacktraces from guice, etc.
          throw new StartupException(e);
      }
  }
```

`Elasticsearch#execute` 会打印 JVM 信息并执行 `init(daemonize, pidFile, quiet, env);`,init 方法执行 `Bootstrap.init(!daemonize, pidFile, quiet, initialEnv);`。
Bootstrap 是启动类，调用其 init 方法启动 ElasticSearch 服务。