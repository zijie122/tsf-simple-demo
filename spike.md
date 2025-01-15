## 术语

https://cloud.tencent.com/document/product/649/13007

#### 单元化

https://help.aliyun.com/document_detail/159741.html

单元化应用可以集中式部署，解决了网络延迟以及异地容灾

是指一个能完成所有业务操作的自包含集合，在这个集合中包含了所有业务所需的所有服务，以及分配给这个单元的数据，是一个五脏俱全的缩小版整站

单元化需要进行数据分区，即不同单元处理不同分区的数据，分区数据的拆分规则和维度必须保证全业务一致，即分区数据不重叠

**网关单元化**: 将部分请求通过全局参数方式确保且请求在整个链路中都是同一个单元化应用(全业务应用)处理

> DNS智能解析会将请求转发到就近的单元化网关 https://help.aliyun.com/zh/dns/intelligent-dns-resolution
>
> 单元化应用部署存在的一些问题
>
> https://cloud.tencent.com/developer/article/2483729
>
> 单AZ: 故障涉及数据复制
>
> 多AZ: 故障存在灰度故障，且存在跨区域的通信延迟

#### 泳道

相当于一个灰度环境

https://cloud.tencent.com/document/product/649/43464

## 版本配套关系

https://cloud.tencent.com/document/product/649/36285

springcloud version: https://spring.io/projects/spring-cloud

```
jdk8
springboot 2.7
springcloud 2021
TSF SDK 1.46.x
```

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

## TSF框架SDK

https://cloud.tencent.com/document/product/649

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

> 该功能为tsf内置功能，原生consul框架也有相同的逻辑获取配置变更，可见consul-demo

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

> 必须部署到TSF中才会生成traceid和spanid到MDC

#### 服务鉴权

https://cloud.tencent.com/document/product/649/16621#.E6.9C.8D.E5.8A.A1.E9.89.B4.E6.9D.83

- 请求双方都需要开启注解
- 调用方(consumer)和被调方(provider)想使用基本tag的鉴权规则:
  - consumer需要通过`TsfContext`设置tag， provider需要在控制台上设置tag鉴权规则

#### 服务路由

#### 服务限流

#### 服务熔断

#### 服务容错

## TSF云原生应用

https://cloud.tencent.com/document/product/649/19049

> 原生应用的服务治理功能是基于TSF Mesh来实现的
>
> 服务调用会经过TSF Mesh的sidecar从而实现服务注册发现，服务调用，负载均衡，路由等功能
>
> 网络代理分为三个组件: pilot-agent Mesh-DNS Envoy
>
> https://cloud.tencent.com/document/product/649/33884

#### 服务注册

需要配置`spec.yaml`文件文件用于描述服务信息，sidecar会通过服务描述文件将服务注册到服务注册中心

`spec.yaml`支持本地配置和**控制台配置**

#### ~~<b style="color:red">配置</b>~~

#### 监控

#### 调用链

https://cloud.tencent.com/document/product/649/54151

目前支持Zipkin的[B3 Propagation](https://github.com/openzipkin/b3-propagation)，所以只要是和Zipkin B3兼容的SDK均可，例如Spring Cloud Sleuth

> 针对其他语言类型的Mesh应用，sidecar可通过'tsf-mesh-tag'header获取应用相关的业务信息

#### 服务鉴权

https://cloud.tencent.com/document/product/649/19049

基于自定义标签`headers={'custom-key':'custom-value'}`

>目前Mesh集成的自定义标签有两种: 1.普通标签 2:mesh标签(感觉这个更具有业务含义，指定被mesh采集使用)

#### 服务路由

#### 服务限流

#### 服务熔断

#### 服务容错