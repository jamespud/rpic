## RPC框架执行流程分析

### 一、生产者（服务提供方）执行流程

1. **服务定义与注册**
    - 使用`@RpcService`注解标记服务实现类，定义服务接口、版本、分组等信息
    - Spring容器启动后，`ServiceStarter`类监听`ContextRefreshedEvent`事件
    - 扫描所有带有`@RpcService`注解的Bean，收集服务元数据（ServiceMetadata）
    - 将服务元数据注册到注册中心（支持Zookeeper和Nacos）

2. **服务启动**
    - 启动Netty服务器（NettyNetServer）监听指定端口
    - 初始化`RpcServerInitializer`和`RpcServerHandler`处理客户端请求
    - 服务启动后等待客户端连接和调用

3. **请求处理流程**
    - 客户端请求到达后，首先由Netty解码器解析协议消息（ProtocolMsg）
    - `RpcServerHandler`处理解码后的消息，反序列化为`RpcRequest`对象
    - 调用`DefaultServerInvocation`执行实际服务调用：
        1. 验证请求有效性
        2. 通过反射获取目标服务实例和方法
        3. 执行方法调用
        4. 将调用结果封装为`RpcResponse`
        5. 序列化响应并返回给客户端

4. **并发控制**
    - 使用信号量（Semaphore）控制最大并发请求数
    - 当请求超过限制时，快速返回错误响应

5. **方法缓存**
    - 使用`methodCache`缓存已解析的方法，提高反射性能

### 二、消费者（服务调用方）执行流程

1. **服务引用和发现**
    - 使用`@RpcReference`注解标记需要远程调用的接口
    - `RpcReferenceAnnotationProcessor`实现`BeanPostProcessor`接口
    - 在Bean初始化前，扫描带有`@RpcReference`注解的字段
    - 为每个引用创建动态代理对象，并注入到相应字段

2. **代理创建**
    - 使用Cglib创建动态代理（CglibProxyFactory）
    - 代理对象拦截所有方法调用，转发到`RpcInvocationHandler`

3. **远程调用流程**
    - 当调用代理对象方法时，`RpcInvocationHandler.intercept`方法被触发：
        1. 构建RPC请求（RpcRequest）包含接口名、方法名、参数等
        2. 构建服务元数据（ServiceMetadata）
        3. 调用`ClientInvocation.invoke`执行远程调用

4. **服务发现和负载均衡**
    - 从注册中心获取服务实例列表（默认缓存一段时间）
    - 通过负载均衡策略选择一个服务实例
    - 支持失败重试机制，可配置重试次数

5. **网络通信**
    - 通过`NettyNetClient`建立与服务端的连接
    - 使用连接池（ConnectionPool）管理和复用连接
    - 发送序列化后的请求并等待响应
    - 处理响应结果并返回

6. **异常处理**
    - 捕获并处理网络超时、服务不可用等异常
    - 支持重试机制，失败后尝试其他服务实例

### 三、关键组件详解

1. **注册中心（Registry）**
    - 抽象接口设计，支持多种实现（ZookeeperRegistry、NacosRegistry）
    - 提供服务注册、发现、订阅功能
    - 使用缓存机制（CaffeineCache）提高服务发现性能
    - 支持服务变更通知机制（ServiceChangeListener）

2. **序列化机制**
    - 支持多种序列化协议（Jackson、Hessian、Kryo）
    - 通过Serializer接口统一抽象

3. **负载均衡（LoadBalancer）**
    - 选择合适的服务实例进行调用
    - 支持排除已尝试失败的实例

4. **网络通信**
    - 基于Netty实现高性能网络通信
    - 自定义协议消息格式（ProtocolMsg）
    - 客户端支持连接池和请求合并

5. **Spring集成**
    - 通过Spring Boot自动配置简化使用
    - `RpcServerAutoConfiguration`配置服务端
    - `RpcClientAutoConfiguration`配置客户端
    - 支持通过配置文件定制RPC行为

### 四、完整调用流程

#### 生产者端：
1. 标记服务类或方法为`@RpcService`
2. Spring启动时自动注册服务到注册中心
3. 启动Netty服务器监听请求
4. 收到请求后反序列化、查找服务实现、执行调用
5. 序列化结果并返回响应

#### 消费者端：
1. 在类中使用`@RpcReference`注解引用远程服务
2. Spring启动时创建动态代理并注入到引用字段
3. 调用服务方法时，代理拦截调用并构建RPC请求
4. 从注册中心发现服务地址，选择一个实例
5. 发送请求到服务实例并等待响应
6. 处理响应结果（成功返回结果，失败抛出异常）
