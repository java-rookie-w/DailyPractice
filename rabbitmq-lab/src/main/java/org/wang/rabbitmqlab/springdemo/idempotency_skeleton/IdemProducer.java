package org.wang.rabbitmqlab.springdemo.idempotency_skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import static org.wang.rabbitmqlab.springdemo.idempotency_skeleton.IdemConfig.EXCHANGE;
import static org.wang.rabbitmqlab.springdemo.idempotency_skeleton.IdemConfig.ROUTING_KEY;

/**
 * 【练习版】幂等 demo 发送端。
 */
public class IdemProducer {

    private static final Logger log = LoggerFactory.getLogger(IdemProducer.class);

    private final RabbitTemplate rabbitTemplate;

    private int round = 0;

    public IdemProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * TODO 1：用指定 id 发消息（模拟同一条消息被投递多次）
     * 写法：
     // rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, body,
     //         persistentWithId(msgId), new CorrelationData(msgId));
     *
     * 关键点：msgId 是发送方显式指定的**业务唯一键**，
     * 不是 CorrelationData 自己生成的批次编号 —— 两者职责不同。
     */
    public void sendWithId(String msgId, String body) {
        // TODO 1
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, body, persistentWithId(msgId), new CorrelationData(msgId));
    }

    /**
     * TODO 2：定时造三种现场（每 20s 一轮）
     *   a) 同一 id 连发 3 次正常消息（如 "dup-001"）→ 消费端应只处理 1 次
     *   b) 发一条 body 含 "FAIL" 的消息（如 "retry-001"）→ 消费端失败并 release
     *   c) 紧接着用**同一个 id** 再发一条正常消息 → 因为 release 过了，应能正常处理
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 20000)
    public void produce() {
        // TODO 2
        log.info("================ 幂等 demo(骨架) 第 {} 轮 ================", ++round);
        // a) 连发 3 次同一 id 的正常消息
        String dupId = "dup-001";
        for (int i = 1; i <= 3; i++) {
            sendWithId(dupId, "正常消息 #" + i);
        }
    }

    // 小工具（已写好，直接用）：持久化 + messageId
    private MessagePostProcessor persistentWithId(String id) {
        return message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(id);
            return message;
        };
    }
}
