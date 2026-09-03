package org.wang.rabbitmqlab.springdemo.reliability_skeleton;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 【练习版】reliability 的骨架，核心逻辑留空等你来填。
 *
 * 跑它：--spring.profiles.active=reliability-skeleton
 * 对照答案在同级目录的 reliability/ 包（profile = reliability），
 * 两边用不同的 profile 和不同的队列名（都带 .sk 后缀），互不干扰。
 *
 * ⚠️ 两个 profile 不要同时激活：两套 @Bean 方法名相同，会抛 ConflictingBeanDefinitionException。
 *
 * @Profile("reliability-skeleton")：
 *   只有启动参数里带 reliability-skeleton 才加载本类。
 *   不加的话跑 hello-world 时也会把这套队列/交换机建出来，而且两个消费者会一起启动，观察结果全乱。
 */
@Profile("reliability-skeleton")
@Configuration
public class ReliabilityConfig {

    // ========== 常量：生产端和消费端共用 ==========
    // 为什么要提常量：routing key 是"全等匹配"，生产消费两边各手打一遍，
    // 打错一个字符 = 消息静默丢弃，而且 confirm 还是 ack=true，极难排查。
    public static final String EXCHANGE = "reliability.sk.exchange";
    public static final String QUEUE = "reliability.sk.queue";
    public static final String ROUTING_KEY = "reliability.sk.routingkey";

    // 死信三件套（名字必须和 queue() 里写的完全一致）
    public static final String DLX_EXCHANGE = "reliability.sk.dlx.exchange";
    public static final String DLX_QUEUE = "reliability.sk.dlx.queue";
    public static final String DLX_ROUTING_KEY = "reliability.sk.dlx.routingkey";

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

    // ========== 主拓扑（已完成） ==========

    /** 业务交换机：direct + 持久化 */
    @Bean
    public DirectExchange directExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    /**
     * 业务队列：持久化 + 指定死信去处。
     * 注意这里只写了"死信往哪发"（DLX 名 + DLX 的 routing key），
     * 但 DLX 交换机和死信队列本身还不存在 —— 见下面三个 TODO。
     */
    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    /** binding key 写在这里（队列侧的订阅规则） */
    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(directExchange()).with(ROUTING_KEY);
    }

    // ========== 死信拓扑（TODO：必须补，否则死信会被静默丢弃） ==========
    //
    // 坑：RabbitMQ 只在真正投递死信的那一刻才检查 DLX 是否存在。
    // 如果 reliability.dlx.exchange 没被声明，nack(requeue=false) 的消息会**直接消失**，
    // 既不在原队列、也不在任何死信队列里，日志上什么都不显示。

    /**
     * TODO 1：声明死信交换机（direct + 持久化，名字用常量 DLX_EXCHANGE）
     * 提示：照抄 directExchange() 的写法，把常量换成 DLX_EXCHANGE。
     */
    // @Bean
    // public DirectExchange dlxExchange() {
    //     return ...
    // }

    /**
     * TODO 2：声明死信队列（持久化即可，不要再给它配 deadLetterExchange，
     *         否则死信还能再死信一次，形成循环）
     * 提示：QueueBuilder.durable(DLX_QUEUE).build();
     */
    // @Bean
    // public Queue dlq() {
    //     return ...
    // }

    /**
     * TODO 3：把死信队列绑到死信交换机上，routing key 用 DLX_ROUTING_KEY
     * 提示：BindingBuilder.bind(dlq()).to(dlxExchange()).with(DLX_ROUTING_KEY);
     */
    // @Bean
    // public Binding dlxBinding() {
    //     return ...
    // }
}
