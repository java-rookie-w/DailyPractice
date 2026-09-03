package org.wang.rabbitmqlab.springdemo.delay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 延迟队列消费端：在"就绪队列"上接延迟到期的消息。【标准实现】
 *
 * 消费端只看两样东西：到达时刻 + x-death（它从哪死过来的、为什么死）。
 * 把到达时刻和发送时刻一对，队列级/消息级谁精确一目了然。
 */
public class DelayConsumer {

    private static final Logger log = LoggerFactory.getLogger(DelayConsumer.class);

    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @RabbitListener(queues = DelayConfig.READY_QUEUE)
    public void receive(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        // x-death：reason=expired 说明是 TTL 到期死信过来的（另两种：rejected / maxlen）
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");

        log.info("[Ready  {}] 收到 body={} x-death={}", LocalTime.now().format(HHMMSS), body, xDeath);
    }
}
