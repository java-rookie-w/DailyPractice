package org.wang.rabbitmqlab.springdemo.idempotency;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 幂等 demo 的消费端：tryMark -> 业务 -> ack / 重复 -> ack 丢弃。【标准实现】
 *
 * 与 reliability demo 的消费端只差一处：幂等存储从内存 Set 换成了 DedupStore（H2 唯一索引），
 * 其余手动 ACK 的写法完全一样 —— 这就是"存储可替换"的意义（面试时主动说这一点加分）。
 */
public class IdemConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdemConsumer.class);

    private final DedupStore dedupStore;

    public IdemConsumer(DedupStore dedupStore) {
        this.dedupStore = dedupStore;
    }

    @RabbitListener(queues = IdemConfig.QUEUE, ackMode = "MANUAL")
    public void receive(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String msgId = message.getMessageProperties().getMessageId();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        log.info("[Consumer] 收到 tag={} msgId={} body={}", tag, msgId, body);

        try {
            // ---- 1. 幂等占位：一步原子，false = 重复消息 ----
            if (msgId != null && !dedupStore.tryMark(msgId)) {
                log.warn("[Consumer] 重复消息，直接丢弃（业务不执行）msgId={}", msgId);
                channel.basicAck(tag, false);
                return;
            }

            // ---- 2. 业务处理 ----
            if (body.contains("FAIL")) {
                throw new RuntimeException("模拟业务失败: " + body);
            }
            log.info("[Consumer] 业务处理完成 body={}", body);

            // ---- 3. 成功 ack ----
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("[Consumer] 处理失败 msgId={} err={}", msgId, e.getMessage());
            // ---- 4. 失败：release 归还 id + nack ----
            // release 是幂等设计里最容易漏的一步：不还的话，补偿重投的同 id 消息会被误杀
            if (msgId != null) {
                dedupStore.release(msgId);
            }
            // 本 demo 没配死信队列，requeue=false 的消息会被直接丢弃（死信链路看 reliability）
            channel.basicNack(tag, false, false);
        }
    }
}
