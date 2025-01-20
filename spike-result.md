## TL;DR

基于TCS私有云平台集成TSF一站式微服务框架的方式有以下两种:

1. 云原生Mesh应用: 0代码侵入，部分微服务功能无法实现(如配置管理)
2. 框架SDK: 需要引入依赖和部分代码侵入，更全面的适配TSF，能正常使用全量功能

其他集成方式(如**javaagent**)暂不在本文章讨论范围

读者可以通过两种集成方式的对比基于当前应用场景选择合适的接入方式

具体Demo参考: https://github.com/zijie122/tsf-simple-demo

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

## TSF框架SDK

https://cloud.tencent.com/document/product/649

#### 前置配置

* 技术栈选型 https://cloud.tencent.com/document/product/649/73790
* Maven配置TSF私服地址
* 引入依赖
* **添加启动注解`@EnableTsf`**

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

如果是其他类型的注册中心(如[Eureka](https://cloud.tencent.com/document/product/649/16617#.E4.BB.8E-eureka-.E8.BF.81.E7.A7.BB.E5.BA.94.E7.94.A8))迁移到TSF需要进行依赖修改和注解更新以支持TSF的consul

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

该功能为TSF内置功能，原生consul框架也有相同的ConfigWatch逻辑获取配置变更，可见consul-demo

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

- 请求双方都需要添加依赖及注解
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

TSF新版本(2.0.0 Release)中，`@EnableTsf`及下属注解都被标记为废弃，目前尚未找到替代的注解

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

原生应用的服务治理功能是基于TSF Mesh来实现的

服务调用会经过TSF Mesh的sidecar从而实现服务注册发现，服务调用，负载均衡，路由等功能

网络代理分为三个组件: pilot-agent Mesh-DNS Envoy

https://cloud.tencent.com/document/product/649/33884

#### 服务注册

云原生应用无需任何改造，目前只支持Consul Eureka和Nacos

#### ~~<b style="color:red">配置</b>~~

**配置类型**

应用配置：生效在单个应用上面，发布的范围是部署组维度，属于 TSF 平台上的配置

全局配置：生效在整个集群或者命名空间，发布的范围是命名空间维度，属于 TSF 平台上的配置

本地配置：是应用程序在代码工程中创建的配置（如 application.yml 和 bootstrap.yml ）

文件配置：支持用户通过控制台将配置下发到服务器的指定目录。应用程序通过读取该目录下的配置文件实现特殊的业务逻辑

云原生应用与Mesh应用不支持应用配置和全局配置，文件配置都支持，若想支持应用配置和全局配置需要使用TSF**框架SDK**集成

#### 监控

监控指标: https://cloud.tencent.com/document/product/649/72802

#### 调用链

https://cloud.tencent.com/document/product/649/54151

目前支持Zipkin的[B3 Propagation](https://github.com/openzipkin/b3-propagation)，所以只要是和Zipkin B3兼容的SDK均可，例如Spring Cloud Sleuth

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

#### 服务限流

https://cloud.tencent.com/document/product/649/54152

原生Spring cloud可以借助TSF平台实现服务限流，有两种方式:

- 自定义标签，需在HTTP请求头添加`tsf-mesh-tag: KEY=VALUE`
- 同上服务路由方式，配合Service Mesh，加入`template.header("custom-key", "custom-value")`

业务应用若需使用TSF的限流功能需要禁用自身组件的限流

业务服务相关的限流组件(Resilience和Sentinel)不支持通过property配置，需自行修改代码来关闭

#### 服务熔断

https://cloud.tencent.com/document/product/649/54152

支持Hystrix，如果要使用TSF实现的熔断，需要关闭服务自身的熔断依赖

**Hystrix/Spring Cloud Hystrix**

在springcloud 2020中已经移除了Hystrix改为**Spring Cloud Circuit Breaker - Resilience4j**

```yaml
# 如果用了Feign，此方式也可以关闭其中的熔断功能
hystrix:
 command:
   default:
     circuitBreaker:
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

## 术语

https://cloud.tencent.com/document/product/649/13007