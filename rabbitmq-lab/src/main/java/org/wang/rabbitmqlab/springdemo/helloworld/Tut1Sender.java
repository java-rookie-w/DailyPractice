package org.wang.rabbitmqlab.springdemo.helloworld;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 生产者（发送端）。
 *
 * 同样没有 @Component 注解，靠 Tut1Config 里的 @Bean + @Profile("sender") 注册成 Bean。
 */
public class Tut1Sender {

    /**
     * RabbitTemplate：Spring AMQP 的消息发送模板。
     * 由 spring-boot-starter-amqp 根据 application.yml 里的 spring.rabbitmq.*
     * 自动配置好连接、序列化器等，直接注入即可用，不用自己管 Connection/Channel。
     */
    @Autowired
    private RabbitTemplate template;

    /**
     * 注入 Tut1Config 里声明的 hello 队列 Bean，通过 queue.getName() 拿队列名——
     * 好处是队列名只在配置里写一次，避免两边硬编码字符串写错。
     */
    @Autowired
    private Queue queue;

    /**
     * 定时发送：容器启动后延迟 500ms 发第一条，之后每次结束后 1000ms 再发（fixedDelay）。
     * 需要主类 SpringDemoApplication 上的 @EnableScheduling 才会生效。
     */
    @Scheduled(fixedDelay = 1000, initialDelay = 500)
    public void send() {
        String message = "Hello World!";
        // convertAndSend(routingKey, 消息对象)：
        // 这里没指定交换机，走的是默认交换机（名字为空字符串 ""），
        // 默认交换机会把消息按 routingKey 精确投递到"同名队列"，
        // 所以 routingKey 直接填队列名 hello 即可。
        this.template.convertAndSend(queue.getName(), message);
        System.out.println(" [x] Sent '" + message + "'");
    }
}
