package org.wang.rabbitmqlab.springdemo.jsonconverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import static org.wang.rabbitmqlab.springdemo.jsonconverter.JsonConfig.EXCHANGE;
import static org.wang.rabbitmqlab.springdemo.jsonconverter.JsonConfig.ROUTING_KEY;

/**
 * 发送端：直接发 POJO。【标准实现】
 * RabbitTemplate 内部会调 MessageConverter（Jackson2JsonMessageConverter）把 Order 转 JSON。
 * 如果没配 converter，默认 SimpleMessageConverter 会直接抛
 * MessageConversionException: "SimpleMessageConverter only supports String, byte[] and Serializable"
 */
public class JsonProducer {

    private static final Logger log = LoggerFactory.getLogger(JsonProducer.class);

    private final RabbitTemplate rabbitTemplate;

    private int seq = 0;

    public JsonProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 5000)
    public void produce() {
        Order order = new Order("ORDER-" + (++seq), "iPhone 17", 2);
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, order);
        log.info("[Send   ] 已发送对象 {}（到 broker 上是 JSON 字符串）", order);
    }
}
