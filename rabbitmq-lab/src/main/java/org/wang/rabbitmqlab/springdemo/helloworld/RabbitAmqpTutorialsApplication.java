package org.wang.rabbitmqlab.springdemo.helloworld;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 启动类，也是这套教程最容易被误解的地方。
 *
 * 【核心设计】用 @Profile 控制"这一次运行扮演什么角色"：
 *   - 不传任何 profile（application.yml 里默认 active: usage_message）→ 只打印用法说明就退出；
 *   - 传 --spring.profiles.active=hello-world,sender,receiver → 才真正声明队列、启动发送者和接收者。
 *
 * 【重要澄清】spring.profiles.active 不是"新增一个 application 配置文件"，
 * 它只是给本次运行"贴标签"，标签本身不需要对应任何 application-xxx.yml 文件。
 * 本工程 resources/ 下确实只有 application.yml 一个文件，没有任何 application-*.yml。
 *
 * 正确的启动参数（IDE：Run/Debug Configurations -> Program arguments）：
 *   --spring.profiles.active=hello-world,sender,receiver
 */
@SpringBootApplication
@EnableScheduling // 开启定时任务调度，Tut1Sender 上的 @Scheduled 靠它才会生效
public class RabbitAmqpTutorialsApplication {

    /**
     * 仅当 usage_message 这个 profile 激活时，才注册这个 Bean。
     *
     * 作用：打印"该怎么用"的提示。这是官方教程加的保险丝——
     * 用户裸跑（不传参数）时不会静默无事发生，而是明确告诉你该怎么传参数。
     *
     * 注意：磁盘上没有任何叫 application-usage_message.yml 的文件，
     * "usage_message" 纯粹是一个标签名，只被 @Profile 用来当 Bean 开关。
     */
    @Profile("usage_message")
    @Bean
    public CommandLineRunner usage() {
        return args -> {
            System.out.println("This app uses Spring Profiles to control its behavior.\n");
            System.out.println("Sample usage: java -jar rabbit-tutorials.jar --spring.profiles.active=hello-world,sender");
        };
    }

    /**
     * 只要 usage_message 没激活，这个 runner 就生效（"!" 表示取反）。
     *
     * 它做的事：容器启动完成后，等待 tutorial.client.duration 毫秒（本工程配了 10000），
     * 给 sender 发消息、receiver 收消息留出时间窗口，到点后主动关闭 Spring 容器、程序退出。
     *
     * 若 duration=0，应用会在消息发出去之前就退出——
     * 这是"启动成功但什么都没发生"的另一个常见原因。
     */
    @Profile("!usage_message")
    @Bean
    public CommandLineRunner tutorial() {
        return new RabbitAmqpTutorialsRunner();
    }

    public static void main(String[] args) throws Exception {
        // 启动 Spring 容器：自动配置（RabbitTemplate 等）与组件扫描都从这里开始；
        // 容器就绪后，CommandLineRunner 会被自动回调执行。
        SpringApplication.run(RabbitAmqpTutorialsApplication.class, args);
    }
}
