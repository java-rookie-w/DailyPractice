package org.wang.rabbitmqlab.springdemo.delay_skeleton;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 【练习版】延迟队列拓扑。队列名带 .sk 后缀与标准版错开。
 *
 * 跑它：--spring.profiles.active=delay-skeleton
 * 要建的东西（5 个 Bean）：两条等待队列 + DLX + 就绪队列 + 一条绑定。
 */
@Profile("delay-skeleton")
@Configuration
public class DelayConfig {

    public static final String QUEUE_LEVEL_QUEUE = "delay.sk.q10";
    public static final String MESSAGE_LEVEL_QUEUE = "delay.sk.mlevel";
    public static final String DLX_EXCHANGE = "delay.sk.dlx.exchange";
    public static final String READY_QUEUE = "delay.sk.ready.queue";
    public static final String READY_ROUTING_KEY = "delay.sk.ready";

    @Bean
    public DelayProducer delayProducer(org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate) {
        return new DelayProducer(rabbitTemplate);
    }

    @Bean
    public DelayConsumer delayConsumer() {
        return new DelayConsumer();
    }

    /**
     * TODO 1：队列级 TTL 的等待队列
     * 要求：durable + ttl(10000) + 死信到 DLX_EXCHANGE（rk 用 READY_ROUTING_KEY）
     * 提示：QueueBuilder.durable(QUEUE_LEVEL_QUEUE).ttl(...).deadLetterExchange(...).deadLetterRoutingKey(...).build();
     */
     @Bean
    public Queue queueLevelQueue() {
        return QueueBuilder.durable(QUEUE_LEVEL_QUEUE)
                .ttl(10000)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(READY_ROUTING_KEY)
                .build();
    }

    /**
     * TODO 2：消息级 TTL 的等待队列
     * 要求：durable + **不设 ttl**（消息自己带 expiration）+ 死信路由同上
     */
     @Bean
    public Queue messageLevelQueue() {
         return QueueBuilder.durable(MESSAGE_LEVEL_QUEUE)
                 .deadLetterExchange(DLX_EXCHANGE)
                 .deadLetterRoutingKey(READY_ROUTING_KEY)
                 .build();
     }
    /** TODO 3：死信交换机（direct + 持久化，名字用 DLX_EXCHANGE） */
    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
    }
    /** TODO 4：就绪队列（持久化即可） */
    @Bean
    public Queue readyQueue() {
        return QueueBuilder.durable(READY_QUEUE).build();
    }
    /** TODO 5：把就绪队列绑到死信交换机（routing key 用 READY_ROUTING_KEY） */
    @Bean
    public Binding readyBinding() {
        return BindingBuilder.bind(readyQueue()).to(dlxExchange()).with(READY_ROUTING_KEY);
    }
}
