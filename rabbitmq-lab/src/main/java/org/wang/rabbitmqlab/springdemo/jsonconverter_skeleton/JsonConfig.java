package org.wang.rabbitmqlab.springdemo.jsonconverter_skeleton;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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
     * 谁装的：RabbitAutoConfiguration 里 RabbitTemplateConfigurer#configureRabbitTemplate 和
     *      SimpleRabbitListenerContainerFactoryConfigurer#configure，都是 getIfUnique()。
     * ⚠️ 定义第二个 MessageConverter Bean 会让 getIfUnique 返回 null，
     *    两端静默退回 SimpleMessageConverter（不报错），发 Order 就抛异常。
     *
     * 它在发送时干三件事：
     *   1. 用 Jackson 把 POJO 序列化成 JSON 字节；
     *   2. 设 content_type = application/json；
     *   3. 写消息头 __TypeId__ = 类的全限定名（消费端靠它"认人"）。
     * 接收时优先用 @RabbitListener 方法参数类型（inferred）反序列化，参数类型就是契约。
     * ⚠️ 生产要收紧信任范围：new Jackson2JsonMessageConverter("org.wang.**")，
     *    完全信任 __TypeId__ 等于允许别人指着你 classpath 里的任意类实例化。
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** TODO 2：交换机（direct + 持久化，名字用 EXCHANGE） */
    @Bean
    public DirectExchange jsonExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    /** TODO 3：队列（持久化即可） */
    @Bean
    public Queue jsonQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    /** TODO 4：绑定（routing key 用 ROUTING_KEY） */
    @Bean
    public Binding jsonBinding() {
        return BindingBuilder.bind(jsonQueue()).to(jsonExchange()).with(ROUTING_KEY);
    }
}
