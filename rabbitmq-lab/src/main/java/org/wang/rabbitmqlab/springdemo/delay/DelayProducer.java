package org.wang.rabbitmqlab.springdemo.delay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.wang.rabbitmqlab.springdemo.delay.DelayConfig.MESSAGE_LEVEL_QUEUE;
import static org.wang.rabbitmqlab.springdemo.delay.DelayConfig.QUEUE_LEVEL_QUEUE;

/**
 * 延迟队列发送端：队列级 vs 消息级两种玩法各发一遍。【标准实现】
 */
public class DelayProducer {

    private static final Logger log = LoggerFactory.getLogger(DelayProducer.class);

    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final RabbitTemplate rabbitTemplate;

    private int round = 0;

    public DelayProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 队列级 TTL：routing key 直接写队列名 = 走默认交换机（""，队列名自动是 binding key） */
    public void sendQueueLevel(String body) {
        // 第一个参数传 ""（默认交换机），第二个参数是队列名
        rabbitTemplate.convertAndSend("", QUEUE_LEVEL_QUEUE, body, persistent());
        log.info("[Send   {}] 队列级 -> {} (10s 后到期)", now(), QUEUE_LEVEL_QUEUE);
    }

    /**
     * 消息级 TTL：expiration 配在**每条消息**上，单位毫秒，且是 String 类型（AMQP 协议如此）。
     */
    public void sendMessageLevel(String body, int ttlMs) {
        rabbitTemplate.convertAndSend("", MESSAGE_LEVEL_QUEUE, body, m -> {
            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            m.getMessageProperties().setExpiration(String.valueOf(ttlMs));
            return m;
        });
        log.info("[Send   {}] 消息级 -> {} ttl={}ms body={}", now(), MESSAGE_LEVEL_QUEUE, ttlMs, body);
    }

    /**
     * 一轮把两个坑都造出来：
     *   1. 队列级：发出后精确 10s 死信
     *   2. 消息级：先发 B(8s) 再发 A(2s)
     *      —— B 在队头挡路，A 的 2s 到了也轮不到它被检查，
     *         结果 A 和 B 几乎同时(约 8s 时)死信。这就是"消息级 TTL 不精确"的现场。
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 30000)
    public void produce() {
        log.info("================ 延迟 demo 第 {} 轮 {} ================", ++round, now());
        sendQueueLevel("Q-队列级TTL(期望10s)");
        sendMessageLevel("M-B-消息级TTL(期望8s，队头)", 8000);
        sendMessageLevel("M-A-消息级TTL(期望2s，被B堵)", 2000);
    }

    private MessagePostProcessor persistent() {
        return m -> {
            m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return m;
        };
    }

    private String now() {
        return LocalTime.now().format(HHMMSS);
    }
}
