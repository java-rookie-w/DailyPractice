package org.wang.rabbitmqlab.springdemo.jsonconverter_skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import static org.wang.rabbitmqlab.springdemo.jsonconverter_skeleton.JsonConfig.EXCHANGE;
import static org.wang.rabbitmqlab.springdemo.jsonconverter_skeleton.JsonConfig.ROUTING_KEY;

/**
 * 【练习版】发送端。
 */
public class JsonProducer {

    private static final Logger log = LoggerFactory.getLogger(JsonProducer.class);

    private final RabbitTemplate rabbitTemplate;

    private int seq = 0;

    public JsonProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * ======== TODO 1：定时发一个 Order 对象 ========
     * 写法：
     // Order order = new Order("ORDER-" + (++seq), "iPhone 17", 2);
     // rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, order);
     *
     * 注意：参数直接传对象。若没配 converter，默认 SimpleMessageConverter 会抛
     * MessageConversionException —— 这正是本 demo 要展示的第一件事。
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 5000)
    public void produce() {
        // TODO 1
    }
}
