# 启动流程

- 目录
  - [启动脚本](#启动脚本)
  - [入口](#入口)
  - [Bootstrap](#Bootstrap)
    - [setup](#setup)
    - [start](#start)

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
Bootstrap 是启动类，调用其 init 方法启动 ElasticSearch  服务。

### Bootstrap

Bootstrap 是 ElasticSearch 的引导类，负责服务启动。

- Bootstrap 构造函数:

```java
// org/elasticsearch/bootstrap/Bootstrap.java#Bootstrap
Bootstrap() {
    // 守护线程，保活
    keepAliveThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                keepAliveLatch.await();
            } catch (InterruptedException e) {
                // bail out
            }
        }
    }, "elasticsearch[keepAlive/" + Version.CURRENT + "]");
    // setDaemon(false) 设置为用户线程：主线程结束后用户线程还会继续运行,JVM 存活
    keepAliveThread.setDaemon(false);
    // keep this thread alive (non daemon thread) until we shutdown
    Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
            keepAliveLatch.countDown();
        }
    });
}
```

Bootstrap 构造函数初始化了 keepAliveThread，它的 run 方法体中 keepAliveLatch 值初始化是 1，当 keepAliveLatch 没有减到 0 时，KeepAliveThread 线程一直等待。而且 keepAliveThread 被设置未用户线程（主线程结束后用户线程还会继续运行,保证 JVM 存活），以达到 keepAlive（保活） 的作用。

注意：此时 keepAliveThread 并没有 start。

- Bootstrap:init

```java
// org/elasticsearch/bootstrap/Bootstrap.java#init
static void init(
    final boolean foreground,
    final Path pidFile,
    final boolean quiet,
    final Environment initialEnv) throws BootstrapException, NodeValidationException, UserException {
    // force the class initializer for BootstrapInfo to run before
    // the security manager is installed
    BootstrapInfo.init();
    // 创建 Bootstrap 实例,内部注册了一个关闭的钩子并使用非守护线程来保证只有一个 Bootstrap 实例启动。
    INSTANCE = new Bootstrap();

    // 如果注册了安全模块则将相关配置加载进来：从提供的配置目录加载关于 Elasticsearch 密钥存储库的信息。
    final SecureSettings keystore = loadSecureSettings(initialEnv);
    // 创建 Elasticsearch 运行的必须环境以及相关配置,如将 config,scripts,plugins,modules,logs,lib,bin 等配置目录加载到运行环境中
    final Environment environment = createEnvironment(pidFile, keystore, initialEnv.settings(), initialEnv.configFile());

    LogConfigurator.setNodeName(Node.NODE_NAME_SETTING.get(environment.settings()));
    try {
        // log4j2 配置
        LogConfigurator.configure(environment);
    } catch (IOException e) {
        throw new BootstrapException(e);
    }
    // JDK 版本检测
    if (JavaVersion.current().compareTo(JavaVersion.parse("11")) < 0) {
        final String message = String.format(
            Locale.ROOT,
            "future versions of Elasticsearch will require Java 11; " +
                "your Java version from [%s] does not meet this requirement",
            System.getProperty("java.home"));
        new DeprecationLogger(LogManager.getLogger(Bootstrap.class)).deprecated(message);
    }
    if (environment.pidFile() != null) {
        try {
            // 创建 Pid 文件存储当前 JVM 进程 Id
            PidFile.create(environment.pidFile(), true);
        } catch (IOException e) {
            throw new BootstrapException(e);
        }
    }

    final boolean closeStandardStreams = (foreground == false) || quiet;
    try {
        if (closeStandardStreams) {
            final Logger rootLogger = LogManager.getRootLogger();
            final Appender maybeConsoleAppender = Loggers.findAppender(rootLogger, ConsoleAppender.class);
            if (maybeConsoleAppender != null) {
                Loggers.removeAppender(rootLogger, maybeConsoleAppender);
            }
            closeSystOut();
        }

        // fail if somebody replaced the lucene jars
        // 检查 lucene 版本
        checkLucene();

        // install the default uncaught exception handler; must be done before security is
        // initialized as we do not want to grant the runtime permission
        // setDefaultUncaughtExceptionHandler
        // 设置默认异常处理器
        Thread.setDefaultUncaughtExceptionHandler(new ElasticsearchUncaughtExceptionHandler());

        // 设置
        INSTANCE.setup(true, environment);

        try {
            // any secure settings must be read during node construction
            IOUtils.close(keystore);
        } catch (IOException e) {
            throw new BootstrapException(e);
        }

        // 启动
        INSTANCE.start();

        if (closeStandardStreams) {
            closeSysError();
        }
    } catch (NodeValidationException | RuntimeException e) {
        // disable console logging, so user does not see the exception twice (jvm will show it already)
        final Logger rootLogger = LogManager.getRootLogger();
        final Appender maybeConsoleAppender = Loggers.findAppender(rootLogger, ConsoleAppender.class);
        if (foreground && maybeConsoleAppender != null) {
            Loggers.removeAppender(rootLogger, maybeConsoleAppender);
        }
        Logger logger = LogManager.getLogger(Bootstrap.class);
        // HACK, it sucks to do this, but we will run users out of disk space otherwise
        if (e instanceof CreationException) {
            // guice: log the shortened exc to the log file
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PrintStream ps = null;
            try {
                ps = new PrintStream(os, false, "UTF-8");
            } catch (UnsupportedEncodingException uee) {
                assert false;
                e.addSuppressed(uee);
            }
            new StartupException(e).printStackTrace(ps);
            ps.flush();
            try {
                logger.error("Guice Exception: {}", os.toString("UTF-8"));
            } catch (UnsupportedEncodingException uee) {
                assert false;
                e.addSuppressed(uee);
            }
        } else if (e instanceof NodeValidationException) {
            logger.error("node validation exception\n{}", e.getMessage());
        } else {
            // full exception
            logger.error("Exception", e);
        }
        // re-enable it if appropriate, so they can see any logging during the shutdown process
        if (foreground && maybeConsoleAppender != null) {
            Loggers.addAppender(rootLogger, maybeConsoleAppender);
        }

        throw e;
    }
}
```

`Bootstrap:init` 处理流程：
1. BootstrapInfo 初始化。
2. 如果注册了安全模块，则加载 SecureSettings keystore 配置。
3. log4j2 日志配置及启动。
4. JDK 版本检测。
5. 创建 Pid 文件存储当前 JVM 进程 Id。
6. 是否静默启动的配置，即关闭标准输出流。
7. 校验 Lucene 版本。
8. 设置默认异常处理器。
9. `INSTANCE.setup(true, environment);`：实例设置。
10. `INSTANCE.start();`：实例启动。

#### setup

前面的流程，已经完成了配置解析和环境检查，`Bootstrap#setup` 开始初始化内部资源。

- Bootstrap#setup

```java
private void setup(boolean addShutdownHook, Environment environment) throws BootstrapException {
    Settings settings = environment.settings();

    try {
        // 生成每个模块的本地控制器
        spawner.spawnNativeControllers(environment);
    } catch (IOException e) {
        throw new BootstrapException(e);
    }

    // 实例化本地资源
    initializeNatives(
        environment.tmpFile(),
        BootstrapSettings.MEMORY_LOCK_SETTING.get(settings),
        BootstrapSettings.SYSTEM_CALL_FILTER_SETTING.get(settings),
        BootstrapSettings.CTRLHANDLER_SETTING.get(settings));

    // initialize probes before the security manager is installed
    // 在安装安全管理器之前初始化探测：OS、JVM
    initializeProbes();

    if (addShutdownHook) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    IOUtils.close(node, spawner);
                    LoggerContext context = (LoggerContext) LogManager.getContext(false);
                    Configurator.shutdown(context);
                    if (node != null && node.awaitClose(10, TimeUnit.SECONDS) == false) {
                        throw new IllegalStateException("Node didn't stop within 10 seconds. " +
                            "Any outstanding requests or tasks might get killed.");
                    }
                } catch (IOException ex) {
                    throw new ElasticsearchException("failed to stop node", ex);
                } catch (InterruptedException e) {
                    LogManager.getLogger(Bootstrap.class).warn("Thread got interrupted while waiting for the node to shutdown.");
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    try {
        // look for jar hell
        final Logger logger = LogManager.getLogger(JarHell.class);
        JarHell.checkJarHell(logger::debug);
    } catch (IOException | URISyntaxException e) {
        throw new BootstrapException(e);
    }

    // Log ifconfig output before SecurityManager is installed
    IfConfig.logIfNecessary();

    fixJDK14EAFileChannelMap();

    // install SM after natives, shutdown hooks, etc.
    try {
        Security.configure(environment, BootstrapSettings.SECURITY_FILTER_BAD_DEFAULTS_SETTING.get(settings));
    } catch (IOException | NoSuchAlgorithmException e) {
        throw new BootstrapException(e);
    }
    // 创建节点对象，这之前都是做装备工作
    node = new Node(environment) {
        @Override
        protected void validateNodeBeforeAcceptingRequests(
            final BootstrapContext context,
            final BoundTransportAddress boundTransportAddress, List<BootstrapCheck> checks) throws NodeValidationException {
            BootstrapChecks.check(context, boundTransportAddress, checks);
        }
    };
}
```

`Bootstrap#setup` 主要是初始化内部资源，在做好这些工作之后，就需要将之前解析的配置属性和资源注入的 Node 中，即实例化 Node。

ElasticSearch 是一个分布式的系统，它的每个实例都是一个 Node，因此启动一个 ElasticSearch 实例，实际上是初始化并运行一个 Node。

- new Node

Node 即一个 ElasticSearch 实例，构造函数如下：

```java
protected Node(
    final Environment environment, Collection<Class<? extends Plugin>> classpathPlugins, boolean forbidPrivateIndexSettings) {
    logger = LogManager.getLogger(Node.class);
    final List<Closeable> resourcesToClose = new ArrayList<>(); // register everything we need to release in the case of an error
    boolean success = false;
    try {
        Settings tmpSettings = Settings.builder().put(environment.settings())
            .put(Client.CLIENT_TYPE_SETTING_S.getKey(), CLIENT_TYPE).build();
        // 实例化节点上下文环境，主要是 elasticsearch.yml 的配置，以及节点 Id,分片信息,元信息,以及分配内存准备给节点使用
        nodeEnvironment = new NodeEnvironment(tmpSettings, environment);
        resourcesToClose.add(nodeEnvironment);
        logger.info("node name [{}], node ID [{}], cluster name [{}]",
            NODE_NAME_SETTING.get(tmpSettings), nodeEnvironment.nodeId(),
            ClusterName.CLUSTER_NAME_SETTING.get(tmpSettings).value());
        // 打印 JVM 信息
        final JvmInfo jvmInfo = JvmInfo.jvmInfo();
        logger.info(
            "version[{}], pid[{}], build[{}/{}/{}/{}], OS[{}/{}/{}], JVM[{}/{}/{}/{}]",
            Build.CURRENT.getQualifiedVersion(),
            jvmInfo.pid(),
            Build.CURRENT.flavor().displayName(),
            Build.CURRENT.type().displayName(),
            Build.CURRENT.hash(),
            Build.CURRENT.date(),
            Constants.OS_NAME,
            Constants.OS_VERSION,
            Constants.OS_ARCH,
            Constants.JVM_VENDOR,
            Constants.JVM_NAME,
            Constants.JAVA_VERSION,
            Constants.JVM_VERSION);
        logger.info("JVM home [{}]", System.getProperty("java.home"));
        logger.info("JVM arguments {}", Arrays.toString(jvmInfo.getInputArguments()));
        if (Build.CURRENT.isProductionRelease() == false) {
            logger.warn(
                "version [{}] is a pre-release version of Elasticsearch and is not suitable for production",
                Build.CURRENT.getQualifiedVersion());
        }

        if (logger.isDebugEnabled()) {
            logger.debug("using config [{}], data [{}], logs [{}], plugins [{}]",
                environment.configFile(), Arrays.toString(environment.dataFiles()), environment.logsFile(), environment.pluginsFile());
        }

        // 初始化 PluginsService，加载相应的模块和插件
        this.pluginsService = new PluginsService(tmpSettings, environment.configFile(), environment.modulesFile(),
            environment.pluginsFile(), classpathPlugins);
        final Settings settings = pluginsService.updatedSettings();
        final Set<DiscoveryNodeRole> possibleRoles = Stream.concat(
            DiscoveryNodeRole.BUILT_IN_ROLES.stream(),
            pluginsService.filterPlugins(Plugin.class)
                .stream()
                .map(Plugin::getRoles)
                .flatMap(Set::stream))
            .collect(Collectors.toSet());
        DiscoveryNode.setPossibleRoles(possibleRoles);
        localNodeFactory = new LocalNodeFactory(settings, nodeEnvironment.nodeId());

        // create the environment based on the finalized (processed) view of the settings
        // this is just to makes sure that people get the same settings, no matter where they ask them from
        this.environment = new Environment(settings, environment.configFile());
        Environment.assertEquivalent(environment, this.environment);

        final List<ExecutorBuilder<?>> executorBuilders = pluginsService.getExecutorBuilders(settings);

        final ThreadPool threadPool = new ThreadPool(settings, executorBuilders.toArray(new ExecutorBuilder[0]));
        resourcesToClose.add(() -> ThreadPool.terminate(threadPool, 10, TimeUnit.SECONDS));
        // adds the context to the DeprecationLogger so that it does not need to be injected everywhere
        DeprecationLogger.setThreadContext(threadPool.getThreadContext());
        resourcesToClose.add(() -> DeprecationLogger.removeThreadContext(threadPool.getThreadContext()));

        final List<Setting<?>> additionalSettings = new ArrayList<>(pluginsService.getPluginSettings());
        final List<String> additionalSettingsFilter = new ArrayList<>(pluginsService.getPluginSettingsFilter());
        // 加载额外配置
        for (final ExecutorBuilder<?> builder : threadPool.builders()) {
            additionalSettings.addAll(builder.getRegisteredSettings());
        }
        // 创建一个节点客户端
        client = new NodeClient(settings, threadPool);
        final ResourceWatcherService resourceWatcherService = new ResourceWatcherService(settings, threadPool);
        final ScriptModule scriptModule = new ScriptModule(settings, pluginsService.filterPlugins(ScriptPlugin.class));
        AnalysisModule analysisModule = new AnalysisModule(this.environment, pluginsService.filterPlugins(AnalysisPlugin.class));
        // this is as early as we can validate settings at this point. we already pass them to ScriptModule as well as ThreadPool
        // so we might be late here already

        final Set<SettingUpgrader<?>> settingsUpgraders = pluginsService.filterPlugins(Plugin.class)
            .stream()
            .map(Plugin::getSettingUpgraders)
            .flatMap(List::stream)
            .collect(Collectors.toSet());

        final SettingsModule settingsModule =
            new SettingsModule(settings, additionalSettings, additionalSettingsFilter, settingsUpgraders);
        scriptModule.registerClusterSettingsListeners(settingsModule.getClusterSettings());
        resourcesToClose.add(resourceWatcherService);
        final NetworkService networkService = new NetworkService(
            getCustomNameResolvers(pluginsService.filterPlugins(DiscoveryPlugin.class)));

        List<ClusterPlugin> clusterPlugins = pluginsService.filterPlugins(ClusterPlugin.class);
        final ClusterService clusterService = new ClusterService(settings, settingsModule.getClusterSettings(), threadPool);
        clusterService.addStateApplier(scriptModule.getScriptService());
        resourcesToClose.add(clusterService);
        clusterService.addLocalNodeMasterListener(
            new ConsistentSettingsService(settings, clusterService, settingsModule.getConsistentSettings())
                .newHashPublisher());
        final IngestService ingestService = new IngestService(clusterService, threadPool, this.environment,
            scriptModule.getScriptService(), analysisModule.getAnalysisRegistry(),
            pluginsService.filterPlugins(IngestPlugin.class), client);
        final ClusterInfoService clusterInfoService = newClusterInfoService(settings, clusterService, threadPool, client);
        final UsageService usageService = new UsageService();

        ModulesBuilder modules = new ModulesBuilder();
        // plugin modules must be added here, before others or we can get crazy injection errors...
        // modules 缓存一系列模块,如 NodeModule,ClusterModule,IndicesModule,ActionModule,GatewayModule,SettingsModule,RepositioriesModule
        for (Module pluginModule : pluginsService.createGuiceModules()) {
            modules.add(pluginModule);
        }
        final MonitorService monitorService = new MonitorService(settings, nodeEnvironment, threadPool, clusterInfoService);
        ClusterModule clusterModule = new ClusterModule(settings, clusterService, clusterPlugins, clusterInfoService);
        modules.add(clusterModule);
        IndicesModule indicesModule = new IndicesModule(pluginsService.filterPlugins(MapperPlugin.class));
        modules.add(indicesModule);

        SearchModule searchModule = new SearchModule(settings, false, pluginsService.filterPlugins(SearchPlugin.class));
        CircuitBreakerService circuitBreakerService = createCircuitBreakerService(settingsModule.getSettings(),
            settingsModule.getClusterSettings());
        resourcesToClose.add(circuitBreakerService);
        modules.add(new GatewayModule());

        PageCacheRecycler pageCacheRecycler = createPageCacheRecycler(settings);
        BigArrays bigArrays = createBigArrays(pageCacheRecycler, circuitBreakerService);
        modules.add(settingsModule);
        List<NamedWriteableRegistry.Entry> namedWriteables = Stream.of(
            NetworkModule.getNamedWriteables().stream(),
            indicesModule.getNamedWriteables().stream(),
            searchModule.getNamedWriteables().stream(),
            pluginsService.filterPlugins(Plugin.class).stream()
                .flatMap(p -> p.getNamedWriteables().stream()),
            ClusterModule.getNamedWriteables().stream())
            .flatMap(Function.identity()).collect(Collectors.toList());
        final NamedWriteableRegistry namedWriteableRegistry = new NamedWriteableRegistry(namedWriteables);
        NamedXContentRegistry xContentRegistry = new NamedXContentRegistry(Stream.of(
            NetworkModule.getNamedXContents().stream(),
            indicesModule.getNamedXContents().stream(),
            searchModule.getNamedXContents().stream(),
            pluginsService.filterPlugins(Plugin.class).stream()
                .flatMap(p -> p.getNamedXContent().stream()),
            ClusterModule.getNamedXWriteables().stream())
            .flatMap(Function.identity()).collect(toList()));
        final MetaStateService metaStateService = new MetaStateService(nodeEnvironment, xContentRegistry);

        // collect engine factory providers from server and from plugins
        final Collection<EnginePlugin> enginePlugins = pluginsService.filterPlugins(EnginePlugin.class);
        final Collection<Function<IndexSettings, Optional<EngineFactory>>> engineFactoryProviders =
            Stream.concat(
                indicesModule.getEngineFactories().stream(),
                enginePlugins.stream().map(plugin -> plugin::getEngineFactory))
                .collect(Collectors.toList());

        final Map<String, IndexStorePlugin.DirectoryFactory> indexStoreFactories =
            pluginsService.filterPlugins(IndexStorePlugin.class)
                .stream()
                .map(IndexStorePlugin::getDirectoryFactories)
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        final IndicesService indicesService =
            new IndicesService(settings, pluginsService, nodeEnvironment, xContentRegistry, analysisModule.getAnalysisRegistry(),
                clusterModule.getIndexNameExpressionResolver(), indicesModule.getMapperRegistry(), namedWriteableRegistry,
                threadPool, settingsModule.getIndexScopedSettings(), circuitBreakerService, bigArrays, scriptModule.getScriptService(),
                clusterService, client, metaStateService, engineFactoryProviders, indexStoreFactories);

        final AliasValidator aliasValidator = new AliasValidator();

        final MetaDataCreateIndexService metaDataCreateIndexService = new MetaDataCreateIndexService(
            settings,
            clusterService,
            indicesService,
            clusterModule.getAllocationService(),
            aliasValidator,
            environment,
            settingsModule.getIndexScopedSettings(),
            threadPool,
            xContentRegistry,
            forbidPrivateIndexSettings);

        Collection<Object> pluginComponents = pluginsService.filterPlugins(Plugin.class).stream()
            .flatMap(p -> p.createComponents(client, clusterService, threadPool, resourceWatcherService,
                scriptModule.getScriptService(), xContentRegistry, environment, nodeEnvironment,
                namedWriteableRegistry).stream())
            .collect(Collectors.toList());

        ActionModule actionModule = new ActionModule(false, settings, clusterModule.getIndexNameExpressionResolver(),
            settingsModule.getIndexScopedSettings(), settingsModule.getClusterSettings(), settingsModule.getSettingsFilter(),
            threadPool, pluginsService.filterPlugins(ActionPlugin.class), client, circuitBreakerService, usageService, clusterService);
        modules.add(actionModule);

        // 获取 RestController,用于处理各种 Elasticsearch 的 RESTful 命令,如 _cat,_all,_cat/health,_clusters 等(Elasticsearch 称之为 action)
        // 并用于初始化 NetworkModule，注册 HttpServerTransport
        final RestController restController = actionModule.getRestController();
        // 初始化 NetworkModule 的传输模块和 HTTP 模块,加载 Transport、HttpServerTransport 和 TransportInterceptor
        final NetworkModule networkModule = new NetworkModule(settings, false, pluginsService.filterPlugins(NetworkPlugin.class),
            threadPool, bigArrays, pageCacheRecycler, circuitBreakerService, namedWriteableRegistry, xContentRegistry,
            networkService, restController);
        Collection<UnaryOperator<Map<String, IndexTemplateMetaData>>> indexTemplateMetaDataUpgraders =
            pluginsService.filterPlugins(Plugin.class).stream()
                .map(Plugin::getIndexTemplateMetaDataUpgrader)
                .collect(Collectors.toList());
        final MetaDataUpgrader metaDataUpgrader = new MetaDataUpgrader(indexTemplateMetaDataUpgraders);
        final MetaDataIndexUpgradeService metaDataIndexUpgradeService = new MetaDataIndexUpgradeService(settings, xContentRegistry,
            indicesModule.getMapperRegistry(), settingsModule.getIndexScopedSettings());
        new TemplateUpgradeService(client, clusterService, threadPool, indexTemplateMetaDataUpgraders);
        // 获取  transport，用于初始化 transportService
        final Transport transport = networkModule.getTransportSupplier().get();
        Set<String> taskHeaders = Stream.concat(
            pluginsService.filterPlugins(ActionPlugin.class).stream().flatMap(p -> p.getTaskHeaders().stream()),
            Stream.of(Task.X_OPAQUE_ID)
        ).collect(Collectors.toSet());
        // 初始化 transportService，用于处理节点间通信
        final TransportService transportService = newTransportService(settings, transport, threadPool,
            networkModule.getTransportInterceptor(), localNodeFactory, settingsModule.getClusterSettings(), taskHeaders);
        final GatewayMetaState gatewayMetaState = new GatewayMetaState();
        final ResponseCollectorService responseCollectorService = new ResponseCollectorService(clusterService);
        final SearchTransportService searchTransportService = new SearchTransportService(transportService,
            SearchExecutionStatsCollector.makeWrapper(responseCollectorService));
        // 获取  httpServerTransport
        final HttpServerTransport httpServerTransport = newHttpTransport(networkModule);

        RepositoriesModule repositoriesModule = new RepositoriesModule(this.environment,
            pluginsService.filterPlugins(RepositoryPlugin.class), transportService, clusterService, threadPool, xContentRegistry);
        RepositoriesService repositoryService = repositoriesModule.getRepositoryService();
        SnapshotsService snapshotsService = new SnapshotsService(settings, clusterService,
            clusterModule.getIndexNameExpressionResolver(), repositoryService, threadPool);
        SnapshotShardsService snapshotShardsService = new SnapshotShardsService(settings, clusterService, repositoryService,
            threadPool, transportService, indicesService, actionModule.getActionFilters(),
            clusterModule.getIndexNameExpressionResolver());
        TransportNodesSnapshotsStatus nodesSnapshotsStatus = new TransportNodesSnapshotsStatus(threadPool, clusterService,
            transportService, snapshotShardsService, actionModule.getActionFilters());
        RestoreService restoreService = new RestoreService(clusterService, repositoryService, clusterModule.getAllocationService(),
            metaDataCreateIndexService, metaDataIndexUpgradeService, clusterService.getClusterSettings());

        final RerouteService rerouteService
            = new BatchedRerouteService(clusterService, clusterModule.getAllocationService()::reroute);
        final DiskThresholdMonitor diskThresholdMonitor = new DiskThresholdMonitor(settings, clusterService::state,
            clusterService.getClusterSettings(), client, threadPool::relativeTimeInMillis, rerouteService);
        clusterInfoService.addListener(diskThresholdMonitor::onNewInfo);

        final DiscoveryModule discoveryModule = new DiscoveryModule(settings, threadPool, transportService, namedWriteableRegistry,
            networkService, clusterService.getMasterService(), clusterService.getClusterApplierService(),
            clusterService.getClusterSettings(), pluginsService.filterPlugins(DiscoveryPlugin.class),
            clusterModule.getAllocationService(), environment.configFile(), gatewayMetaState, rerouteService);
        this.nodeService = new NodeService(settings, threadPool, monitorService, discoveryModule.getDiscovery(),
            transportService, indicesService, pluginsService, circuitBreakerService, scriptModule.getScriptService(),
            httpServerTransport, ingestService, clusterService, settingsModule.getSettingsFilter(), responseCollectorService,
            searchTransportService);

        final SearchService searchService = newSearchService(clusterService, indicesService,
            threadPool, scriptModule.getScriptService(), bigArrays, searchModule.getFetchPhase(),
            responseCollectorService);

        final List<PersistentTasksExecutor<?>> tasksExecutors = pluginsService
            .filterPlugins(PersistentTaskPlugin.class).stream()
            .map(p -> p.getPersistentTasksExecutor(clusterService, threadPool, client, settingsModule))
            .flatMap(List::stream)
            .collect(toList());

        final PersistentTasksExecutorRegistry registry = new PersistentTasksExecutorRegistry(tasksExecutors);
        final PersistentTasksClusterService persistentTasksClusterService =
            new PersistentTasksClusterService(settings, registry, clusterService, threadPool);
        resourcesToClose.add(persistentTasksClusterService);
        final PersistentTasksService persistentTasksService = new PersistentTasksService(clusterService, threadPool, client);

        // 绑定处理各种服务的实例,这里是最核心的地方,也是 Elasticsearch 能处理各种服务的核心.
        modules.add(b -> {
                b.bind(Node.class).toInstance(this);
                b.bind(NodeService.class).toInstance(nodeService);
                b.bind(NamedXContentRegistry.class).toInstance(xContentRegistry);
                b.bind(PluginsService.class).toInstance(pluginsService);
                b.bind(Client.class).toInstance(client);
                b.bind(NodeClient.class).toInstance(client);
                b.bind(Environment.class).toInstance(this.environment);
                b.bind(ThreadPool.class).toInstance(threadPool);
                b.bind(NodeEnvironment.class).toInstance(nodeEnvironment);
                b.bind(ResourceWatcherService.class).toInstance(resourceWatcherService);
                b.bind(CircuitBreakerService.class).toInstance(circuitBreakerService);
                b.bind(BigArrays.class).toInstance(bigArrays);
                b.bind(PageCacheRecycler.class).toInstance(pageCacheRecycler);
                b.bind(ScriptService.class).toInstance(scriptModule.getScriptService());
                b.bind(AnalysisRegistry.class).toInstance(analysisModule.getAnalysisRegistry());
                b.bind(IngestService.class).toInstance(ingestService);
                b.bind(UsageService.class).toInstance(usageService);
                b.bind(NamedWriteableRegistry.class).toInstance(namedWriteableRegistry);
                b.bind(MetaDataUpgrader.class).toInstance(metaDataUpgrader);
                b.bind(MetaStateService.class).toInstance(metaStateService);
                b.bind(IndicesService.class).toInstance(indicesService);
                b.bind(AliasValidator.class).toInstance(aliasValidator);
                b.bind(MetaDataCreateIndexService.class).toInstance(metaDataCreateIndexService);
                b.bind(SearchService.class).toInstance(searchService);
                b.bind(SearchTransportService.class).toInstance(searchTransportService);
                b.bind(SearchPhaseController.class).toInstance(new SearchPhaseController(searchService::createReduceContext));
                b.bind(Transport.class).toInstance(transport);
                b.bind(TransportService.class).toInstance(transportService);
                b.bind(NetworkService.class).toInstance(networkService);
                b.bind(UpdateHelper.class).toInstance(new UpdateHelper(scriptModule.getScriptService()));
                b.bind(MetaDataIndexUpgradeService.class).toInstance(metaDataIndexUpgradeService);
                b.bind(ClusterInfoService.class).toInstance(clusterInfoService);
                b.bind(GatewayMetaState.class).toInstance(gatewayMetaState);
                b.bind(Discovery.class).toInstance(discoveryModule.getDiscovery());
                {
                    RecoverySettings recoverySettings = new RecoverySettings(settings, settingsModule.getClusterSettings());
                    processRecoverySettings(settingsModule.getClusterSettings(), recoverySettings);
                    b.bind(PeerRecoverySourceService.class).toInstance(new PeerRecoverySourceService(transportService,
                        indicesService, recoverySettings));
                    b.bind(PeerRecoveryTargetService.class).toInstance(new PeerRecoveryTargetService(threadPool,
                        transportService, recoverySettings, clusterService));
                }
                b.bind(HttpServerTransport.class).toInstance(httpServerTransport);
                pluginComponents.stream().forEach(p -> b.bind((Class) p.getClass()).toInstance(p));
                b.bind(PersistentTasksService.class).toInstance(persistentTasksService);
                b.bind(PersistentTasksClusterService.class).toInstance(persistentTasksClusterService);
                b.bind(PersistentTasksExecutorRegistry.class).toInstance(registry);
                b.bind(RepositoriesService.class).toInstance(repositoryService);
                b.bind(SnapshotsService.class).toInstance(snapshotsService);
                b.bind(SnapshotShardsService.class).toInstance(snapshotShardsService);
                b.bind(TransportNodesSnapshotsStatus.class).toInstance(nodesSnapshotsStatus);
                b.bind(RestoreService.class).toInstance(restoreService);
                b.bind(RerouteService.class).toInstance(rerouteService);
            }
        );
        // 利用 Guice 将各种模块以及服务(xxxService)注入到 Elasticsearch 环境中
        injector = modules.createInjector();

        // TODO hack around circular dependencies problems in AllocationService
        clusterModule.getAllocationService().setGatewayAllocator(injector.getInstance(GatewayAllocator.class));

        List<LifecycleComponent> pluginLifecycleComponents = pluginComponents.stream()
            .filter(p -> p instanceof LifecycleComponent)
            .map(p -> (LifecycleComponent) p).collect(Collectors.toList());
        pluginLifecycleComponents.addAll(pluginsService.getGuiceServiceClasses().stream()
            .map(injector::getInstance).collect(Collectors.toList()));
        resourcesToClose.addAll(pluginLifecycleComponents);
        resourcesToClose.add(injector.getInstance(PeerRecoverySourceService.class));
        this.pluginLifecycleComponents = Collections.unmodifiableList(pluginLifecycleComponents);
        // 进一步初始化 node client ：将 action 对应的 handler 缓存在 map 中；保存 Node Id;注入 remoteClusterService
        client.initialize(injector.getInstance(new Key<Map<ActionType, TransportAction>>() {
            }),
            () -> clusterService.localNode().getId(), transportService.getRemoteClusterService());

        logger.debug("initializing HTTP handlers ...");
        // 注册 RestHandlers，处理客户端请求
        actionModule.initRestHandlers(() -> clusterService.state().nodes());
        // 完成初始化
        logger.info("initialized");

        success = true;
    } catch (IOException ex) {
        throw new ElasticsearchException("failed to bind service", ex);
    } finally {
        if (!success) {
            IOUtils.closeWhileHandlingException(resourcesToClose);
        }
    }
}
```

Node 构造函数流程：
1. 通过 environment 属性构造 node-environment 属性，实例化节点上下文环境。
2. resourcesToClose 收集资源用于 Node 退出时关闭资源。
3. 打印 JVM 信息。
4. 初始化 PluginsService，用于加载模块。
5. 创建一个节点客户端。
6. 实例化模块服务，如 ClusterService、IngestService、MonitorService 等。
7. ModulesBuilder 缓存模块，如 NodeModule,ClusterModule,IndicesModule,ActionModule,GatewayModule,SettingsModule,RepositioriesModule 等。
8. 初始化 RestController 和 NetworkModule。
9. 绑定处理各种服务的实例,这里是最核心的地方,也是 Elasticsearch 能处理各种服务的核心。
10. 利用 Guice 将各种模块以及服务(xxxService)注入到 node 环境中。
11. 进一步初始化 node client ：将 action 对应的 handler 缓存在 map 中；保存 Node Id;注入 remoteClusterService。
12. 注册 RestHandlers，处理客户端请求。


> 完成 node 实例化，意味着 Bootstrap#setup 处理完毕，下面准备 Bootstrap#start。

#### start

- Bootstrap#start

```java
private void start() throws NodeValidationException {
    // 启动节点
    node.start();
    // 保活
    keepAliveThread.start();
}
```

`Bootstrap#start` 负责启动 node 和 keepAlive，当 node 启动失败，会对外抛出异常。

-  Node#start

```java
public Node start() throws NodeValidationException {
    if (!lifecycle.moveToStarted()) {
        return this;
    }

    logger.info("starting ...");
    // 扩展模块调用 start 方法
    pluginLifecycleComponents.forEach(LifecycleComponent::start);

    // 利用 Guice 获取上述注册的各种模块以及服务，并启动
    injector.getInstance(MappingUpdatedAction.class).setClient(client);
    injector.getInstance(IndicesService.class).start();
    injector.getInstance(IndicesClusterStateService.class).start();
    injector.getInstance(SnapshotsService.class).start();
    injector.getInstance(SnapshotShardsService.class).start();
    injector.getInstance(SearchService.class).start();
    nodeService.getMonitorService().start();

    // 获取 ClusterService 实例
    final ClusterService clusterService = injector.getInstance(ClusterService.class);

    // 该组件负责维护从该节点到集群状态中列出的所有节点的连接，并在从集群状态中删除节点后断开与节点的连接。
    final NodeConnectionsService nodeConnectionsService = injector.getInstance(NodeConnectionsService.class);
    nodeConnectionsService.start();
    clusterService.setNodeConnectionsService(nodeConnectionsService);

    injector.getInstance(ResourceWatcherService.class).start();
    injector.getInstance(GatewayService.class).start();
    // 一个可插入模块，允许发现其他节点，将集群状态发布到所有节点，选择引发集群状态更改事件的集群主节点。
    Discovery discovery = injector.getInstance(Discovery.class);
    clusterService.getMasterService().setClusterStatePublisher(discovery::publish);

    // Start the transport service now so the publish address will be added to the local disco node in ClusterService
    // 现在启动传输服务，以便将发布地址添加到 ClusterService 中的本地节点
    TransportService transportService = injector.getInstance(TransportService.class);
    transportService.getTaskManager().setTaskResultsService(injector.getInstance(TaskResultsService.class));
    transportService.start();
    assert localNodeFactory.getNode() != null;
    assert transportService.getLocalNode().equals(localNodeFactory.getNode())
        : "transportService has a different local node than the factory provided";
    // 源分片恢复接受来自其他对等分片的恢复请求，并启动从这个源分片到目标分片的恢复过程。
    injector.getInstance(PeerRecoverySourceService.class).start();

    // Load (and maybe upgrade) the metadata stored on disk
    // 加载(可能升级)存储在磁盘上的元数据
    final GatewayMetaState gatewayMetaState = injector.getInstance(GatewayMetaState.class);
    gatewayMetaState.start(settings(), transportService, clusterService, injector.getInstance(MetaStateService.class),
        injector.getInstance(MetaDataIndexUpgradeService.class), injector.getInstance(MetaDataUpgrader.class));
    // we load the global state here (the persistent part of the cluster state stored on disk) to
    // pass it to the bootstrap checks to allow plugins to enforce certain preconditions based on the recovered state.
    // 我们在这里加载全局状态(存储在磁盘上的集群状态的持久部分)，将其传递给引导检查，以允许插件根据恢复的状态强制执行某些先决条件。
    final MetaData onDiskMetadata = gatewayMetaState.getPersistedState().getLastAcceptedState().metaData();
    assert onDiskMetadata != null : "metadata is null but shouldn't"; // this is never null
    // 用于在网络服务启动之后、集群服务启动之前和网络服务开始接受传入网络请求之前验证节点的钩子。
    validateNodeBeforeAcceptingRequests(new BootstrapContext(environment, onDiskMetadata), transportService.boundAddress(),
        pluginsService.filterPlugins(Plugin.class).stream()
            .flatMap(p -> p.getBootstrapChecks().stream()).collect(Collectors.toList()));

    clusterService.addStateApplier(transportService.getTaskManager());
    // start after transport service so the local disco is known
    // 在传输服务之后启动，以便可以知道本地节点信息
    // start before cluster service so that it can set initial state on ClusterApplierService
    // 在集群服务之前启动，以便它可以在 ClusterApplierService 上设置初始状态
    discovery.start();
    // 启动集群
    //
    // clusterApplierService.start(); >>  clusterApplierService 负责执行启动期间注册的任务：如 ConsistentSettingsService$HashesPublisher、InternalClusterInfoService、MlMemoryTracker、MlInitializationService、IndexLifecycleService、
    // SnapshotLifecycleService、SnapshotRetentionService、EnrichPolicyMaintenanceService。
    //
    // masterService.start(); >> new Batcher(logger, threadPoolExecutor);  批处理任务执行器，用于执行集群状态更新任务
    clusterService.start();
    assert clusterService.localNode().equals(localNodeFactory.getNode())
        : "clusterService has a different local node than the factory provided";
    // 开始接受传入的请求。
    transportService.acceptIncomingRequests();
    // TODO
    discovery.startInitialJoin();
    final TimeValue initialStateTimeout = DiscoverySettings.INITIAL_STATE_TIMEOUT_SETTING.get(settings());
    configureNodeAndClusterIdStateListener(clusterService);

    // 加入集群
    if (initialStateTimeout.millis() > 0) {
        final ThreadPool thread = injector.getInstance(ThreadPool.class);
        ClusterState clusterState = clusterService.state();
        ClusterStateObserver observer =
            new ClusterStateObserver(clusterState, clusterService, null, logger, thread.getThreadContext());

        if (clusterState.nodes().getMasterNodeId() == null) {
            logger.debug("waiting to join the cluster. timeout [{}]", initialStateTimeout);
            final CountDownLatch latch = new CountDownLatch(1);
            // 等待下一个 statePredicate 状态，加入进群
            observer.waitForNextChange(new ClusterStateObserver.Listener() {
                @Override
                public void onNewClusterState(ClusterState state) { latch.countDown(); }

                @Override
                public void onClusterServiceClose() {
                    latch.countDown();
                }

                @Override
                public void onTimeout(TimeValue timeout) {
                    logger.warn("timed out while waiting for initial discovery state - timeout: {}",
                        initialStateTimeout);
                    latch.countDown();
                }
            }, state -> state.nodes().getMasterNodeId() != null, initialStateTimeout);

            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new ElasticsearchTimeoutException("Interrupted while waiting for initial discovery state");
            }
        }
    }

    injector.getInstance(HttpServerTransport.class).start();

    if (WRITE_PORTS_FILE_SETTING.get(settings())) {
        TransportService transport = injector.getInstance(TransportService.class);
        writePortsFile("transport", transport.boundAddress());
        HttpServerTransport http = injector.getInstance(HttpServerTransport.class);
        writePortsFile("http", http.boundAddress());
    }

    logger.info("started");

    // 节点启动完毕后，启动自定义插件
    pluginsService.filterPlugins(ClusterPlugin.class).forEach(ClusterPlugin::onNodeStarted);

    return this;
}
```

Node#start 负责启动节点，主要是启动各个模块、加载磁盘数据、初始化节点状态、将节点注册到集群中并提供服务，以及后续启动自定义插件。

- keepAliveThread.start

前面提到过，keepAliveThread 的作用就是保活，当执行 start，在节点没有暂停之前，该线程会一直处于 "等待" 中，以保证 JVM 存活。