# Kafka Lab

> Distributed streaming platform experiments — producers, consumers, partitioning, and stream processing.

---

## Module Goals

- Understand Kafka architecture: brokers, topics, partitions, consumer groups
- Master producer tuning: acks, batching, compression, idempotency
- Master consumer tuning: offset management, rebalancing, delivery semantics
- Explore Kafka Streams and exactly-once semantics

---

## Learning Path

| # | Experiment | Goal |
|---|-----------|------|
| KAF001 | Basic Producer | Sync/async send, acks configuration |
| KAF002 | Basic Consumer | Consumer groups, offset commit strategies |
| KAF003 | Partitioning | Custom partitioner, key-based routing |
| KAF004 | Delivery Semantics | At-most-once, at-least-once, exactly-once |
| KAF005 | Kafka Streams | Stateless and stateful transformations |
| KAF006 | Error Handling | Retry, DLQ, idempotent consumers |

---

## Experiment Standards

- Each experiment is self-contained
- Use Spring Kafka abstractions
- Document topic creation and configuration
- Include consumer offset verification steps

---

## Resources

- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Kafka Reference](https://docs.spring.io/spring-kafka/reference/)
