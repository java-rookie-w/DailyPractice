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

        // ======== TODO 1：幂等占位（false = 重复消息 → ack 后 return） ========

        // ======== TODO 2：业务处理（body 含 "FAIL" 抛异常） ========

        // ======== TODO 3：成功 → channel.basicAck(tag, false) ========

        // ======== TODO 4：失败 catch 里 ========
        //   a) dedupStore.release(msgId)   ← 容易漏的一步
        //   b) channel.basicNack(tag, false, false)

        // TODO 写完之前先让编译通过：
        channel.basicAck(tag, false);
    }
}
