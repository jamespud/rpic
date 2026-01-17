# RPIC - 高性能 RPC 框架

RPIC 是一个基于 Spring Boot 和 Netty 构建的高性能 RPC（远程过程调用）框架，提供了完整的微服务通信解决方案，包括服务注册发现、负载均衡、容错机制和可观测性。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 2.7.10 | 自动配置、依赖注入 |
| Netty | 4.2.6.Final | 高性能网络通信 |
| Nacos | 3.1.0 | 服务注册发现 |
| ZooKeeper | 3.9.4 | 服务注册发现 |
| CGLIB | 3.3.0 | 动态代理 |
| Kryo | 5.6.2 | 序列化 |
| Resilience4j | 2.3.0 | 熔断器 |
| Micrometer | 1.9.11 | 指标收集 |
| Prometheus | - | 指标存储 |
| OpenTelemetry | 1.32.0 | 分布式追踪 |

## 快速开始

### 环境要求

- JDK 8+
- Maven 3.6+
- ZooKeeper 3.9.4+ 或 Nacos 3.1.0+

### 安装与运行

#### 1. 克隆项目

```bash
git clone https://github.com/your-username/rpic.git
cd rpic
```

#### 2. 安装依赖

```bash
mvn clean install -DskipTests
```

#### 3. 启动 ZooKeeper

```bash
# 下载并启动 ZooKeeper
wget https://archive.apache.org/dist/zookeeper/zookeeper-3.9.4/apache-zookeeper-3.9.4-bin.tar.gz
tar -xzf apache-zookeeper-3.9.4-bin.tar.gz
cd apache-zookeeper-3.9.4-bin
bin/zkServer.sh start
```

#### 4. 运行示例

**启动服务端：**
```bash
cd rpic-example/rpic-example-simple/simple-server
mvn spring-boot:run
```

**启动客户端：**
```bash
cd rpic-example/rpic-example-simple/simple-client
mvn spring-boot:run
```

## 核心功能

### 1. 服务定义与注解驱动开发

#### @RpcService（服务提供者）
```java
@Service
@RpcService(version = "1.0.0", weight = 10, timeout = 5000)
public class HelloServiceImpl implements HelloService {
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }
}
```

#### @RpcReference（服务消费者）
```java
@RpcReference(version = "1.0.0", loadbalance = "p2c_ewma", retries = 2)
private HelloService helloService;
```

### 2. 服务注册与发现

- **抽象 Registry 接口**：统一服务注册发现接口
- **支持的注册中心**：NacosRegistry、ZookeeperRegistry
- **缓存机制**：使用 Caffeine 缓存，支持配置 TTL 和刷新间隔

### 3. 高性能网络通信（Netty）

#### 自定义协议设计
```
┌────────┬──────┬──────┬───────────┬──────────┬──────────┐
│ Magic  │ Ver  │ Type │ Serializer│  Length  │  Content │
│ (1byte)│(1byte)│(1byte)│  (1byte)  │ (4bytes) │ (N bytes)│
└────────┴──────┴──────┴───────────┴──────────┴──────────┘
```

### 4. 负载均衡与容错

#### 负载均衡策略
- **P2C+EWMA**：基于延迟的负载均衡（默认推荐）
- **Random**：随机选择
- **RoundRobin**：轮询
- **WeightedRandom**：加权随机
- **WeightedRoundRobin**：加权轮询

#### 容错机制
1. **熔断器（Resilience4j）**
2. **异常节点剔除（Outlier Ejection）**
3. **指数退避重试**

### 5. RPC 调用流程

**CGLIB 动态代理**：
```java
public class RpcInvocationHandler implements MethodInterceptor {
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) {
        // 1. 处理 Object 方法
        // 2. 构建 RPC 请求
        // 3. 处理同步/异步调用
        // 4. 异常处理
    }
}
```

### 6. 可观测性

#### Metrics（Micrometer）
- **客户端指标**：`rpic.client.latency`、`rpic.client.requests`、`rpic.client.errors` 等
- **服务端指标**：`rpic.server.latency`、`rpic.server.requests`、`rpic.server.errors` 等
- **熔断器指标**：`rpic.circuitbreaker.state`、`rpic.circuitbreaker.failure.rate` 等

#### Tracing（OpenTelemetry）
- 支持分布式链路追踪
- 自动注入 Span 上下文

## 使用方法

### 配置文件

**application.yml（客户端）：**
```yaml
rpc:
  role: client
  applicationName: my-app
  serializeType: kryo
  compressType: none
  registry:
    type: zookeeper
    address: localhost:2181
  client:
    loadbalance: p2c_ewma
    timeout: 5000
    retry:
      enabled: true
      maxAttempts: 3
    circuit-breaker:
      enabled: true
      failureRateThreshold: 50
    outlier:
      enabled: true
      errorRateThreshold: 0.5
```

**application.yml（服务端）：**
```yaml
rpc:
  role: server
  server:
    port: 9090
  registry:
    type: zookeeper
    address: localhost:2181
```

### 代码示例

**服务端：**
```java
@Service
@RpcService(version = "1.0.0", weight = 10)
public class HelloServiceImpl implements HelloService {
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }

    public CompletableFuture<String> sayHelloAsync(String name) {
        return CompletableFuture.supplyAsync(() -> sayHello(name));
    }
}
```

**客户端：**
```java
@SpringBootApplication
public class ConsumerApplication implements CommandLineRunner {

    @RpcReference(version = "1.0.0")
    private HelloService helloService;

    public void run(String... args) {
        // 同步调用
        String result = helloService.sayHello("World");

        // 异步调用
        helloService.sayHelloAsync("World")
            .thenAccept(r -> System.out.println("异步结果: " + r));
    }
}
```

## 架构设计

### 整体架构

项目采用 Maven 多模块结构：
- **rpic-spring-boot-starter**：核心 RPC 框架
- **rpic-example**：示例应用，包含服务端、客户端和 API 模块

### 核心组件

1. **服务定义与注解层**：@RpcService、@RpcReference
2. **服务注册与发现**：Registry 接口、ZookeeperRegistry、NacosRegistry
3. **网络通信**：NettyNetClient、NettyNetServer、ProtocolMsg
4. **RPC 调用**：RpcInvocationHandler、DefaultClientInvocation、DefaultServerInvocation
5. **负载均衡**：LoadBalancer、P2CEWMALoadBalancer
6. **容错机制**：CircuitBreakerManager、EndpointStatsRegistry
7. **可观测性**：RpcMetricsRecorder、RpcTracer

## 贡献指南

### 开发流程

1. Fork 项目
2. 创建功能分支：`git checkout -b feature/your-feature`
3. 提交修改：`git commit -m "Add your feature"`
4. 推送到分支：`git push origin feature/your-feature`
5. 提交 Pull Request

### 代码规范

- 使用 Java 8+ 语法
- 遵循 Spring Boot 代码风格
- 注释清晰，文档完整
- 编写单元测试

### 常用命令

```bash
# 编译项目
mvn compile

# 运行测试
mvn test

# 打包项目
mvn clean package -DskipTests

# 运行示例
cd rpic-example/rpic-example-simple/simple-server
mvn spring-boot:run
```

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。
