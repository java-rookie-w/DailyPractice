package org.wang.rabbitmqlab.springdemo.jsonconverter_skeleton;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 【练习版】converter demo 的装配。
 *
 * 跑它：--spring.profiles.active=jsonconverter-skeleton
 * 要建的东西：1 个 MessageConverter Bean + 拓扑三件套 + 生产/消费 Bean。
 */
@Profile("jsonconverter-skeleton")
@Configuration
public class JsonConfig {

    public static final String EXCHANGE = "json.sk.exchange";
    public static final String QUEUE = "json.sk.queue";
    public static final String ROUTING_KEY = "json.sk.routingkey";

    @Bean
    public JsonProducer jsonProducer(RabbitTemplate rabbitTemplate) {
        return new JsonProducer(rabbitTemplate);
    }

    @Bean
    public JsonConsumer jsonConsumer() {
        return new JsonConsumer();
    }

    /**
     * TODO 1：整个 demo 最核心的一个 Bean
     * 写法：
     // @Bean
     // public MessageConverter jacksonMessageConverter() {
     //     return new Jackson2JsonMessageConverter();
     // }
     * 效果：这一个 Bean 会被 Boot 同时装到 RabbitTemplate 和监听容器工厂上，
     *      两端的序列化/反序列化就一致了 —— converter 必须**两端一致**，这是本 demo 的主坑。
     */
    // @Bean
    // public MessageConverter jacksonMessageConverter() { ... }

    /** TODO 2：交换机（direct + 持久化，名字用 EXCHANGE） */
    // @Bean
    // public DirectExchange jsonExchange() { ... }

    /** TODO 3：队列（持久化即可） */
    // @Bean
    // public Queue jsonQueue() { ... }

    /** TODO 4：绑定（routing key 用 ROUTING_KEY） */
    // @Bean
    // public Binding jsonBinding() { ... }
}
