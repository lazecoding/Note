# Bean 的实例化

- 目录
  - [什么是 IOC](#什么是-IOC)
    - [BeanFactory](#BeanFactory)
    - [ApplicationContext](#ApplicationContext)
    - [BeanDefinition](#BeanDefinition)
    - [BeanDefinitionRegistry](#BeanDefinitionRegistry)
    - [FactoryBean](#FactoryBean)
  - [实例化和解决循环依赖](#实例化和解决循环依赖) 
    - [循环依赖](#BeanFactory)
      - [构造器依赖](#构造器依赖)
      - [属性依赖](#属性依赖)
    - [finishBeanFactoryInitialization](#finishBeanFactoryInitialization)
      - [preInstantiateSingletons](#preInstantiateSingletons)
      - [factoryBean](#factoryBean)
      - [getBean](#getBean)
      - [getSingleton](#getSingleton)
      - [createBean](#createBean)
      - [doCreateBean](#doCreateBean)
  - [如何解决循环依赖](#如何解决循环依赖)
    - [解决思路](#解决思路)
    - [什么是三级缓存](#什么是三级缓存)
    - [一级缓存可行性分析](#一级缓存可行性分析)
    - [二级缓存可行性分析](#二级缓存可行性分析)
    - [三级缓存可行性分析](#三级缓存可行性分析)
      
上文 [Spring Boot 启动流程](https://github.com/lazecoding/Note/blob/main/note/articles/springboot/启动流程.md) 中讲解 Spring Boot 工程启动流程，其中关键的地方是 IOC 容器的工作流程。本文对上文进行补充说明，详细讲解 bean 的实例化原理。

### 什么是 IOC

> 控制反转（Inversion of Control，缩写为IoC），是面向对象编程中的一种设计原则，可以用来减低计算机代码之间的耦合度。其中最常见的方式叫做依赖注入（Dependency Injection，简称DI）。通过控制反转，对象在被创建的时候，由一个调控系统内所有对象的外界实体，将其所依赖的对象的引用传递给它。也可以说，依赖被注入到对象中。

DI 是一种实现，而 IOC 是一种设计思想。从 IOC 到 DI，就是从理论到了实践。你把依赖交给了容器，容器帮你管理依赖，这就是依赖注入的核心。

### IOC 重要组成

Spring Boot 是面向对象编码的，我们有必要对启动流程中涉及的对象进行说明。

#### BeanFactory

BeanFactory 是 Spring 框架最核心的接口，它提供了 IOC 的配置机制。

#### ApplicationContext

ApplicationContext 建立在 BeanFactory 基础之上，提供了更多面向应用的功能。SpringBoot 使用的是 ApplicationContext 的子类 AnnotationConfigServletWebServerApplicationContext。

#### BeanDefinition

BeanDefinition 用于保存 bean 的相关信息，包括属性、构造方法参数、依赖的 bean 名称及是否单例、延迟加载等，它是实例化 bean 的原材料，Spring 根据 BeanDefinition 中的信息实例化 bean。

#### BeanDefinitionRegistry

BeanDefinitionRegistry 是一个接口，它定义了关于 BeanDefinition 的注册、移除、查询等一系列的操作。以 DefaultListableBeanFactory 实现类为例，所以的 BeanDefinition 都存放在 beanDefinitionMap 中，这是一个 ConcurrentHashMap。

```java
// DefaultListableBeanFactory.java&beanDefinitionMap
private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);
```

#### FactoryBean

FactoryBean 是一种特殊的 bean，它是个工厂 bean，可以自己创建 bean 实例，如果一个类实现了 factoryBean 接口，则该类可以自己定义创建实例对象的方法，只需要实现它的 getObject() 方法。为了区分 factoryBean 和 factoryBean 创建的 bean 实例，Spring 通过 “&” 前缀区分。假设我们的 beanName 为 apple，则 getBean("apple") 获得的是 AppleFactoryBean 通过 getObject() 方法创建的 bean 实例；而 getBean("&apple") 获得的是 AppleFactoryBean 本身。

### 实例化和解决循环依赖

AbstractApplicationContext.java#finishBeanFactoryInitialization 不仅实现了 bean 的实例化，而且解决了循环依赖的问题。

#### 循环依赖

简单的说，循环依赖就是 A 依赖了 B，B 又依赖了 A。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/springboot/循环依赖.png" width="600px">
</div>

循环依赖分为构造器依赖和属性依赖，Spring 解决了属性的循环依赖（set 注入）。

##### 构造器依赖

产生构造器循环依赖时启动 Spring Boot 工程会出现错误，错误描述也很清晰：The dependencies of some of the beans in the application context form a cycle，即容器中的 beans 产生了循环依赖。

````java
@Component
public class A {
    public A(B b) {
    }
}

@Component
public class B {
    public B(A a) {
    }
}

/* Output:
The dependencies of some of the beans in the application context form a cycle:

┌─────┐
|  a defined in file [E:\Demos\base\target\classes\com\example\base\beaninit\A.class]
↑     ↓
|  b defined in file [E:\Demos\base\target\classes\com\example\base\beaninit\B.class]
└─────┘
*/
````

##### 属性依赖

单例的属性循环依赖，工程可以正常启动。

````java
@Component
public class A {
    @Autowired
    B b;
}

@Component
public class B {
    @Autowired
    A a;
}
````

#### finishBeanFactoryInitialization

finishBeanFactoryInitialization 方法的作用便是实例化所有剩余的非惰性加载的单例 bean，循环依赖也在这里得到了解决。

```java
// AbstractApplicationContext.java#refresh        
finishBeanFactoryInitialization(beanFactory);

// AbstractApplicationContext.java#finishBeanFactoryInitialization        
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
    // Initialize conversion service for this context.
    // 初始化上下文的转换服务，ConversionService 是一个类型转换接口
    if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
            beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
        beanFactory.setConversionService(
                beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
    }

    // Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
    // 初始化 LoadTimeWeaverAware Bean，以便尽早注册其转换器。
    String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
    for (String weaverAwareName : weaverAwareNames) {
        getBean(weaverAwareName);
    }

    // Stop using the temporary ClassLoader for type matching.
    // 停止使用临时的 ClassLoader 进行类型匹配。
    beanFactory.setTempClassLoader(null);

    // Allow for caching all bean definition metadata, not expecting further changes.
    // 设置 beanDefinition 元数据 不可以再修改
    beanFactory.freezeConfiguration();

    // Instantiate all remaining (non-lazy-init) singletons.
    // 实例化所有的非懒加载单例。
    beanFactory.preInstantiateSingletons();
}
```

##### preInstantiateSingletons

beanFactory.preInstantiateSingletons() 是 finishBeanFactoryInitialization 方法的关键行为，这块进入的是 DefaultListableBeanFactory 类。DefaultListableBeanFactory 就是我们的 beanFactory，preInstantiateSingletons 方法负责实例化非惰性加载的单例 bean。该方法遍历 beanDefinitionNames 实例化 bean，其中需要区别 factoryBean 和普通 bean。

```java
// DefaultListableBeanFactory.java#preInstantiateSingletons
@Override
public void preInstantiateSingletons() throws BeansException {
    if (logger.isTraceEnabled()) {
        logger.trace("Pre-instantiating singletons in " + this);
    }

    // Iterate over a copy to allow for init methods which in turn register new bean definitions.
    // While this may not be part of the regular factory bootstrap, it does otherwise work fine.
    // 将 beanDefinitionNames 放到集合中
    List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

    // Trigger initialization of all non-lazy singleton beans...
    // 遍历 beanDefinitionNames 集合
    for (String beanName : beanNames) {
        // 获取beanName对应的 MergedBeanDefinition,MergedBeanDefinition 来表示 "合并的 bean 定义"。
        RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
        // MergedBeanDefinition 不是抽象 & 是单例 & 不是懒加载
        if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
            // 是否为 factoryBean
            if (isFactoryBean(beanName)) {
                // 通过 beanName 获取 factoryBean 实例
                // 通过 getBean(&beanName) 拿到的是 factoryBean 本身；通过 getBean(beanName) 拿到的是 factoryBean 创建的 bean 实例
                Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
                if (bean instanceof FactoryBean) {
                    FactoryBean<?> factory = (FactoryBean<?>) bean;
                    // 判断这个 factoryBean 是否希望急切的初始化
                    boolean isEagerInit;
                    if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                        isEagerInit = AccessController.doPrivileged(
                                (PrivilegedAction<Boolean>) ((SmartFactoryBean<?>) factory)::isEagerInit,
                                getAccessControlContext());
                    }
                    else {
                        isEagerInit = (factory instanceof SmartFactoryBean &&
                                ((SmartFactoryBean<?>) factory).isEagerInit());
                    }
                    // 如果希望急切的初始化，则通过 beanName 获取 bean 实例
                    if (isEagerInit) {
                        getBean(beanName);
                    }
                }
            }
            else {
                // 如果 beanName 对应的 bean 不是 FactoryBean，只是普通 bean，通过 beanName 获取 bean 实例
                getBean(beanName);
            }
        }
    }

    // Trigger post-initialization callback for all applicable beans...
    // 遍历 beanDefinitionNames 集合，触发所有 SmartInitializingSingleton 的后初始化回调
    for (String beanName : beanNames) {
        // 拿到 beanName 对应的 bean 实例
        Object singletonInstance = getSingleton(beanName);
        // 判断 singletonInstance 是否实现了 SmartInitializingSingleton 接口
        if (singletonInstance instanceof SmartInitializingSingleton) {
            SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
            if (System.getSecurityManager() != null) {
                // 触发 SmartInitializingSingleton 实现类的 afterSingletonsInstantiated 方法
                AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    smartSingleton.afterSingletonsInstantiated();
                    return null;
                }, getAccessControlContext());
            }
            else {
                smartSingleton.afterSingletonsInstantiated();
            }
        }
    }
}
```

##### factoryBean

factoryBean 是一种特殊的 bean，它是个工厂 bean，可以自己创建 bean 实例，如果一个类实现了 factoryBean 接口，则该类可以自己定义创建实例对象的方法，只需要实现它的 getObject() 方法。为了区分 factoryBean 和 factoryBean 创建的 bean 实例，Spring 通过 “&” 前缀区分。假设我们的 beanName 为 apple，则 getBean("apple") 获得的是 AppleFactoryBean 通过 getObject() 方法创建的 bean 实例；而 getBean("&apple") 获得的是 AppleFactoryBean 本身。

```java
// FactoryBean.java
public interface FactoryBean<T> {

    String OBJECT_TYPE_ATTRIBUTE = "factoryBeanObjectType";

    // 该 factory 生产的 bean 实例
    @Nullable
    T getObject() throws Exception;

    // 该 factory 生产的 bean class
    @Nullable
    Class<?> getObjectType();

    // 该Factory生产的Bean是否为单例
    default boolean isSingleton() {
        return true;
    }

}
```

##### getBean

下面的重点是 getBean 方法，它调用的 doGetBean 方法是真正进行实例化 bean 的地方。

```java
# AbstractBeanFactory.java#getBean
@Override
public Object getBean(String name) throws BeansException {
    return doGetBean(name, null, null, false);
}

# AbstractBeanFactory.java#doGetBean
@SuppressWarnings("unchecked")
protected <T> T doGetBean(
        String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
        throws BeansException {

    // 解析 beanName，主要是解析别名、去掉 FactoryBean 的前缀 "&""
    String beanName = transformedBeanName(name);
    Object bean;

    // 尝试从缓存中获取 beanName 对应的实例
    Object sharedInstance = getSingleton(beanName);
    // 判断是否从缓存中获取到了 beanName 实例
    if (sharedInstance != null && args == null) {
        if (logger.isTraceEnabled()) {
            // 判断 bean 是否正在创建，也就是判断是不是循环依赖了。
            if (isSingletonCurrentlyInCreation(beanName)) {
                logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
                        "' that is not fully initialized yet - a consequence of a circular reference");
            }
            else {
                logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
            }
        }
        // 返回 beanName 对应的实例对象（主要用于 FactoryBean 的特殊处理，普通 Bean 会直接返回 sharedInstance 本身）
        bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
    }

    else {
        // scope 为 prototype 的循环依赖校验：如果 beanName 已经正在创建 Bean 实例中，而此时我们又要再一次创建 beanName 的实例，则代表出现了循环依赖，需要抛出异常。
        if (isPrototypeCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }

        // Check if bean definition exists in this factory.
        // 获取 parentBeanFactory
        BeanFactory parentBeanFactory = getParentBeanFactory();
        // 如果 parentBeanFactory 存在，并且 beanName 在当前 BeanFactory 不存在 Bean 定义，则尝试从 parentBeanFactory 中获取 bean 实例
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // Not found -> check parent.
            //  将别名解析成真正的 beanName
            String nameToLookup = originalBeanName(name);
            // 尝试在 parentBeanFactory 中获取 bean 实例
            if (parentBeanFactory instanceof AbstractBeanFactory) {
                return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
                        nameToLookup, requiredType, args, typeCheckOnly);
            }
            else if (args != null) {
                // Delegation to parent with explicit args.
                return (T) parentBeanFactory.getBean(nameToLookup, args);
            }
            else if (requiredType != null) {
                // No args -> delegate to standard getBean method.
                return parentBeanFactory.getBean(nameToLookup, requiredType);
            }
            else {
                return (T) parentBeanFactory.getBean(nameToLookup);
            }
        }

        if (!typeCheckOnly) {
            // 如果不是仅仅做类型检测，而是创建 bean 实例，这里要将 beanName 放到 alreadyCreated 缓存
            markBeanAsCreated(beanName);
        }

        try {
            // 根据 beanName 重新获取 MergedBeanDefinition
            RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
            // 检查 MergedBeanDefinition
            checkMergedBeanDefinition(mbd, beanName, args);

            // Guarantee initialization of beans that the current bean depends on.
            // 拿到当前 bean 依赖的 bean 名称集合，在实例化自己之前，需要先实例化自己依赖的 bean
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
                // 遍历当前 bean 依赖的 bean 名称集合
                for (String dep : dependsOn) {
                    // 检查 dep 是否依赖于 beanName，即检查是否存在循环依赖
                    if (isDependent(beanName, dep)) {
                        // 如果是循环依赖则抛异常
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
                    }
                    //  将 dep 和 beanName 的依赖关系注册到缓存中
                    registerDependentBean(dep, beanName);
                    try {
                        // 获取 dep 对应的 bean 实例，如果 dep 还没有创建 bean 实例，则创建 dep 的 bean 实例
                        getBean(dep);
                    }
                    catch (NoSuchBeanDefinitionException ex) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
                    }
                }
            }

            // Create bean instance.
            // 针对不同的 scope 进行 bean 的创建
            if (mbd.isSingleton()) {
                // scope 为 singleton 的 bean 创建（新建了一个 ObjectFactory，并且重写了 getObject 方法）
                sharedInstance = getSingleton(beanName, () -> {
                    try {
                        // 创建 Bean 实例
                        return createBean(beanName, mbd, args);
                    }
                    catch (BeansException ex) {
                        // Explicitly remove instance from singleton cache: It might have been put there
                        // eagerly by the creation process, to allow for circular reference resolution.
                        // Also remove any beans that received a temporary reference to the bean.
                        destroySingleton(beanName);
                        throw ex;
                    }
                });
                // 返回 beanName 对应的实例对象
                bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
            }

            else if (mbd.isPrototype()) {
                // scope 为 prototype 的 bean 创建
                // It's a prototype -> create a new instance.
                Object prototypeInstance = null;
                try {
                    // 创建实例前的操作（将 beanName 保存到 prototypesCurrentlyInCreation 缓存中）
                    beforePrototypeCreation(beanName);
                    // 创建 Bean 实例
                    prototypeInstance = createBean(beanName, mbd, args);
                }
                finally {
                    // 创建实例后的操作（将创建完的 beanName 从 prototypesCurrentlyInCreation 缓存中移除）
                    afterPrototypeCreation(beanName);
                }
                // 返回 beanName 对应的实例对象
                bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
            }

            else {
                // 其他 scope 的 bean 创建
                // 根据 scopeName，从缓存拿到 scope 实例
                String scopeName = mbd.getScope();
                if (!StringUtils.hasLength(scopeName)) {
                    throw new IllegalStateException("No scope name defined for bean ´" + beanName + "'");
                }
                Scope scope = this.scopes.get(scopeName);
                if (scope == null) {
                    throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
                }
                try {
                    // 其他 scope 的 bean 创建（新建了一个 ObjectFactory，并且重写了 getObject 方法）
                    Object scopedInstance = scope.get(beanName, () -> {
                        // 创建实例前的操作（将 beanName 保存到 prototypesCurrentlyInCreation 缓存中）
                        beforePrototypeCreation(beanName);
                        try {
                            // 创建bean实例
                            return createBean(beanName, mbd, args);
                        }
                        finally {
                            // 创建实例后的操作（将创建完的 beanName 从 prototypesCurrentlyInCreation 缓存中移除）
                            afterPrototypeCreation(beanName);
                        }
                    });
                    // 返回 beanName 对应的实例对象
                    bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                }
                catch (IllegalStateException ex) {
                    throw new BeanCreationException(beanName,
                            "Scope '" + scopeName + "' is not active for the current thread; consider " +
                            "defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                            ex);
                }
            }
        }
        catch (BeansException ex) {
            // 如果创建 bean 实例过程中出现异常，则将 beanName 从 alreadyCreated 缓存中移除
            cleanupAfterBeanCreationFailure(beanName);
            throw ex;
        }
    }

    // Check if required type matches the type of the actual bean instance.
    // 检查所需类型是否与实际的 bean 对象的类型匹配
    if (requiredType != null && !requiredType.isInstance(bean)) {
        try {
            // 类型不对，则尝试转换bean类型
            T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
            if (convertedBean == null) {
                throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
            }
            return convertedBean;
        }
        catch (TypeMismatchException ex) {
            if (logger.isTraceEnabled()) {
                logger.trace("Failed to convert bean '" + name + "' to required type '" +
                        ClassUtils.getQualifiedName(requiredType) + "'", ex);
            }
            throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
        }
    }
    // 返回创建出来的 bean 实例对象
    return (T) bean; 
}
```

##### getSingleton

getSingleton 方法会尝试从缓存中获取 beanName 对应的实例，从三级缓存中逐层获取。

```java
// AbstractBeanFactory.java#doGetBean
Object sharedInstance = getSingleton(beanName);

// AbstractBeanFactory.java#getSingleton
public Object getSingleton(String beanName) {
    return getSingleton(beanName, true);
}

// AbstractBeanFactory.java#getSingleton
protected Object getSingleton(String beanName, boolean allowEarlyReference) {
    // 首先检查 beanName 对应的 bean 实例是否在一级缓存中存在，如果已经存在，则直接返回
    Object singletonObject = this.singletonObjects.get(beanName);
    // 如果缓存中不存在，而且 bean 正在创建
    if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
        // 从二级缓存中尝试获取
        singletonObject = this.earlySingletonObjects.get(beanName);
        if (singletonObject == null && allowEarlyReference) {
            // 如果缓存中不存在加锁，避免重复创建单例对象
            synchronized (this.singletonObjects) {
                // 尝试从一级缓存中获取 bean 实例
                singletonObject = this.singletonObjects.get(beanName);
                if (singletonObject == null) {
                    // 尝试从二级缓存
                    singletonObject = this.earlySingletonObjects.get(beanName);
                    if (singletonObject == null) {
                        // 如果一级缓存、二级缓存都没获取到：从三级缓存中获取 ObjectFactory
                        ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                        if (singletonFactory != null) {
                            // 提供三级缓存中的 ObjectFactory 获取 bean 实例
                            singletonObject = singletonFactory.getObject();
                            // 更新二级缓存
                            this.earlySingletonObjects.put(beanName, singletonObject);
                            // 移除三级缓存中对应 beanFactory
                            this.singletonFactories.remove(beanName);
                        }
                    }
                }
            }
        }
    }
    return singletonObject;
}
```

这里出现了三级缓存：

- 一级缓存：保存所有的 singletonBean 的实例
- 二级缓存：保存所有早期创建的 Bean 实例，这个 Bean 还没有完成依赖注入
- 三级缓存：singletonBean 的生产工厂

其中特别需要关注的是 singletonFactories，它的 value 中保存的类是 ObjectFactory，这是一个函数式接口，是用于返回对象的工厂接口。
实际上，这个 ObjectFactory 是个 Lambda 表达式：() -> getEarlyBeanReference(beanName, mbd, bean)。

getEarlyBeanReference 做了两件事：

- 根据 beanName 将它对应的实例化后且未初始化完的 bean，存入 Map<Object, Object> earlyProxyReferences = new ConcurrentHashMap<>(16);（earlyProxyReferences 用于记录哪些 Bean 执行过 AOP，防止后期再次对 Bean 进行 AOP）
- 生成该 Bean 对应的代理类返回。

后续再将该代理对象放入第二级缓存中，也就是 Map<String, Object> earlySingletonObjects 里，由于该代理类也是为初始化的，所以不放到一级缓存中。

```java
/** Cache of singleton objects: bean name to bean instance. */
// 一级缓存：保存所有的 singletonBean 的实例
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

/** Cache of singleton factories: bean name to ObjectFactory. */
// 三级缓存：singletonBean 的生产工厂
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

/** Cache of early singleton objects: bean name to bean instance. */
// 二级缓存：保存所有早期创建的 Bean 实例，这个 Bean 还没有完成依赖注入
private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

// ObjectFactory.java
@FunctionalInterface
public interface ObjectFactory<T> {
    
	T getObject() throws BeansException;

}

// () -> getEarlyBeanReference(beanName, mbd, bean) 为 ObjectFactory 的 Lambda 写法
addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));

// AbstractAutowireCapableBeanFactory.java#getEarlyBeanReference
protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
    Object exposedObject = bean;
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (BeanPostProcessor bp : getBeanPostProcessors()) {
            if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
                SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
                exposedObject = ibp.getEarlyBeanReference(exposedObject, beanName);
            }
        }
    }
    return exposedObject;
}

// AbstractAutoProxyCreator.java#getEarlyBeanReference
@Override
public Object getEarlyBeanReference(Object bean, String beanName) {
    Object cacheKey = getCacheKey(bean.getClass(), beanName);
    this.earlyProxyReferences.put(cacheKey, bean);
    return wrapIfNecessary(bean, beanName, cacheKey);
}

// AbstractAutoProxyCreator.java#wrapIfNecessary
protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
    if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
        return bean;
    }
    if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
        return bean;
    }
    if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
        this.advisedBeans.put(cacheKey, Boolean.FALSE);
        return bean;
    }

    // Create proxy if we have advice.
    Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
    if (specificInterceptors != DO_NOT_PROXY) {
        this.advisedBeans.put(cacheKey, Boolean.TRUE);
        Object proxy = createProxy(
                bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
        this.proxyTypes.put(cacheKey, proxy.getClass());
        return proxy;
    }

    this.advisedBeans.put(cacheKey, Boolean.FALSE);
    return bean;
}
```

如果从三级缓存中没有获取到 bean 实例，就需要继续往下走。

##### createBean

下面会对 beanName 做 scope 循环依赖校验并重写获取 MergedBeanDefinition，最终执行 createBean 方法，顾名思义，创建 bean。进入 createBean 方法中，doCreateBean 方法是真正创建 bean 实例的方法。

```java
// AbstractAutowireCapableBeanFactory.java#createBean
@Override
protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
        throws BeanCreationException {

    if (logger.isTraceEnabled()) {
        logger.trace("Creating instance of bean '" + beanName + "'");
    }
    RootBeanDefinition mbdToUse = mbd;

    // Make sure bean class is actually resolved at this point, and
    // clone the bean definition in case of a dynamically resolved Class
    // which cannot be stored in the shared merged bean definition.
    // 根据 beanName（全路径） 解析 bean
    Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
    if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
        // 如果resolvedClass存在，并且mdb的beanClass类型不是Class，并且mdb的beanClass不为空（则代表beanClass存的是Class的name）,
        // 则使用mdb深拷贝一个新的RootBeanDefinition副本，并且将解析的Class赋值给拷贝的RootBeanDefinition副本的beanClass属性，
        // 该拷贝副本取代mdb用于后续的操作
        mbdToUse = new RootBeanDefinition(mbd);
        mbdToUse.setBeanClass(resolvedClass);
    }

    // Prepare method overrides.
    try {
        // 对 @override 属性进行标记及验证
        mbdToUse.prepareMethodOverrides();
    }
    catch (BeanDefinitionValidationException ex) {
        throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
                beanName, "Validation of method overrides failed", ex);
    }

    try {
        // Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
        // 实例化前的处理，给 InstantiationAwareBeanPostProcessor 一个机会返回代理对象来替代真正的 bean 实例
        Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
        if (bean != null) {
            return bean;
        }
    }
    catch (Throwable ex) {
        throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
                "BeanPostProcessor before instantiation of bean failed", ex);
    }

    try {
        // 创建Bean实例（真正创建 Bean 的方法)
        Object beanInstance = doCreateBean(beanName, mbdToUse, args);
        if (logger.isTraceEnabled()) {
            logger.trace("Finished creating instance of bean '" + beanName + "'");
        }
        return beanInstance;
    }
    catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
        // A previously detected exception with proper bean creation context already,
        // or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
        throw ex;
    }
    catch (Throwable ex) {
        throw new BeanCreationException(
                mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
    }
}

// AbstractAutowireCapableBeanFactory.java#doCreateBean
protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
        throws BeanCreationException {

    // Instantiate the bean.
    // 声明 Bean 包装类
    BeanWrapper instanceWrapper = null;
    if (mbd.isSingleton()) {
        // 如果是 FactoryBean，则需要先移除未完成的 FactoryBean 实例的缓存
        instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
    }
    if (instanceWrapper == null) {
        // 根据 beanName、mbd、args，使用对应的策略创建 bean 实例，并返回包装类 BeanWrapper
        instanceWrapper = createBeanInstance(beanName, mbd, args);
    }
    // 创建好的 Bean 实例
    Object bean = instanceWrapper.getWrappedInstance();
    // Bean 实例的类型
    Class<?> beanType = instanceWrapper.getWrappedClass();
    if (beanType != NullBean.class) {
        mbd.resolvedTargetType = beanType;
    }

    // Allow post-processors to modify the merged bean definition.
    synchronized (mbd.postProcessingLock) {
        if (!mbd.postProcessed) {
            try {
                // 应用后置处理器 MergedBeanDefinitionPostProcessor，允许修改 MergedBeanDefinition，
                // Autowired 注解正是通过此方法实现注入类型的预解析
                applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
            }
            catch (Throwable ex) {
                throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                        "Post-processing of merged bean definition failed", ex);
            }
            mbd.postProcessed = true;
        }
    }

    // Eagerly cache singletons to be able to resolve circular references
    // even when triggered by lifecycle interfaces like BeanFactoryAware.
    // 判断是否需要提早曝光实例：单例 && 允许循环依赖 && 当前 bean 正在创建中
    boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
            isSingletonCurrentlyInCreation(beanName));
    if (earlySingletonExposure) {
        if (logger.isTraceEnabled()) {
            logger.trace("Eagerly caching bean '" + beanName +
                    "' to allow for resolving potential circular references");
        }
        // 提前曝光 bean 的 ObjectFactory，用于解决循环依赖
        addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }

    // Initialize the bean instance.
    Object exposedObject = bean;
    try {
        // 对 bean 进行属性填充；其中，可能存在依赖于其他 bean 的属性，则会递归初始化依赖的 bean 实例
        populateBean(beanName, mbd, instanceWrapper);
        // 对 bean 进行初始化
        exposedObject = initializeBean(beanName, exposedObject, mbd);
    }
    catch (Throwable ex) {
        if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
            throw (BeanCreationException) ex;
        }
        else {
            throw new BeanCreationException(
                    mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
        }
    }

    if (earlySingletonExposure) {
        // 如果允许提前曝光实例，则进行循环依赖检查
        Object earlySingletonReference = getSingleton(beanName, false);
        // earlySingletonReference 只有在当前解析的 bean 存在循环依赖的情况下才会不为空
        if (earlySingletonReference != null) {
            if (exposedObject == bean) {
                // 如果 exposedObject 没有在 initializeBean 方法中被增强，则不影响之前的循环引用
                exposedObject = earlySingletonReference;
            }
            else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
                // 如果 exposedObject 在 initializeBean 方法中被增强 && 不允许在循环引用的情况下使用注入原始 bean 实例 && 当前 bean 有被其他 bean 依赖
                
                // 拿到依赖当前 bean 的所有 bean 的 beanName 数组
                String[] dependentBeans = getDependentBeans(beanName);
                Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
                for (String dependentBean : dependentBeans) {
                    // 尝试移除这些 bean 的实例，因为这些 bean 依赖的 bean 已经被增强了，他们依赖的 bean 相当于脏数据
                    if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                        // 移除失败的添加到 actualDependentBeans
                        actualDependentBeans.add(dependentBean);
                    }
                }
                if (!actualDependentBeans.isEmpty()) {
                    // 如果存在移除失败的，则抛出异常，因为存在 bean 依赖了 "脏数据""
                    throw new BeanCurrentlyInCreationException(beanName,
                            "Bean with name '" + beanName + "' has been injected into other beans [" +
                            StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
                            "] in its raw version as part of a circular reference, but has eventually been " +
                            "wrapped. This means that said other beans do not use the final version of the " +
                            "bean. This is often the result of over-eager type matching - consider using " +
                            "'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
                }
            }
        }
    }

    // Register bean as disposable.
    try {
        // 注册用于销毁的 bean，执行销毁操作的有三种：自定义 destroy 方法、DisposableBean 接口、DestructionAwareBeanPostProcessor
        registerDisposableBeanIfNecessary(beanName, bean, mbd);
    }
    catch (BeanDefinitionValidationException ex) {
        throw new BeanCreationException(
                mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
    }
    // 完成创建并返回
    return exposedObject;
}
```

##### doCreateBean

在 doCreateBean 方法中 createBeanInstance 方法用于创建 bean 实例包含以下几种：工厂方法、构造函数自动装配（通常指带有参数的构造函数）、简单实例化（默认的构造函数）。

```java
// AbstractAutowireCapableBeanFactory.java#createBeanInstance
protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
    // Make sure bean class is actually resolved at this point.
    // 解析bean的类型信息
    Class<?> beanClass = resolveBeanClass(mbd, beanName);

    if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
        // beanClass 不为空 && beanClass 不是公开类（不是 public 修饰） && 该 bean 不允许访问非公共构造函数和方法，则抛异常
        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                "Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
    }

    Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
    if (instanceSupplier != null) {
        return obtainFromSupplier(instanceSupplier, beanName);
    }

    // 如果存在工厂方法则使用工厂方法实例化 bean 对象
    if (mbd.getFactoryMethodName() != null) {
        return instantiateUsingFactoryMethod(beanName, mbd, args);
    }

    // Shortcut when re-creating the same bean...
    // resolved: 构造函数或工厂方法是否已经解析过
    boolean resolved = false;
    boolean autowireNecessary = false;
    if (args == null) {
        // 加锁
        synchronized (mbd.constructorArgumentLock) {
            // 如果 resolvedConstructorOrFactoryMethod 缓存不为空，则将 resolved 标记为已解析
            if (mbd.resolvedConstructorOrFactoryMethod != null) {
                resolved = true;
                autowireNecessary = mbd.constructorArgumentsResolved;
            }
        }
    }
    if (resolved) {
        // 如果已经解析过，则使用 resolvedConstructorOrFactoryMethod 缓存里解析好的构造函数方法
        if (autowireNecessary) {
            // / 需要自动注入，则执行构造函数自动注入
            return autowireConstructor(beanName, mbd, null, null);
        }
        else {
            // // 否则使用默认的构造函数进行bean的实例化
            return instantiateBean(beanName, mbd);
        }
    }

    // Candidate constructors for autowiring?
    // 应用后置处理器 SmartInstantiationAwareBeanPostProcessor，拿到 bean 的候选构造函数
    Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
    if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
            mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
        // 如果 ctors 不为空 || mbd 的注入方式为 AUTOWIRE_CONSTRUCTOR || mdb 定义了构造函数的参数值 || args 不为空，则执行构造函数自动注入
        return autowireConstructor(beanName, mbd, ctors, args);
    }

    // Preferred constructors for default construction?
    ctors = mbd.getPreferredConstructors();
    if (ctors != null) {
        return autowireConstructor(beanName, mbd, ctors, null);
    }

    // No special handling: simply use no-arg constructor.
    // 没有特殊处理，则使用默认的构造函数进行bean的实例化
    return instantiateBean(beanName, mbd);
}
```

目前，点到为止，来日方长。

### 如何解决循环依赖  <!-- https://www.zhihu.com/question/438247718 -->

我们从上面的分析已经知道通过三级缓存可以解决循环依赖，但是为什么是三级缓存，而不是二级缓存，或者其他方式，这也是需要思考的。

#### 解决思路

循环依赖的问题在于创建对象需要先实例化依赖的对象，依赖的对象如果也依赖自己，那就要先实例化自己在实例化对方，这就是一个死环。

如果我们可以先创建对象（new），之后再初始化，实例化阶段注入依赖对象的引用，虽然依赖的对象还没有（未初始化赋值）彻底实例化。这样的确可以解决部分依赖注入的问题，即通过 set 注入的依赖，对于构造函数循环依赖目前是无解的。

Spring 中解决循环依赖正是这种思路：实例化和初始化分离。Spring 管理 Bean 的实例化底层是由反射实现的，先实例化出对象，最后再初始化赋值。但构造器循环依赖的确尚未解决。

#### 什么是三级缓存

三级缓存：

- 一级缓存：保存所有的 singletonBean 的实例
- 二级缓存：保存所有早期创建的 Bean 实例，这个 Bean 还没有完成依赖注入
- 三级缓存：singletonBean 的生产工厂

其中特别需要关注的是 singletonFactories，它的 value 中保存的类是 ObjectFactory，这是一个函数式接口，是用于返回对象的工厂接口。
实际上，这个 ObjectFactory 是个 Lambda 表达式：() -> getEarlyBeanReference(beanName, mbd, bean)。

```java
/** Cache of singleton objects: bean name to bean instance. */
// 一级缓存：保存所有的 singletonBean 的实例
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

/** Cache of singleton factories: bean name to ObjectFactory. */
// 三级缓存：singletonBean 的生产工厂
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

/** Cache of early singleton objects: bean name to bean instance. */
// 二级缓存：保存所有早期创建的 Bean 实例，这个 Bean 还没有完成依赖注入
private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

// ObjectFactory.java
@FunctionalInterface
public interface ObjectFactory<T> {
    
	T getObject() throws BeansException;

}

// () -> getEarlyBeanReference(beanName, mbd, bean) 为 ObjectFactory 的 Lambda 写法
addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
```

Spring 管理的 Bean 其实默认都是单例的，也就是说 Spring 将最终可以使用的 Bean 统一放入第一级缓存中，也就是 singletonObjects（单例池）里，以后凡是用到某个 Bean 了都从这里获取就行了。

#### 一级缓存可行性分析

既然单例 bean 都从 singletonObjects 里获取，那么仅仅使用这一个 singletonObjects，可以吗？

肯定不可以的。首先 singletonObjects 存入的是完全初始化好的 Bean，可以拿来直接用的。如果我们直接将未初始化完的 Bean 放在 singletonObjects 里面，注意，这个未初始化完的 Bean 极有可能会被其他的类拿去用，它都没完事呢，就被拿去造了，肯定要出事啊！

#### 二级缓存可行性分析

循环依赖问题本质是 ： A -> B -> A -> B ...

既然一级缓存不行，我们再加一层，二级缓存存放实例化但未初始化完成的 bean，行吗？

流程分析：首先实例化 A，将 A 放到二级缓存中，如果 A 需要依赖 B，B 未从缓存中获取，则 B 进行实例化,放入二级缓存中。当 B 依赖了 A，
从二级缓存中获取了 A，B 实例成功填充了 A，完成了 B 的初始化（清除二级缓存中 B，将 B 加入到一级缓存）。
接下来将 B 填充到 A 中，完成 A 的初始化（清除二级缓存中 A，将 A 加入到一级缓存）。

上面的流程看起来已经成功解决了循环依赖，然而这不是真的。让我们先回顾一下 Spring 管理 Bean 的主要流程：

1. Spring 根据开发人员的配置，扫描哪些类由 Spring 来管理，并为每个类生成一个 BeanDefintion，里面封装了类的一些信息，如全限定类名、哪些属性、是否单例等等。
2. 根据 BeanDefintion 的信息，通过反射，去实例化 Bean（此时就是实例化但未初始化 的 Bean）。
3. 填充上述未初始化对象中的属性（依赖注入）。
4. 如果上述未初始化对象中的方法被 AOP 了，那么就需要生成代理类（也叫包装类）。
5. 最后将完成初始化的对象存入缓存中（此处缓存 Spring 里叫： singletonObjects），下次用从缓存获取 ok 了。

步骤 4 中， Spring 中被 AOP 的方法，会生成代理类，代理类和原本的类不是一个对象，这时候二级缓存就出问题了。

#### 三级缓存可行性分析

我们已经知道，三级缓存是可以的。

我们要特别关注三级缓存，它存储的并不是对象实例，而是对象的生成工厂。创建 bean 时（doCreateBean 方法），如果发现 bean 是单例 bean、存在循环依赖而且正在创建中，就将该 bean 的生产工厂添加到三级缓存中 singletonFactories。

```java
/** Cache of singleton factories: bean name to ObjectFactory. */
// 三级缓存：singletonBean 的生产工厂
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

// ObjectFactory.java
@FunctionalInterface
public interface ObjectFactory<T> {
    
	T getObject() throws BeansException;

}

// () -> getEarlyBeanReference(beanName, mbd, bean) 为 ObjectFactory 的 Lambda 写法
addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
```

流程分析：首先实例化 A，如果 A 需要单例、存在循环依赖且正在创建中，就将 bean 的生产工厂放入三级缓存，如果 A 需要依赖 B，B 未从缓存中获取，则 B 进行实例化。
如果 B 需要单例、存在循环依赖且正在创建中就将 B 添加到三级缓存中，从三级缓存中获取 A 并将 A 填入 B 中（通过三级缓存获取 bean 时，会将这个 bean 加入到二级缓存中），
完成 B 的初始化（清除二级、三级缓存，放入一级缓存）。接下来将 B 填充到 A 中，完成 A 的初始化（清除二级、三级缓存，放入一级缓存）。