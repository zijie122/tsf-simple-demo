## 集成方式概览

| 功能       | 云原生应用                             | 普通应用               |
| ---------- | -------------------------------------- | ---------------------- |
| 适用场景   | 存量业务应用开源Spring Cloud零代码改造 | 新业务全新技术框架选型 |
| 集成方式   | Mesh流量劫持                           | 框架SDK                |
| 注册发现   | ✓                                      | ✓                      |
| 服务鉴权   | ✓                                      | ✓                      |
| 服务限流   | ✓                                      | ✓                      |
| 服务熔断   | ✓                                      | ✓                      |
| 服务路由   | ✓                                      | ✓                      |
| 调用链     | ✓ 支持服务级别                         | ✓                      |
| 日志服务   | ✓                                      | ✓                      |
| 配置管理   | 不支持                                 | ✓                      |
| 优雅下线   | ✓                                      | ✓                      |
| 全链路灰度 | ✓                                      | ✓                      |
| 单元化     | 不支持                                 | ✓                      |

## 背景介绍

某证券行业的客户在对自身的微服务应用进行云迁移，需要支持的微服务应用包含: 多版本的SpringCloud应用，其他语言(Go Python)应用，以及需要部署在虚拟机上的应用等

在大的信创背景以及自身应用架构下，客户选择了基于TCS私有云平台的TSF一站式微服务框架进行集成，结合常用的微服务组件相关功能我们针对目前TSF提供的**框架SDK**和**云原生Mesh应用**两种集成方式进行调研

## 版本配套关系

https://cloud.tencent.com/document/product/649/36285

SpringCloud Version: https://spring.io/projects/spring-cloud

```
jdk8
springboot 2.7.18
springcloud 2021.0.7
TSF SDK 1.46.x
```

## 工程目录

| 工程名称      | 工程说明                          |
| ------------- | --------------------------------- |
| consumer-demo | TSF微服务治理服务消费者           |
| provider-demo | TSF微服务治理服务提供者           |
| consul-demo   | 基于开源 SpringCloudConsul 的示例 |

## 本地轻量服务注册中心

> 需要在本地安装好maven + docker compose

- 打包**consul-demo**

  ```sh
  cd consul-demo
  mvn clean package
  ```

- 启动**consul-server**和**consul-demo**

  ```sh
  # 回到项目根目路
  docker-compose -f docker/consul/docker-compose.yml up -d
  ```

- 访问 http://localhost:8500/ui 和 http://localhost:18085/test





> 以下介绍中所有加了⚠️的内容都是根据逻辑的猜测，没有基于真实的TSF环境进行测试
>





## TSF框架SDK

https://cloud.tencent.com/document/product/649

> ⚠️ 框架SDK和SpringCloudTencent集成区别在于框架SDK更针对SpringCloud应用，而SpringCloudTencent可支持普通的java应用进行云化并提供TSF的一站式功能
>

#### 前置配置

* [x] 技术栈选型 https://cloud.tencent.com/document/product/649/73790

* [x] Maven配置TSF私服地址

* [x] 引入依赖

* [x] **添加启动注解`@EnableTsf`**包括了

```java
@EnableTsfMonitor // 服务监控
@EnableTsfUnit // 单元化
@EnableTsfLane //泳道
@EnableTsfAuth // 服务鉴权
@EnableTsfSleuth // 调用链

@EnableTsfRoute // 服务路由
@EnableTsfRateLimit // 服务限流
@EnableTsfCircuitBreaker // 服务熔断
@EnableTsfFaultTolerance // 服务容错
```

#### 服务注册

运行在TSF平台上的应用无须配置服务注册中心地址，SDK会通过**环境变量**自动获取注册中心地址

TSF搭建时会将内置的consul注册中心地址写入环境变量，应用部署在TSF上后自动获取注入

如果是其他类型的注册中心(如Eureka)迁移到TSF需要进行依赖修改和注解更新以支持TSF的consul

https://cloud.tencent.com/document/product/649/16617#.E4.BB.8E-eureka-.E8.BF.81.E7.A7.BB.E5.BA.94.E7.94.A8

#### 配置管理

- 热更新
  - 需要使用`@RefreshScope`
- 配置回调
  - 允许程序在不重启的情况下动态修改业务逻辑
  - 配置更新触发回调功能允许程序在不重启的情况下动态修改业务逻辑。当配置更新时，触发配置回调方法的调用

```java
// prefix： 配置信息前缀，前缀匹配
// async: 回调事件是否异步执行，默认 false
// value： 精确匹配配置项
@ConfigChangeListener(prefix = "io",async = true)
public class SimpleConfigurationListener implements ConfigChangeCallback {
    @Override
    public void callback(ConfigProperty lastConfigItem, ConfigProperty currentConfigItem) {
    }
}
```

`com.tencent.tsf.consul.config.watch.ConfigChangeCallback`

`com.tencent.tsf.consul.config.ConfigWatch` 监听Consul的配置变化

> 该功能为TSF内置功能，原生consul框架也有相同的ConfigWatch逻辑获取配置变更，可见consul-demo

#### 监控

监控指标: https://cloud.tencent.com/document/product/649/72802

#### 调用链

- 支持用户在代码中设置标签(Tag)和自定义元数据(CustomMetada)，分别用于调用链的筛选和附带业务信息

- 支持业务应用服务注入`Tracer`并获取traceId、spanId、tag信息

- 调整采样率

  ```yaml
  tsf:
    sleuth:
      samplerRate: 0.1 # 默认为1
  ```

必须部署到TSF中才会生成traceid和spanid到MDC

- 配置日志 pattern

```yaml
logging:
  pattern:
    level: "%-5level [${spring.application.name},%mdc{trace_id},%mdc{span_id},]"
```

#### 服务鉴权

https://cloud.tencent.com/document/product/649/16621#.E6.9C.8D.E5.8A.A1.E9.89.B4.E6.9D.83

- 请求双方都需要开启注解
- 调用方(consumer)和被调方(provider)想使用基本tag的鉴权规则:
  - consumer需要通过`TsfContext`设置tag， provider需要在控制台上设置tag鉴权规则

#### 服务路由

https://cloud.tencent.com/document/product/649/16621

有两种方式来实现服务路由:

1. 在TSF平台配置路由规则

- 添加依赖及注解`@EnableTsfRoute`(服务提供方)
- 在TSF控制台维护路由规则，规则需要配置在服务提供方

2. 基于自定义标签配置路由规则

- 添加依赖及注解`@EnableTsfRoute`(服务调用方和提供方)
- 服务调用方配置好相应规则的内容，通过`TsfContext`进行自定义标签的设置

> TSF新版本(2.0.0 Release)中，`@EnableTsf`及下属注解都被标记为废弃，目前尚未找到替代的注解
>
> ⚠️ 猜测有可能TSF平台发展后续是否倾向于删除这些注解

#### 服务限流

https://cloud.tencent.com/document/product/649/16621

- 添加依赖及注解`@EnableTsfRateLimit`(服务提供方)
- 建立限流规则
- 启动限流即可

#### 服务熔断

- 添加依赖及注解`@EnableTsfCircuitBreaker`(服务调用方)
- TSF平台配置熔断规则

  也可以自己写配置文件，但是会被线上配置的规则覆盖，测试时可用

TSF摒弃了已经不再继续维护的Hystrix断路器，采用官方推荐的**Resilience4J**作为底层实现

#### 服务容错

https://cloud.tencent.com/document/product/649/40582

- 添加依赖及注解`@EnableTsfFaultTolerance`(服务调用方)
- 如果需要使用feign的降级功能(如下)，则需要关闭Hystrix开关(若当前没有Hystrix的依赖可忽略)

```Java
@FeignClient(name="circuit-breaker-mock-service",fallbackFactory=HystrixClientFallbackFactory.class)
@FeignClient(name="circuit-breaker-mock-service",fallback=FeignClientFallback.class)
```
关闭Hystrix开关(默认是关闭的，如果之前使用了该功能，可以删除该配置或者关闭)

```yaml
feign:
  hystrix:
    enabled: false
```

打开TSF熔断开关

```yaml
feign:
  tsf:
    enabled: true
```

- 代码中要添加注解`@TsfFaultTolerance`

```Java
// 下面省略了无关的代码
@TsfFaultTolerance(strategy = TsfFaultToleranceStragety.FAIL_OVER, parallelism = 2, fallbackMethod = "doWorkFallback")
public void doWork() throws InterruptedException {
    String response = providerDemoService.echo("1234");
    LOG.info("consumer-demo auto test, response: [" + response + "]");
}
public void doWorkFallback() {
    System.out.println("fallback");
}
// fallbackMethod可以加也可以不加，用户可以自行选择
@TsfFaultTolerance(strategy = TsfFaultToleranceStragety.FAIL_FAST)
public void doWork2() throws InterruptedException {
    String response = providerDemoService.echo2("1234");
    LOG.info("consumer-demo auto test, response: [" + response + "]");
}
```

## TSF云原生应用

https://cloud.tencent.com/document/product/649/54147

> 原生应用的服务治理功能是基于TSF Mesh来实现的
>
> 服务调用会经过TSF Mesh的sidecar从而实现服务注册发现，服务调用，负载均衡，路由等功能
>
> 网络代理分为三个组件: pilot-agent Mesh-DNS Envoy
>
> https://cloud.tencent.com/document/product/649/33884

#### 服务注册

云原生应用无需任何改造，目前只支持Consul Eureka和Nacos

#### ~~<b style="color:red">配置</b>~~

**配置类型**

应用配置：生效在单个应用上面，发布的范围是部署组维度，属于 TSF 平台上的配置

全局配置：生效在整个集群或者命名空间，发布的范围是命名空间维度，属于 TSF 平台上的配置

本地配置：是应用程序在代码工程中创建的配置（如 application.yml 和 bootstrap.yml ）

文件配置：支持用户通过控制台将配置下发到服务器的指定目录。应用程序通过读取该目录下的配置文件实现特殊的业务逻辑

> 原生应用与Mesh应用不支持应用配置和全局配置，文件配置都支持，若想支持应用配置和全局配置需要使用TSF**框架SDK**集成

#### 监控

监控指标: https://cloud.tencent.com/document/product/649/72802

#### 调用链

https://cloud.tencent.com/document/product/649/54151

目前支持Zipkin的[B3 Propagation](https://github.com/openzipkin/b3-propagation)，所以只要是和Zipkin B3兼容的SDK均可，例如Spring Cloud Sleuth

由于是通过TSF Mesh流量劫持实现，因此不支持方法级别的调用

> ⚠️ 针对其他语言类型的Mesh应用，sidecar可通过'tsf-mesh-tag'header自定义标签获取应用相关的业务信息实现调用链

#### 服务鉴权

https://cloud.tencent.com/document/product/649/19049

基于自定义标签`headers={'custom-key':'custom-value'}`

#### 服务路由

https://cloud.tencent.com/document/product/649/54147

与Mesh配合添加自定义标签，在请求头里加入指定的key

```Java
// 以拦截FeignClient请求为例
@Configuration
public class FeignConfig {
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                template.header("custom-key", "custom-value");
                // 省略其他header信息
            }
        };
    }
}
```

> ⚠️ 猜测直接在TSF平台配置路由规则，也可以实现路由，尚未验证

#### 服务限流

https://cloud.tencent.com/document/product/649/54152

原生Srping cloud可以借助TSF平台实现服务限流，有两种方式:

- 自定义标签，需在HTTP请求头添加`tsf-mesh-tag: KEY=VALUE`
- 同上服务路由方式，配合Service Mesh，加入`template.header("custom-key", "custom-value")`

业务应用若需使用TSF的限流功能需要禁用自身组件的限流

业务服务相关的限流组件(Resilience和Sentinel)不支持通过property配置，需自行修改代码来关闭

#### 服务熔断

https://cloud.tencent.com/document/product/649/54152

支持Hystrix，如果要使用TSF实现的熔断，需要关闭服务自身的熔断依赖

**Hystrix/Spring Cloud Hystrix**

> 在springcloud 2020中已经移除了Hystrix改为**Spring Cloud Circuit Breaker - Resilience**

```yaml
# 如果用了Feign，此方式也可以关闭其中的熔断功能
hystrix:
 command:
   default:
     circuitBreaker:
       enabled: false
```

> ⚠️ 云原生Mesh应用集成TSF熔断需要关闭自身熔断是否可用如下配置

```yaml
feign:
  hystrix:
    enabled: false
```

**Resilience**

不支持配置，只能通过`transitionToDisabledState()`或自行修改代码来关闭。`transitionToDisabledState()`示例如下:

```Java
public class ProviderServiceResilience {
    private final static String cbName = "default";

    private final CircuitBreakerRegistry cbRegistry;

    public ProviderServiceResilience() {
        cbRegistry = CircuitBreakerRegistry.ofDefaults();
    }

    public String run() {
        try {
            cbRegistry.circuitBreaker(cbName).executeCallable(...);
        } catch (Exception e) {
        }
        return "resilience fallback\n";
    }

    public void disable() {
        cbRegistry.circuitBreaker(cbName).transitionToDisabledState();
    }
}
```

**Spring Cloud Circuit Breaker - Resilience**

修改`application.yml`

```yaml
spring:
  cloud:
    circuitbreaker:
      resilience4j:
        enabled: false
```

**Sentinel**

修改`application.yml`

```yaml
spring:
  cloud:
    circuitbreaker:
      sentinel:
        enabled: false
```

#### ~~<b style="color:red">服务容错</b>~~

暂未找到相关文档

> ⚠️ 支持通过TSF框架SDK集成方式，容错规则同样需要服务自己实现

## 术语

https://cloud.tencent.com/document/product/649/13007

#### 单元化

https://help.aliyun.com/document_detail/159741.html

单元化应用可以集中式部署，解决了网络延迟以及异地容灾

是指一个能完成所有业务操作的自包含集合，在这个集合中包含了所有业务所需的所有服务，以及分配给这个单元的数据，是一个五脏俱全的缩小版整站

单元化需要进行数据分区，即不同单元处理不同分区的数据，分区数据的拆分规则和维度必须保证全业务一致，即分区数据不重叠

**网关单元化**: 将部分请求通过全局参数方式确保且请求在整个链路中都是同一个单元化应用(全业务应用)处理

> ⚠️ 在多AZ部署下DNS智能解析会将请求转发到就近的单元化网关
>
> https://help.aliyun.com/zh/dns/intelligent-dns-resolution
>
> 单元化应用部署存在的一些问题
>
> https://cloud.tencent.com/developer/article/2483729
>
> 单AZ: 故障涉及数据复制
>
> 多AZ: 故障存在灰度故障，且存在跨区域的通信延迟

#### 泳道

服务调用配置，可基于此实现灰度环境

https://cloud.tencent.com/document/product/649/43464

## 遗留问题

* [ ] TSF Mesh如何代理云原生应用的注册中心(支持Consul Eureka Nacos)

  -- 监听注册中心(Consul Eureka Nacos)端口拦截代理部分请求到TSF内置的Consul注册中心
  
  如: 注册请求，获取服务列表请求，获取实例列表请求
  
  https://cloud.tencent.com/developer/article/1809735
  
* [ ] 云原生Mesh应用和Mesh应用不支持远程配置(TSF应用配置和全局配置)

  -- 未拦截/代理配置相关请求

  详见配置管理: https://main.qcloudimg.com/raw/document/debug/product/pdf/649_36499_cn.pdf

* [ ] 自定义标签中普通标签(headers={KV})和mesh标签(headers={'tsf-mesh-tag': KV})的区别

  -- mesh标签更具有平台特性，与普通的http header区分开
