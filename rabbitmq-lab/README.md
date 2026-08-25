# RabbitMQ Lab

> Message queue experiments with RabbitMQ — from simple queues to complex routing topologies.

---

## Module Goals

- Understand AMQP protocol concepts: exchanges, queues, bindings, routing keys
- Implement all 5 standard exchange patterns
- Explore message reliability: acknowledgments, persistence, dead-letter queues
- Practice common patterns: RPC, pub/sub, competing consumers, delayed messages

---

## Learning Path

| # | Experiment | Package | Exchange Type | Goal |
|---|-----------|---------|---------------|------|
| RMQ001 | Hello World | `demo01_simple` | *(default)* | Send & receive a single message |
| RMQ002 | Work Queue | `demo02_workqueue` | *(default)* | Distribute tasks among workers, message acknowledgment |
| RMQ003 | Publish/Subscribe | `demo03_fanout` | Fanout | Broadcast messages to all bound queues |
| RMQ004 | Routing | `demo04_direct` | Direct | Route by exact routing key match |
| RMQ005 | Topics | `demo05_topic` | Topic | Route by wildcard pattern matching |
| RMQ006 | Publisher Confirms | `demo06_publisherConfirms` | *(default)* | Reliable publishing with async confirms |
| RMQ007 | Headers Exchange | `demo07_headers` | Headers | Route by header `all`/`any` matching |
| RMQ008 | Stream Queue | `demo08_stream` | Stream | High-throughput streaming queue with `first`/`next` offsets |
| RMQ009 | Dead Letter Queue | `demo09_deadletter` | Direct | DLX 三种触发条件：TTL 过期 / 队列超长 / 消费拒绝（x-death 头解析原因） |
| RMQ010 | Persistence | `demo10_persistence` | Topic | durable Exchange / durable Queue / persistent Message 三件套与重启存活矩阵 |
| RMQ011 | Reliability | `demo11_reliability` | Direct | 端到端不丢：Publisher Confirm + mandatory + 手动 ACK + 死信 + 幂等 |
| RMQ012 | TTL | `demo12_ttl` | Direct | 队列级 vs 消息级 TTL，复现消息级 TTL 队头阻塞坑 |
| RMQ013 | Delayed Order (Plugin) | `demo13_delay_plugin` | x-delayed-message | 延迟订单取消生产方案一：rabbitmq_delayed_message_exchange 插件，每条独立延迟无队头阻塞 |
| RMQ014 | Delayed Order (TTL+DLX) | `demo14_delay_ttl_dlx` | Direct | 延迟订单取消生产方案二：多级 TTL+DLX 队列串联，纯原生不依赖插件 |

---

## Experiment Standards

- Each demo starts from the simplest case and escalates
- All experiments runnable as Spring Boot applications
- Include configuration for both producer and consumer roles
- Document RabbitMQ management console steps for verification

---

## Resources

- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)
- [Spring AMQP Reference](https://docs.spring.io/spring-amqp/reference/)
