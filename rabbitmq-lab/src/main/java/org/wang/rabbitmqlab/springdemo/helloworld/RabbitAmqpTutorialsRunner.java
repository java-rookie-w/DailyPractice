package org.wang.rabbitmqlab.springdemo.helloworld;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 一个"定时器"：Spring 容器启动完成后立刻回调 run()，睡够 duration 毫秒，然后关闭容器。
 *
 * 为什么需要它：Tut1Sender 是靠 @Scheduled 定时发送的，容器一关定时任务就停。
 * 所以必须让容器活着一段时间，才能看到消息被发出和消费。
 * duration 默认 0（@Value 的默认值），此时应用会瞬间退出、什么都来不及做。
 */
public class RabbitAmqpTutorialsRunner implements CommandLineRunner {

    /**
     * 从 application.yml 读取 tutorial.client.duration；":0" 表示配置缺失时兜底为 0。
     * 本工程配置为 10000，即启动后存活 10 秒。
     */
    @Value("${tutorial.client.duration:0}")
    private int duration;

    // 注入 Spring 容器本身，用于到点后主动关闭
    @Autowired
    private ConfigurableApplicationContext ctx;

    @Override
    public void run(String... arg0) throws Exception {
        System.out.println("Ready ... running for " + duration + "ms");
        Thread.sleep(duration);
        ctx.close(); // 关闭容器 -> 监听容器停止、定时任务停止 -> 应用退出
    }
}
