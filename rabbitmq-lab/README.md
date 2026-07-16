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
| RMQ006 | Dead Letter Queue | `demo06_dlq` | Direct | Handle failed/rejected messages |
| RMQ007 | Delayed Messages | `demo07_delay` | Custom | TTL + DLX pattern for delayed processing |

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
