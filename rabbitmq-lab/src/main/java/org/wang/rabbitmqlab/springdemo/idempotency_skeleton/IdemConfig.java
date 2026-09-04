package org.wang.rabbitmqlab.springdemo.idempotency_skeleton;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

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

    /** TODO 1：消费者改为依赖 IdemOrderService（事务边界在 Service 上，不在监听方法上） */
    @Bean
    public IdemConsumer idemConsumer(IdemOrderService orderService) {
        return new IdemConsumer(orderService);
    }

    /**
     * TODO 2：声明 DedupStore Bean
     * 写法：
     // @Bean
     // public DedupStore dedupStore(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
     //     return new JdbcDedupStore(jdbcTemplate);
     // }
     * JdbcTemplate 不用自己建：classpath 上有 starter-jdbc + H2，Boot 自动配置就给了一个。
     */
     @Bean
     public DedupStore dedupStore(JdbcTemplate jdbcTemplate) {
         return new JdbcDedupStore(jdbcTemplate);
     }

    /**
     * TODO 3：声明业务 Service Bean（占位 + 业务更新同事务）
     * 事务管理器（DataSourceTransactionManager）同样是 Boot 自动配置的。
     */
    @Bean
    public IdemOrderService idemOrderService(JdbcTemplate jdbcTemplate, DedupStore dedupStore) {
        return new IdemOrderService(jdbcTemplate, dedupStore);
    }

    /** TODO 4：声明交换机（direct + 持久化，名字用常量 EXCHANGE） */
    @Bean
    public DirectExchange directExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    /** TODO 5：声明队列（持久化即可，本 demo 不配死信） */
    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    /** TODO 6：把队列绑到交换机上（routing key 用常量 ROUTING_KEY） */
    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(directExchange()).with(ROUTING_KEY);
    }
}
