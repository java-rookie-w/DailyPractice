package org.wang.rabbitmqlab.springdemo.idempotency_skeleton;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 【练习版】幂等 demo 消费端。手动 ACK 的标准参数：(Message, Channel)。
 *
 * 骨架要点：事务边界在 Service 上，ack 必须发生在事务**提交之后**。
 */
public class IdemConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdemConsumer.class);

    private final IdemOrderService orderService;

    public IdemConsumer(IdemOrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = IdemConfig.QUEUE, ackMode = "MANUAL")
    public void receive(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        // ======== TODO 1：从消息头取业务键，而不是拿 messageId 当幂等键 ========
        String bizKey = message.getMessageProperties().getHeader(IdemProducer.ORDER_ID_HEADER);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        log.info("[Consumer] 收到 tag={} messageId={} bizKey={} body={}", tag, messageId, bizKey, body);

        if (bizKey == null) {
            log.error("[Consumer] 缺少业务键 {}，无法幂等，丢弃 tag={}", IdemProducer.ORDER_ID_HEADER, tag);
            channel.basicNack(tag, false, false);
            return;
        }

        try {
            // ======== TODO 2：调 Service（事务在它返回时提交），成功/重复都要 ack ========
            IdemOrderService.Result result = orderService.handle(bizKey);
            if (result == IdemOrderService.Result.DUPLICATE) {
                log.warn("[Consumer] 重复消息，业务未执行，直接 ack 丢弃 bizKey={}", bizKey);
            } else {
                log.info("[Consumer] 业务处理完成，ack bizKey={}", bizKey);
            }
            channel.basicAck(tag, false);

        } catch (Exception e) {
            // ======== TODO 3：失败 → nack（requeue=false） ========
            // 注意这里**不需要** release：事务回滚时占位已经没了。
            // 换成 Redis / 跨库去重表才必须 dedupStore.release(bizKey)。
            log.error("[Consumer] 处理失败，事务已回滚（占位随之消失）bizKey={} err={}", bizKey, e.getMessage());
            channel.basicNack(tag, false, false);
        }
    }
}
