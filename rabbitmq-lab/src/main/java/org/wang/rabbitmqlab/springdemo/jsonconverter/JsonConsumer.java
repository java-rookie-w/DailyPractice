package org.wang.rabbitmqlab.springdemo.jsonconverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 消费端：方法参数直接写 POJO 类型，converter 自动反序列化。【标准实现】
 *
 * 类型是从哪来的：Jackson2JsonMessageConverter 序列化时在消息头写了 __TypeId__
 * （本 demo 情况下其实是监听器直接用方法参数类型 Order，连 __TypeId__ 都不用读）。
 *
 * ⚠️ 相关坑（面试加分点）：
 *   1. 如果监听方法参数写 Object / 不写类型，就得靠 __TypeId__ 反查类名，
 *      这时受"信任包"限制 —— 不在 trusted packages 里的类会抛
 *      "The class 'xxx' is not in the trusted packages"（防反序列化攻击的机制）。
 *   2. 两端类路径/包名要一致，或者配置 TypeMapper 做类名映射。
 *   3. 如果发送端配了 Jackson、消费端没配，收到的 body 就是 byte[]，
 *      方法参数却写 Order → 转换失败。
 */
public class JsonConsumer {

    private static final Logger log = LoggerFactory.getLogger(JsonConsumer.class);

    @RabbitListener(queues = JsonConfig.QUEUE)
    public void receive(Order order) {
        // 走到这里时，JSON -> Order 的转换已经由 converter 做完了
        log.info("[Consumer] 收到对象 {}，直接当业务实体用", order);
    }
}
