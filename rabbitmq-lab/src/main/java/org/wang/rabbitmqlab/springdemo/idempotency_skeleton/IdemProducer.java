package org.wang.rabbitmqlab.springdemo.idempotency_skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

import static org.wang.rabbitmqlab.springdemo.idempotency_skeleton.IdemConfig.EXCHANGE;
import static org.wang.rabbitmqlab.springdemo.idempotency_skeleton.IdemConfig.ROUTING_KEY;

/**
 * 【练习版】幂等 demo 发送端。
 *
 * 🔑 本 demo 的题眼就在 sendOrder()：messageId 每次都新生成，业务键固定不变。
 *    为什么幂等键不能用 messageId？见标准版 IdemProducer 的类注释。
 */
public class IdemProducer {

    public static final String ORDER_ID_HEADER = "order_id";

    private static final Logger log = LoggerFactory.getLogger(IdemProducer.class);

    private final RabbitTemplate rabbitTemplate;

    private int round = 0;

    public IdemProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * TODO 1：发一笔订单
     *   a) messageId = UUID.randomUUID().toString()   ← 每条消息都是新的
     *   b) 消息头塞业务键：setHeader(ORDER_ID_HEADER, orderId)
     *   c) convertAndSend(EXCHANGE, ROUTING_KEY, body, processor, new CorrelationData(messageId))
     */
    public void sendOrder(String orderId, String body) {
        // TODO 1
        String messageId = UUID.randomUUID().toString();
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, body, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(messageId);
            message.getMessageProperties().setHeader(ORDER_ID_HEADER, orderId);
            return message;
        }, new CorrelationData(messageId));
        log.info("[Send   ] messageId={} orderId={} body={}", messageId, orderId, body);
    }

    /**
     * TODO 2：定时造四种现场（每 20s 一轮）
     *   a) 同一订单 ORDER-1001 连发 3 次（3 个不同 messageId）→ 只处理 1 次
     *   b) ORDER-1002 正常发一次 → 正常处理
     *   c) ORDER-9999（不存在）→ 业务失败 → 事务回滚，占位自动消失
     *   d) ORDER-9999 再发一次（补偿重投）→ 能重新进来重试（占位已回滚）
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 20000)
    public void produce() {
        // TODO 2
        log.info("================ 幂等 demo(骨架) 第 {} 轮 ================", ++round);
        for (int i = 1; i <= 3; i++) {
            sendOrder("ORDER-1001", "订单 ORDER-1001 第 " + i + " 次投递");
        }
        sendOrder("ORDER-1002", "订单 ORDER-1002 正常支付");
        sendOrder("ORDER-9999", "订单 ORDER-9999 不存在，业务必然失败");
        sendOrder("ORDER-9999", "订单 ORDER-9999 补偿重投");
    }
}
