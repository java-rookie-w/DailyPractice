package org.wang.rabbitmqlab.springdemo.idempotency;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 幂等 demo 的消费端：调业务 Service → 事务提交后再 ack。【标准实现】
 *
 * 与 reliability demo 的消费端相比，差异只有"业务放哪"：
 *   reliability 把业务写在消费方法里（演示 ack/nack 本身的语义），
 *   这里把业务抽成 IdemOrderService，为了让它能挂 @Transactional —— 这才是生产的形状。
 *
 * ⚠️ 最容易被问倒的一点：**ack 必须在事务提交之后**。
 *    所以这里没有给 receive() 加 @Transactional，而是让 Service 方法结束时提交事务，
 *    回来再 ack。反过来写（事务包住 ack）就会出现"消息已确认、业务回滚"的丢消息事故。
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
        // messageId：每条消息自己的编号，**重发时会变**，所以不能拿它做幂等键
        String messageId = message.getMessageProperties().getMessageId();
        // bizKey：发送方塞在消息头里的业务键（订单号），重发保持不变 —— 幂等用它
        String bizKey = message.getMessageProperties().getHeader(IdemProducer.ORDER_ID_HEADER);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        log.info("[Consumer] 收到 tag={} messageId={} bizKey={} body={}", tag, messageId, bizKey, body);

        if (bizKey == null) {
            // 连业务键都没有的消息，没法做幂等，直接丢弃（生产上应进死信 + 告警）
            log.error("[Consumer] 缺少业务键 {}，无法幂等，丢弃 tag={}", IdemProducer.ORDER_ID_HEADER, tag);
            channel.basicNack(tag, false, false);
            return;
        }

        try {
            // 事务在 handle() 返回时提交；下面的 ack 一定发生在提交之后
            IdemOrderService.Result result = orderService.handle(bizKey);

            if (result == IdemOrderService.Result.DUPLICATE) {
                log.warn("[Consumer] 重复消息，业务未执行，直接 ack 丢弃 bizKey={}", bizKey);
            } else {
                log.info("[Consumer] 业务处理完成，ack bizKey={}", bizKey);
            }
            channel.basicAck(tag, false);

        } catch (Exception e) {
            // 事务已回滚：占位的 INSERT 跟着一起没了，所以这里**不需要**调 release。
            // 换成 Redis / 跨库去重表，就必须在这里 dedupStore.release(bizKey)，
            // 否则补偿重投的同 key 消息会被误杀成"重复"，消息等于永久丢失。
            log.error("[Consumer] 处理失败，事务已回滚（占位随之消失）bizKey={} err={}", bizKey, e.getMessage());
            channel.basicNack(tag, false, false);
        }
    }
}
