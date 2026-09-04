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
 * 监听容器工厂上 —— 这一句值得在面试里说出来。
 *
 * 到底谁装的：RabbitAutoConfiguration（org.springframework.boot.autoconfigure.amqp）
 *   ① 发送端：RabbitTemplateConfigurer#configureRabbitTemplate
 *   ② 消费端：SimpleRabbitListenerContainerFactoryConfigurer#configure
 * 两处都是 messageConverter.getIfUnique() —— "容器里**唯一**的那个 MessageConverter Bean"。
 * ⚠️ 所以别定义第二个 MessageConverter 类型的 Bean：getIfUnique 拿不到唯一实例时
 *    返回 null，两端静默退回 SimpleMessageConverter，发 Order 就开始抛异常。
 * ⚠️ 自己 new RabbitTemplate / 手搓容器工厂 Bean，不走这条自动配置，要手动 setMessageConverter。
 */
@Profile("jsonconverter")
@Configuration
public class JsonConfig {

    public static final String EXCHANGE = "json.exchange";
    public static final String QUEUE = "json.queue";
    public static final String ROUTING_KEY = "json.routingkey";

    /**
     * 整个 demo 最核心的一个 Bean：发送端序列化 + 消费端反序列化用的是同一个实例。
     *
     * 发送时（convertAndSend 传的是 Order 对象）它做三件事：
     *   1. 用 Jackson 把 Order 序列化成 JSON 字节；
     *   2. 设消息属性 content_type = application/json；
     *   3. 写消息头 __TypeId__ = org.wang.rabbitmqlab.springdemo.jsonconverter.Order
     *      （类的全限定名，broker 和消费端都靠它"认人"）。
     * 可以用 RabbitMQ 管理台 Get Messages 看到这具"尸体"—— 就是纯 JSON 文本加一堆头。
     *
     * 接收时（@RabbitListener 的方法参数声明成 Order）：
     *   优先用**方法参数类型**（inferred type）反序列化 —— 这也是推荐写法，
     *   参数类型就是契约；只有参数声明成 Object/Message 时才完全按 __TypeId__ 找类。
     *
     * ⚠️ 安全点：完全信任 __TypeId__ 意味着别人可以指着你 classpath 里的任意类让你实例化。
     *    生产上应显式收紧信任范围：new Jackson2JsonMessageConverter(trustedPackages)，
     *    只放行自己团队的包（如 "org.wang.**"）。跨服务/跨语言时更要注意 ——
     *    对方进程里根本没有 Order 这个类，__TypeId__ 就是个误导，该用通用的 Map/JSON 契约。
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
