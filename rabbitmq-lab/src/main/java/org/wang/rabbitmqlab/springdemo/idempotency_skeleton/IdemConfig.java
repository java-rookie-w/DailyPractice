package org.wang.rabbitmqlab.springdemo.idempotency_skeleton;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 【练习版】幂等 demo 的拓扑与 Bean 装配。
 *
 * 跑它：--spring.profiles.active=idempotency-skeleton
 * 标准答案在 idempotency 包。两边队列名带 .sk 后缀，互不干扰。
 */
@Profile("idempotency-skeleton")
@Configuration
public class IdemConfig {

    // 队列名与标准版错开（.sk 后缀），避免两边先后运行时撞"同名但参数不同"的坑
    public static final String EXCHANGE = "idem.sk.exchange";
    public static final String QUEUE = "idem.sk.queue";
    public static final String ROUTING_KEY = "idem.sk.routingkey";

    @Bean
    public IdemProducer idemProducer(RabbitTemplate rabbitTemplate) {
        return new IdemProducer(rabbitTemplate);
    }

    @Bean
    public IdemConsumer idemConsumer(DedupStore dedupStore) {
        return new IdemConsumer(dedupStore);
    }

    /**
     * TODO 1：声明 DedupStore Bean
     * 写法：
     // @Bean
     // public DedupStore dedupStore(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
     //     return new JdbcDedupStore(jdbcTemplate);
     // }
     * JdbcTemplate 不用自己建：classpath 上有 starter-jdbc + H2，Boot 自动配置就给了一个。
     */
    // @Bean
    // public DedupStore dedupStore(...) { ... }

    /** TODO 2：声明交换机（direct + 持久化，名字用常量 EXCHANGE） */

    /** TODO 3：声明队列（持久化即可，本 demo 不配死信） */

    /** TODO 4：把队列绑到交换机上（routing key 用常量 ROUTING_KEY） */
}
