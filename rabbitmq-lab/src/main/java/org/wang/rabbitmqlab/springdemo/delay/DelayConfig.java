package org.wang.rabbitmqlab.springdemo.delay;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * delay 演示：TTL + 死信实现延迟队列。【标准实现】
 *
 * 跑它：--spring.profiles.active=delay
 * 练习骨架在 delay_skeleton 包（profile = delay-skeleton）。
 *
 * 核心面试点（原生 demo12 已用代码验证过，这里用 Spring 再走一遍）：
 *   1. 队列级 TTL（x-message-ttl）：队列定时器统一触发，**精确**
 *   2. 消息级 TTL（expiration）：消息到队头才被惰性检查，队头被长 TTL 挡住 → 后面全堵 → **不精确**
 *   3. 生产上的第三种：rabbitmq_delayed_message_exchange 插件（x-delayed-message），
 *      免队头阻塞、免"过期时间档位化"，但插件有性能代价且要装在 broker 上
 *
 * 拓扑：两条"等待队列"（消息在这躺够时间）到期后经同一个 DLX 进"就绪队列"被消费。
 */
@Profile("delay")
@Configuration
public class DelayConfig {

    /** 队列级 TTL：10 秒。进这个队列的消息一律 10s 后死信，由队列定时器保证精确 */
    public static final String QUEUE_LEVEL_QUEUE = "delay.q10";
    /** 消息级 TTL：队列本身不设 TTL，每条消息自带 expiration */
    public static final String MESSAGE_LEVEL_QUEUE = "delay.mlevel";

    public static final String DLX_EXCHANGE = "delay.dlx.exchange";
    public static final String READY_QUEUE = "delay.ready.queue";
    public static final String READY_ROUTING_KEY = "delay.ready";

    @Bean
    public DelayProducer delayProducer(org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate) {
        return new DelayProducer(rabbitTemplate);
    }

    @Bean
    public DelayConsumer delayConsumer() {
        return new DelayConsumer();
    }

    // ---------- 等待队列：消息在这里"睡觉" ----------

    /** 队列级 TTL = 10000ms。注意 TTL 配在队列参数上，对队列里所有消息生效 */
    @Bean
    public Queue queueLevelQueue() {
        return QueueBuilder.durable(QUEUE_LEVEL_QUEUE)
                .ttl(10000)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(READY_ROUTING_KEY)
                .build();
    }

    /**
     * 消息级 TTL 队列：不设 ttl，消息自己带 expiration。
     * 死信路由配置和上面一样 —— TTL 到期的消息从这里进 DLX。
     */
    @Bean
    public Queue messageLevelQueue() {
        return QueueBuilder.durable(MESSAGE_LEVEL_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(READY_ROUTING_KEY)
                .build();
    }

    // ---------- 死信交换机 + 就绪队列 ----------

    @Bean
    public DirectExchange delayDlx() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
    }

    /** 就绪队列：延迟到期的消息最终落这里，消费者在这里接（"关单"动作就是在这做） */
    @Bean
    public Queue readyQueue() {
        return QueueBuilder.durable(READY_QUEUE).build();
    }

    @Bean
    public Binding readyBinding() {
        return BindingBuilder.bind(readyQueue()).to(delayDlx()).with(READY_ROUTING_KEY);
    }
}
