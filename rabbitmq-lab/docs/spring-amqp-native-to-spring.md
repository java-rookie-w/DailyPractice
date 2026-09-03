# Spring AMQP：原生 API → Spring API 对照

> 写给"原生 14 个 demo 都写过、但不知道同一件事在 Spring 里怎么表达"的阶段。
> 目标：看完能自己写出 `reliability` / `idempotency` / `delay` 三个 Spring demo。

---

## 0. 四个思维转变（先建立这个，再看 API）

| | 原生 `amqp-client` | Spring AMQP |
|---|---|---|
| 谁持有 Channel | 你自己 `ConnectionUtil` 创建、自己关 | Spring 管连接池，**把 Channel 注入给你** |
| 怎么表达"我要做什么" | 调命令式 API（`basicPublish` / `basicConsume`） | **声明式**：`@Bean` 声明资源，`@RabbitListener` 声明监听 |
| 方法参数从哪来 | `deliverCallback` 里拿 envelope/props/body | **想要什么声明什么**：`Message` / `Channel` / `@Header` / POJO 都能当方法参数 |
| 配置放哪 | 硬编码在 Java 里 | `application.yml` + 少量 `@Bean` |

**最重要的一条**：`ack` / `nack` / `reject` 的代码**和原生一模一样**，因为拿到的是同一个 `com.rabbitmq.client.Channel` 对象。
变的只是"Channel 从哪来"（自己 new → Spring 注入方法参数）和"ACK 模式怎么开"（代码里传 `autoAck=false` → yml 配 `acknowledge-mode: manual`）。

---

## 1. 生产端：消息确认（Confirm）与退回（Return）

### 1.1 开启确认模式

| 原生 | Spring |
|---|---|
| `channel.confirmSelect()` | yml：`spring.rabbitmq.publisher-confirm-type: correlated` |

三个取值：
- `none`：关闭（默认）
- `correlated`：**异步回调** `ConfirmCallback` —— 推荐，对应原生 `addConfirmListener`
- `simple`：同步阻塞等待 —— 对应原生 `waitForConfirmsOrDie`，**吞吐极低，生产别用**

> ⚠️ Spring 把"同步等待"这条路基本废掉了，只留异步回调。
> 确实需要等结果时用 `CorrelationData.getFuture().get()`（见 1.4）。

### 1.2 确认回调

```java
// 原生
channel.addConfirmListener(
    (seq, multiple) -> { /* ack  */ },
    (seq, multiple) -> { /* nack */ });

// Spring：挂在 RabbitTemplate 上
rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
    String id = correlationData != null ? correlationData.getId() : null;
    if (ack) {
        log.info("投递成功 id={}", id);
    } else {
        log.error("投递失败 id={} cause={}", id, cause);  // 这里要：重发 / 落库 / 告警
    }
});
```

### 1.3 不可达退回（对应原生 `addReturnListener` + `mandatory`）

```java
rabbitTemplate.setReturnsCallback(returned -> {
    log.error("消息不可达：exchange={} routingKey={} code={} text={}",
        returned.getExchange(), returned.getRoutingKey(),
        returned.getReplyCode(), returned.getReplyText());
});
```

**必须同时开 mandatory**，否则不可达消息被静默丢弃：

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    template:
      mandatory: true     # 关键！不开这条 ReturnsCallback 永远不触发
```

> 🔥 **最经典的坑**：只配了 `publisher-confirm-type`、没配 `mandatory` →
> 消息路由不到任何队列时被**静默丢弃**，而 confirm 回调照样返回 `ack=true`。
> 于是你以为"消息都成功了"，实际全丢了。**"confirm 全绿但消息丢失" 90% 是这个原因。**

### 1.4 发送时带上业务 ID（可靠性的前提）

```java
// 方式一：CorrelationData，confirm 回调里能原样拿回来
CorrelationData cd = new CorrelationData(orderId);
rabbitTemplate.convertAndSend(exchange, routingKey, payload, cd);

// 方式二：塞进 MessageProperties
rabbitTemplate.convertAndSend(exchange, routingKey, payload, msg -> {
    msg.getMessageProperties().setMessageId(orderId);
    return msg;
}, cd);

// 需要同步等结果时（少量关键消息才这么用）
CorrelationData.Confirm confirm = cd.getFuture().get(5, TimeUnit.SECONDS);
if (confirm != null && !confirm.isAck()) { /* 补偿 */ }
```

> ⚠️ **`CorrelationData` / `messageId` 是"发送批次"的标识，不是业务幂等键。**
> 幂等去重要用消息体里的**业务 ID**（订单号等），因为消息重投时 MQ 会生成新的 messageId。
> 详见幂等 demo。

---

## 2. 消费端：手动 ACK / nack / 死信

### 2.1 开启手动 ACK

| 原生 | Spring |
|---|---|
| `channel.basicConsume(queue, false, callback, cancel)` | yml `spring.rabbitmq.listener.simple.acknowledge-mode: manual`<br>或注解 `@RabbitListener(queues="q", ackMode="MANUAL")` |

### 2.2 方法签名：想要什么声明什么

```java
@RabbitListener(queues = "reliability.queue", ackMode = "MANUAL")
public void onMessage(Message message, Channel channel) throws IOException {
    long tag = message.getMessageProperties().getDeliveryTag();
    // 或者直接用 @Header 注入：
    // public void onMessage(@Payload String body, Channel channel,
    //                       @Header(AmqpHeaders.DELIVERY_TAG) long tag)
}
```

`AmqpHeaders` 在 `org.springframework.amqp.support.AmqpHeaders`。

### 2.3 ack / nack 代码（与原生完全一致）

```java
channel.basicAck(tag, false);            // 确认；false = 不批量
channel.basicNack(tag, false, true);     // requeue=true  -> 重新入队，可再次消费
channel.basicNack(tag, false, false);    // requeue=false -> 丢弃；配了 DLX 就进死信
channel.basicReject(tag, false);         // 单条拒绝，等价 nack(multiple=false)
```

### 2.4 prefetch 与并发

| 原生 | Spring |
|---|---|
| `channel.basicQos(1)` | yml `spring.rabbitmq.listener.simple.prefetch: 1` |
| 自己起多个线程 / 多个 consumer | yml `concurrency: 3` + `max-concurrency: 10` |

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 1
        concurrency: 3
        max-concurrency: 10
        default-requeue-rejected: false   # 拒绝后不自动重投（配合 DLX，避免无限循环）
```

> `default-requeue-rejected` 默认是 `true`：消费异常会把消息塞回队头 →
> 处理失败就无限循环。**配了 DLX 的话应该设 false**，让失败消息进死信。

### 2.5 Spring 特有的重试（原生没有）

```yaml
spring.rabbitmq.listener.simple.retry:
  enabled: true
  max-attempts: 3
  initial-interval: 1000ms
```

- 这是**消费端本地重试**（在同一条消息上重跑 listener），不是重新入队。
- 重试耗尽后交给 `MessageRecoverer`，默认是 `RejectAndDontRequeueRecoverer`（拒绝 + 不重投 → 进死信）。

> ⚠️ **练习时二选一，别同时开**：
> 要么「手动 ACK + 自己 `basicNack(requeue)` 控制重投」，
> 要么「自动 ACK + Spring retry」。
> 两者叠加时"谁负责重试、谁负责重投"的责任边界会糊，先各写一遍分开理解。

---

## 3. 声明式装配：Queue / Exchange / Binding

原生用 `channel.queueDeclare(...)` / `exchangeDeclare` / `queueBind`；
Spring 里把资源声明成 `@Bean`，**应用启动时自动同步到 broker**（不存在就创建）。

```java
@Configuration
public class ReliabilityTopology {

    @Bean
    public DirectExchange bizExchange() {
        return ExchangeBuilder.directExchange("reliability.exchange").durable(true).build();
        // 或 new DirectExchange("reliability.exchange", true, false)
    }

    @Bean
    public Queue bizQueue() {
        return QueueBuilder.durable("reliability.queue")
                .deadLetterExchange("dlx.exchange")       // x-dead-letter-exchange
                .deadLetterRoutingKey("dlx.key")          // x-dead-letter-routing-key
                // .ttl(10000)                            // x-message-ttl，队列级
                // .quorum()                              // 仲裁队列
                .build();
    }

    @Bean
    public Binding bizBinding(Queue bizQueue, DirectExchange bizExchange) {
        return BindingBuilder.bind(bizQueue).to(bizExchange).with("reliability.key");
    }
}
```

对应原生参数：`x-dead-letter-exchange` / `x-dead-letter-routing-key` / `x-message-ttl`，
只是从 `Map<String,Object> args` 换成了 Builder 链式调用。

**消息级 TTL**（每条消息单独设过期时间）：

```java
rabbitTemplate.convertAndSend(exchange, routingKey, payload, msg -> {
    msg.getMessageProperties().setExpiration("10000");   // 字符串，单位毫秒
    return msg;
});
```

---

## 4. 消息转换器（可选，一个 Bean 的事）

原生里你手动 `ObjectMapper` 转 `byte[]`；Spring 里换掉转换器就能直接收发 POJO：

```java
@Bean
public MessageConverter jacksonConverter() {
    return new Jackson2JsonMessageConverter();
}
```

之后 listener 可以直接写：

```java
@RabbitListener(queues = "q")
public void onMessage(OrderMessage order, @Header(AmqpHeaders.DELIVERY_TAG) long tag) { ... }
```

---

## 5. `reliability` demo 搭建清单（照着写）

按这个顺序，每个文件该用什么 API 都标好了：

| 步骤 | 文件 | 要做的 | 用到的 API |
|---|---|---|---|
| 1 | `ReliabilityConfig` | 声明业务交换机/队列/绑定 + 死信交换机/队列/绑定 | `@Bean` + `QueueBuilder` / `ExchangeBuilder` / `BindingBuilder` |
| 2 | `ReliabilityConfig` | 把 `ConfirmCallback` / `ReturnsCallback` 挂到 `RabbitTemplate` 上 | `rabbitTemplate.setConfirmCallback` / `setReturnsCallback` |
| 3 | yml | 开 confirm + mandatory | `publisher-confirm-type: correlated`、`template.mandatory: true` |
| 4 | `ReliabilityProducer` | 带 `CorrelationData` 发消息；**故意发一条 routingKey 不存在的**验证 Return | `convertAndSend(ex, rk, payload, cd)` |
| 5 | `ReliabilityConsumer` | 手动 ACK；业务失败 `basicNack(requeue=false)` 让它进死信 | `@RabbitListener(ackMode="MANUAL")` + `channel.basicAck/basicNack` |
| 6 | `DlxConsumer` | 监听死信队列，打印 `x-death` 头看被投递的原因 | `@Header("x-death")` 或 `message.getMessageProperties().getHeader("x-death")` |

**验收标准**（写完后自己跑一遍确认真的懂了）：
1. 正常消息 → 控制台出现 `投递成功 id=xxx` + 消费成功；
2. routingKey 不存在 → 触发 `ReturnsCallback`，打印"不可达"；
3. 消费端抛异常 → 消息进死信队列，`x-death` 里能看到 `reason: rejected`；
4. 把 `template.mandatory` 改成 false 再跑一次第 2 条 → **Return 不触发、confirm 照样 ack=true**（亲手踩这个坑印象最深）。
