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

### 五、观测性（Metrics）

启用 Micrometer（如 Spring Boot Actuator + Prometheus）后，框架会自动输出以下指标，所有指标默认携带 `service`、`method`（可按配置关闭高基数）、以及 `success` 等标签：

| 指标名称 | 说明 | 标签补充 |
| --- | --- | --- |
| `rpic.client.latency` | 客户端调用耗时直方图 | `endpoint` = 目标节点 |
| `rpic.client.requests` / `rpic.client.errors` | 客户端请求总数 / 失败总数 | `error` = 异常类型（失败时） |
| `rpic.client.request.bytes` / `rpic.client.response.bytes` | 客户端请求/响应负载大小 | - |
| `rpic.client.pool.acquire` / `rpic.client.pool.acquire.errors` | 连接池获取成功/失败次数 | `endpoint` |
| `rpic.client.pool.active` | 每个节点的活动连接数 Gauge | `endpoint` |
| `rpic.server.latency` | 服务端处理耗时直方图 | `caller` = 调用方地址 |
| `rpic.server.requests` / `rpic.server.errors` | 服务端请求总数 / 失败总数 | `error` |
| `rpic.server.request.bytes` / `rpic.server.response.bytes` | 服务端接收/发送负载大小 | - |

> 提示：可通过 `rpc.metrics.enabled=false` 关闭指标；`rpc.metrics.high-cardinality-tags-enabled=false` 可避免方法级高基数标签。

### 六、Phase 1 客户端稳健性增强

- **端到端 Deadline**：客户端在单次调用开始时写入 `deadlineAtMillis`，每次重试只消耗剩余时间；服务端若检测到请求已过期会直接返回超时响应，避免无意义的业务执行。
- **指数退避重试**：支持全抖动（full jitter）的指数回退策略，可按异常类型与关键字白名单决定是否重试，默认最大 3 次尝试。
- **熔断与异常实例剔除**：基于 Resilience4j 的每端点熔断器叠加 EWMA/失败率统计，自动隔离高错误率或高延迟节点并定期探活。
- **P2C+EWMA 负载均衡**：新增 `p2c_ewma` 策略，使用“二选一”+延迟指数滑动平均选择更健康的节点，默认示例已启用。
- **指标补充标签**：客户端指标新增 `retried`（低基数）与可选 `attempt`（受高基数开关控制）标签，便于追踪重试行为。

示例配置（位于 `rpic-example-simple/simple-client/application.yml`）：

```yaml
rpc:
    client:
        loadbalance: p2c_ewma
        retry:
            enabled: true
            max-attempts: 3
            base-delay-ms: 50
            max-delay-ms: 1000
            multiplier: 2.0
            jitter-factor: 1.0
        circuit-breaker:
            enabled: true
            failure-rate-threshold: 50
            slow-call-rate-threshold: 50
            slow-call-duration-threshold-ms: 1000
            sliding-window-size: 100
            minimum-number-of-calls: 50
            wait-duration-in-open-state-ms: 10000
        outlier:
            enabled: true
            error-rate-threshold: 0.5
            min-request-volume: 50
            ejection-duration-ms: 30000
            probe-interval-ms: 5000
```

> 验证建议：在示例 server 端模拟部分节点故障或注入延迟，观察客户端重试日志与 `retried=true` 指标计数；通过 Actuator 暴露的 Micrometer 指标对比熔断/剔除前后的端点延迟情况。
