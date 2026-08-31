package org.wang.rabbitmqlab.springdemo.helloworld;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Tut1（Hello World）的装配类：声明队列，并按 profile 决定注册发送者还是接收者。
 *
 * 【关键】@Profile({"tut1", "hello-world"}) 加在整个配置类上：
 * 只有当 tut1 或 hello-world 之一被激活时，这个类才会被加载。
 * 这也是为什么启动时必须显式传 --spring.profiles.active=hello-world,...
 * ——否则连下面的 hello 队列 Bean 都不会存在，
 *   Tut1Sender 里 @Autowired Queue 会直接抛 NoSuchBeanDefinitionException。
 */
@Profile({"tut1", "hello-world"})
@Configuration
public class Tut1Config {

    /**
     * 声明队列。
     *
     * Spring Boot 启动时，会自动把容器中 Queue / Exchange / Binding 类型的 Bean
     * 同步声明到 RabbitMQ 服务器上（不存在就创建，已存在则校验参数是否一致）。
     *
     * durable("hello")：队列名 hello，且持久化（broker 重启后队列不丢）。
     * quorum()：仲裁队列（Quorum Queue），RabbitMQ 3.8+ 的复制型队列，
     *           基于 Raft 协议多副本，比老的镜像队列更可靠、行为更可预测。
     *           注意：仲裁队列不支持"非持久化消息优先级"等少数经典队列特性。
     */
    @Bean
    public Queue hello() {
        return QueueBuilder.durable("hello").quorum().build();
    }

    /**
     * 仅当 receiver 被激活时，才注册消费者 Bean。
     * 只有成为 Spring Bean，Tut1Receiver 上的 @RabbitListener 才会被扫描并建立监听容器。
     */
    @Profile("receiver")
    @Bean
    public Tut1Receiver receiver() {
        return new Tut1Receiver();
    }

    /** 仅当 sender 被激活时，才注册生产者 Bean（否则没人发消息）。 */
    @Profile("sender")
    @Bean
    public Tut1Sender sender() {
        return new Tut1Sender();
    }
}
