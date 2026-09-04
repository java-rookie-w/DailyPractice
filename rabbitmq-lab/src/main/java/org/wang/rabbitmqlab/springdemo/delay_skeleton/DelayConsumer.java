package org.wang.rabbitmqlab.springdemo.delay_skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 【练习版】延迟队列消费端。
 */
public class DelayConsumer {

    private static final Logger log = LoggerFactory.getLogger(DelayConsumer.class);

    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @RabbitListener(queues = DelayConfig.READY_QUEUE)
    public void receive(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        // ======== TODO 1：打印「到达时刻 + body + x-death」 ========
        // 到达时刻：LocalTime.now().format(HHMMSS)
        // x-death（在哪个队列死的、reason 是不是 expired）：
        //   message.getMessageProperties().getHeaders().get("x-death")
        // 拿到达时刻对照发送时刻，队列级(精确10s)和消息级(A 被堵到 8s)的差别就一目了然
        // TODO 1
        String arriveTime = LocalTime.now().format(HHMMSS);
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        log.info("[Consumer] 到达时刻={} body={} x-death={}", arriveTime, body, xDeath);
    }
}
