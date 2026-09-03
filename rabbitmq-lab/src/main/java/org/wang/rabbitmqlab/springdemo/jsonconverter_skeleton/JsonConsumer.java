package org.wang.rabbitmqlab.springdemo.jsonconverter_skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 【练习版】消费端。
 */
public class JsonConsumer {

    private static final Logger log = LoggerFactory.getLogger(JsonConsumer.class);

    /**
     * ======== TODO 1：监听 json.sk.queue，方法参数直接写 Order 类型 ========
     * 写法：
     // @RabbitListener(queues = JsonConfig.QUEUE)
     // public void receive(Order order) {
     //     log.info("[Consumer] 收到对象 {}", order);
     // }
     *
     * 考点：参数类型写 Order，converter 就按这个类型反序列化；
     *      如果这里只写 Object 或不写类型，就得依赖消息头 __TypeId__，且受信任包限制。
     */
    // @RabbitListener(queues = JsonConfig.QUEUE)
    // public void receive(Order order) { ... }
}
