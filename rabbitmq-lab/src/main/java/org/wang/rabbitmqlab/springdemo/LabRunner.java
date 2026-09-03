package org.wang.rabbitmqlab.springdemo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 一个"定时器"：Spring 容器启动完成后立刻回调 run()，睡够 duration 毫秒，然后关闭容器。
 *
 * 为什么需要它：所有 demo 的发送端都是靠 @Scheduled 定时发送的，容器一关定时任务就停。
 * 所以必须让容器活着一段时间，才能看到消息被发出和消费。
 * duration 默认 0（@Value 的默认值），此时应用会瞬间退出、什么都来不及做。
 *
 * 它属于"公共基础设施"而不是某个具体 demo，所以和启动类一起放在 springdemo 这一层。
 */
public class LabRunner implements CommandLineRunner {

    /**
     * 从 yml 读取 lab.client.duration；":0" 表示配置缺失时兜底为 0。
     * application.yml 里是 10000（10 秒）；需要更长观察窗口的 demo 在各自 profile 文件里覆盖。
     */
    @Value("${lab.client.duration:0}")
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
