package org.wang.rabbitmqlab.springdemo.idempotency;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * idempotency 演示：消费幂等，存储用 H2 内存库（MySQL 唯一索引的替身）。【标准实现】
 *
 * 跑它：--spring.profiles.active=idempotency
 * 练习骨架在 idempotency_skeleton 包（profile = idempotency-skeleton）。
 *
 * 演示重点：
 *   1. 同一 messageId 连发 3 次，业务只执行 1 次（另 2 次被去重表拦下）
 *   2. 业务失败会 release 归还 id —— 同 id 重发后能正常处理
 *   3. 为什么必须幂等：broker 只保证 at-least-once，重复投递是常态
 */
@Profile("idempotency")
@Configuration
public class IdemConfig {

    public static final String EXCHANGE = "idem.exchange";
    public static final String QUEUE = "idem.queue";
    public static final String ROUTING_KEY = "idem.routingkey";

    @Bean
    public IdemProducer idemProducer(RabbitTemplate rabbitTemplate) {
        return new IdemProducer(rabbitTemplate);
    }

    @Bean
    public IdemConsumer idemConsumer(DedupStore dedupStore) {
        return new IdemConsumer(dedupStore);
    }

    /** JdbcTemplate 由 Boot 自动配置（classpath 上有 starter-jdbc + H2 就有） */
    @Bean
    public DedupStore dedupStore(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new JdbcDedupStore(jdbcTemplate);
    }

    @Bean
    public DirectExchange idemExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue idemQueue() {
        // 这个 demo 不配死信（死信链路在 reliability 里已经看过了），聚焦幂等本身
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding idemBinding() {
        return BindingBuilder.bind(idemQueue()).to(idemExchange()).with(ROUTING_KEY);
    }
}
