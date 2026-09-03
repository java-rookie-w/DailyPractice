package org.wang.rabbitmqlab.springdemo.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

import static org.wang.rabbitmqlab.springdemo.idempotency.IdemConfig.EXCHANGE;
import static org.wang.rabbitmqlab.springdemo.idempotency.IdemConfig.ROUTING_KEY;

/**
 * 幂等 demo 的发送端：制造「重复投递」和「失败后重发」两种现场。【标准实现】
 *
 * 关键设计：msgId 由发送方显式指定（业务唯一键），
 * 而不是用 CorrelationData 的 id —— 后者只是回调对号用的批次编号。
 */
public class IdemProducer {

    private static final Logger log = LoggerFactory.getLogger(IdemProducer.class);

    private final RabbitTemplate rabbitTemplate;

    private int round = 0;

    public IdemProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 用指定 id 发消息：模拟"同一条消息被投递多次"（broker 的 at-least-once） */
    public void sendWithId(String msgId, String body) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, body, persistentWithId(msgId),
                new CorrelationData(msgId));
        log.info("[Send   ] id={} body={}", msgId, body);
    }

    /** 一次性把三种现场都造出来 */
    @Scheduled(initialDelay = 1000, fixedDelay = 20000)
    public void produce() {
        log.info("================ 幂等 demo 第 {} 轮 ================", ++round);

        // 现场一：同一 id 连发 3 次（正常消息）—— 应只处理 1 次
        // 模拟：网络抖动导致 broker 重投 / 消费者 ack 前宕机后 broker 重发
        for (int i = 1; i <= 3; i++) {
            sendWithId("dup-001", "订单A 第" + i + "次投递(同一msgId)");
        }

        // 现场二：业务失败（body 含 FAIL）→ 消费端 release + nack 丢弃
        sendWithId("retry-001", "订单B FAIL 业务失败一次");

        // 现场三：同一个 id 紧接着重发（模拟补偿重投）
        // 因为上一步失败时已经 release，这条会被当成第一次正常处理 —— 这就是 release 的意义
        sendWithId("retry-001", "订单B 补偿重投(同msgId)");
    }

    private MessagePostProcessor persistentWithId(String id) {
        return message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(id);
            return message;
        };
    }
}
