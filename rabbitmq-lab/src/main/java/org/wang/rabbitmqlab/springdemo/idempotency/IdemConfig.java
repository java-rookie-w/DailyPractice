package org.wang.rabbitmqlab.springdemo.idempotency;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * idempotency 演示：消费幂等 —— 业务键 + 唯一索引 + **同库同事务**。【标准实现】
 *
 * 跑它：--spring.profiles.active=idempotency
 * 练习骨架在 idempotency_skeleton 包（profile = idempotency-skeleton）。
 *
 * 演示重点（对照日志看）：
 *   1. 同一笔订单连发 3 次，messageId 各不相同 → 业务只执行 1 次（幂等键是业务键，不是 messageId）
 *   2. 业务失败 → 事务回滚，占位自动消失 → 不需要 release（对比 Redis/跨库方案必须 release）
 *   3. 失败的订单补偿重投 → 能重新进来重试（占位已回滚，否则会被误杀）
 *   4. 幂等两层：去重表拦重复投递 + 状态机条件更新（WHERE status='UNPAID'）兜业务正确性
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

    /**
     * 消费者只依赖业务 Service —— 事务边界在 Service 上，ack 在它提交之后。
     * 别把 @Transactional 加到监听方法上，原因见 IdemOrderService 类注释。
     */
    @Bean
    public IdemConsumer idemConsumer(IdemOrderService orderService) {
        return new IdemConsumer(orderService);
    }

    /** JdbcTemplate 由 Boot 自动配置（classpath 上有 starter-jdbc + H2 就有） */
    @Bean
    public DedupStore dedupStore(JdbcTemplate jdbcTemplate) {
        return new JdbcDedupStore(jdbcTemplate);
    }

    /**
     * 业务 Service：占位 + 业务更新在一个事务里。
     * 事务管理器（DataSourceTransactionManager）也是 Boot 自动配置的。
     */
    @Bean
    public IdemOrderService idemOrderService(JdbcTemplate jdbcTemplate, DedupStore dedupStore) {
        return new IdemOrderService(jdbcTemplate, dedupStore);
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
