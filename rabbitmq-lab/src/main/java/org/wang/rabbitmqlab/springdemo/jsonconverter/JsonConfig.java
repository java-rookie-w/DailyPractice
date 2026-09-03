package org.wang.rabbitmqlab.springdemo.jsonconverter;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * jsonconverter 演示：MessageConverter —— 发对象而不是发字符串。【标准实现】
 *
 * 跑它：--spring.profiles.active=jsonconverter
 * 练习骨架在 jsonconverter_skeleton 包（profile = jsonconverter-skeleton）。
 *
 * 面试考点：默认的 SimpleMessageConverter 只支持 String / byte[] / java.io.Serializable：
 *   1. 发自定义 POJO 直接抛 MessageConversionException
 *   2. Java 序列化跨语言不友好，还有反序列化安全风险
 * 所以生产标配 Jackson2JsonMessageConverter。
 *
 * ⚠️ 关键坑：converter 必须**两端一致** —— 生产端序列化、消费端反序列化用的是同一套。
 * 只在发送端配了、消费端没配（或反之），收到的就是 byte[] 或直接转换失败。
 *
 * @Bean 一个 MessageConverter，Boot 会**同时**把它装到 RabbitTemplate 和
 * 监听容器工厂上（两个自动配置都拿 ObjectProvider 唯一的那个）—— 这一句值得在面试里说出来。
 */
@Profile("jsonconverter")
@Configuration
public class JsonConfig {

    public static final String EXCHANGE = "json.exchange";
    public static final String QUEUE = "json.queue";
    public static final String ROUTING_KEY = "json.routingkey";

    /**
     * 整个 demo 最核心的一个 Bean。
     * RabbitTemplate 发送时用它把 Order 序列化成 JSON（并写入 __TypeId__ 头），
     * @RabbitListener 收到时用它反序列化回 Order。
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public JsonProducer jsonProducer(RabbitTemplate rabbitTemplate) {
        return new JsonProducer(rabbitTemplate);
    }

    @Bean
    public JsonConsumer jsonConsumer() {
        return new JsonConsumer();
    }

    @Bean
    public DirectExchange jsonExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue jsonQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding jsonBinding() {
        return BindingBuilder.bind(jsonQueue()).to(jsonExchange()).with(ROUTING_KEY);
    }
}
