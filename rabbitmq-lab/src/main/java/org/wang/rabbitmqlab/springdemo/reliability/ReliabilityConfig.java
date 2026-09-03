package org.wang.rabbitmqlab.springdemo.reliability;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * reliability 演示的拓扑 + 三个 Bean 的装配。【标准实现，可直接运行】
 *
 * 跑它：--spring.profiles.active=reliability
 * 练习用的空骨架在同级目录的 reliability_skeleton/ 包（profile = reliability-skeleton）。
 *
 * @Profile("reliability")：
 *   只有启动参数里带 reliability 才加载本类。
 *   不加的话跑 hello-world 时也会把这套队列/交换机建出来，而且两个消费者会一起启动，观察结果全乱。
 */
@Profile("reliability")
@Configuration
public class ReliabilityConfig {

    // ========== 常量：生产端和消费端共用 ==========
    // 为什么要提常量：routing key 是"全等匹配"，生产消费两边各手打一遍，
    // 打错一个字符 = 消息静默丢弃，而且 confirm 还是 ack=true，极难排查。
    public static final String EXCHANGE = "reliability.exchange";
    public static final String QUEUE = "reliability.queue";
    public static final String ROUTING_KEY = "reliability.routingkey";

    // 死信三件套（名字必须和 queue() 里写的完全一致）
    public static final String DLX_EXCHANGE = "reliability.dlx.exchange";
    public static final String DLX_QUEUE = "reliability.dlx.queue";
    public static final String DLX_ROUTING_KEY = "reliability.dlx.routingkey";

    // ========== 三个业务 Bean ==========
    // 说明：RabbitTemplate 由 Spring Boot 自动配置，通过方法参数注入给 Producer，
    // 让它自己去注册 Confirm / Return 回调（那是生产者侧的事，不该放在配置类里）。

    @Bean
    public ReliabilityProducer reliabilityProducer(RabbitTemplate rabbitTemplate) {
        return new ReliabilityProducer(rabbitTemplate);
    }

    @Bean
    public ReliabilityConsumer reliabilityConsumer() {
        return new ReliabilityConsumer();
    }

    @Bean
    public DlxConsumer dlxConsumer() {
        return new DlxConsumer();
    }

    // ========== 主拓扑 ==========

    /** 业务交换机：direct + 持久化 */
    @Bean
    public DirectExchange directExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    /**
     * 业务队列：持久化 + 指定死信去处。
     * 这里只写了"死信往哪发"（DLX 名 + DLX 的 routing key），
     * DLX 交换机和死信队列本身在下面三个 @Bean 里声明。
     */
    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    /**
     * binding key 写在这里（队列侧的订阅规则）。
     * 本 demo 用的是 direct exchange，所以这里是**全等匹配**：
     * 生产者发的 routing key 必须和这个字符串一字不差。
     */
    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(directExchange()).with(ROUTING_KEY);
    }

    // ========== 死信拓扑 ==========
    // 坑：RabbitMQ 只在真正投递死信的那一刻才检查 DLX 是否存在。
    // 如果 reliability.dlx.exchange 没被声明，nack(requeue=false) 的消息会**直接消失**，
    // 既不在原队列、也不在任何死信队列里，日志上什么都不显示。

    /** 死信交换机：普通 direct 交换机，没有任何特殊之处 */
    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
    }

    /**
     * 死信队列：就是一个普通队列。
     * 注意不要再给它配 deadLetterExchange —— 否则死信还能再死信一次，形成循环。
     */
    @Bean
    public Queue dlq() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    /** 死信队列也要绑到死信交换机上，否则消息同样进不去 */
    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlq()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }
}
