package org.wang.rabbitmqlab.springdemo.helloworld;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 消费者（接收端）。
 *
 * 注意：这个类本身没有 @Component / @Service 注解！
 * 它是靠 Tut1Config 里的 @Bean + @Profile("receiver") 注册成 Spring Bean 的。
 * ——只有成为 Bean，@RabbitListener 才会被 Spring 扫描到并建立真正的监听容器。
 *
 * 如果想让它无条件生效、不依赖 profile，也可以改成在类上加 @Component；
 * 那样就不用 Tut1Config 里那两个 sender/receiver 的 @Bean 了。
 * 官方教程保留 profile 开关，是为了方便"只发"或"只收"两种跑法。
 */
@RabbitListener(queues = "hello") // 监听名为 hello 的队列
public class Tut1Receiver {

    /**
     * @RabbitHandler 标记真正处理消息的方法。
     *
     * 入参类型决定消息如何反序列化：这里写 String，
     * 对应发送端 RabbitTemplate.convertAndSend() 发送的字符串。
     * （Spring AMQP 默认用 SimpleMessageConverter，能按 Content-Type 自动转 String/byte[]/序列化对象）
     */
    @RabbitHandler
    public void receive(String in) {
        System.out.println(" [x] Received '" + in + "'");
    }
}
