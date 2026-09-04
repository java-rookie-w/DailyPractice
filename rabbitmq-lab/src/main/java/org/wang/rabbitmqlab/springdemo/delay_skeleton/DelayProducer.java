package org.wang.rabbitmqlab.springdemo.delay_skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.wang.rabbitmqlab.springdemo.delay_skeleton.DelayConfig.MESSAGE_LEVEL_QUEUE;
import static org.wang.rabbitmqlab.springdemo.delay_skeleton.DelayConfig.QUEUE_LEVEL_QUEUE;

/**
 * 【练习版】延迟队列发送端。
 */
public class DelayProducer {

    private static final Logger log = LoggerFactory.getLogger(DelayProducer.class);

    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final RabbitTemplate rabbitTemplate;

    private int round = 0;

    public DelayProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * ======== TODO 1：发队列级 TTL 消息 ========
     * 写法（默认交换机 ""，routing key = 队列名）：
     // rabbitTemplate.convertAndSend("", QUEUE_LEVEL_QUEUE, body, persistent());
     */
    public void sendQueueLevel(String body) {
        rabbitTemplate.convertAndSend("", QUEUE_LEVEL_QUEUE, body, persistent());
    }

    /**
     * ======== TODO 2：发消息级 TTL 消息 ========
     * 在 MessagePostProcessor 里设：
     // m.getMessageProperties().setExpiration(String.valueOf(ttlMs));  // 单位 ms，String 类型！
     */
    public void sendMessageLevel(String body, int ttlMs) {
        rabbitTemplate.convertAndSend("", MESSAGE_LEVEL_QUEUE, body, m -> {
            m.getMessageProperties().setExpiration(String.valueOf(ttlMs));
            return m;
        });
    }

    /**
     * ======== TODO 3：每 30s 造一轮现场 ========
     * 顺序（不能反）：
     // sendQueueLevel("Q-队列级TTL(期望10s)");
     // sendMessageLevel("M-B(期望8s，先发、在队头)", 8000);
     // sendMessageLevel("M-A(期望2s，后发、被堵)", 2000);
     * 先 B 后 A 才能造出"队头阻塞"：A 的 2s 早到了，但要等队头 B 的 8s 到了才一起死信。
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 30000)
    public void produce() {
        // TODO 3
        log.info("================ 延迟 demo(骨架) 第 {} 轮 {} ================", ++round, now());
        sendQueueLevel("Q-队列级TTL(期望10s)");
        sendMessageLevel("M-B(期望8s，先发、在队头)", 8000);
        sendMessageLevel("M-A(期望2s，后发、被堵)", 2000);
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
