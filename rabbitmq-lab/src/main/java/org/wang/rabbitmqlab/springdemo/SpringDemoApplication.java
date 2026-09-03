package org.wang.rabbitmqlab.springdemo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * springdemo 下**所有 demo 的统一启动类**。
 *
 * 【为什么放在这一层】
 * @SpringBootApplication 的组件扫描范围是"启动类所在包及其子包"。
 * 本类在 org.wang.rabbitmqlab.springdemo 下，helloworld / reliability / idempotency / delay /
 * jsonconverter（及各自的 _skeleton 练习包）都在扫描范围内。
 * 如果把它塞进 helloworld 包（官方教程的原始位置），兄弟包会被漏扫 ——
 * 表现是"启动成功但一个 Bean 都没注册，什么都不发生"。
 *
 * 【用 profile 选择跑哪个 demo】
 * IDE 设置位置：Run/Debug Configurations -> Program arguments
 *
 *   想跑的 demo            Program arguments
 *   ---------------------  -------------------------------------------------------------
 *   helloworld（tut1）     --spring.profiles.active=hello-world,sender,receiver
 *   reliability 标准实现    --spring.profiles.active=reliability
 *   reliability 练习骨架    --spring.profiles.active=reliability-skeleton
 *   idempotency 标准实现    --spring.profiles.active=idempotency
 *   idempotency 练习骨架    --spring.profiles.active=idempotency-skeleton
 *   delay 标准实现          --spring.profiles.active=delay
 *   delay 练习骨架          --spring.profiles.active=delay-skeleton
 *   jsonconverter 标准实现  --spring.profiles.active=jsonconverter
 *   jsonconverter 练习骨架  --spring.profiles.active=jsonconverter-skeleton
 *   只打印本说明后退出      --spring.profiles.active=usage_message
 *
 * 不传任何参数时，跑的是 application.yml 里 spring.profiles.active 的默认值。
 * ⚠️ 同一个 demo 的"标准版"和"-skeleton 版"不能同时激活：两套 @Bean 方法名相同。
 *
 * 【profile 三层含义，别混】
 *   1. 业务标签（hello-world / sender / receiver / reliability / ...）：
 *      纯开关，只被 @Profile 用来决定"这个 Bean 注不注册"，磁盘上不需要有对应文件。
 *   2. 配置文件标签：只有你自己建了 application-{profile}.yml 才会额外加载，
 *      且优先级高于 application.yml（同名 key 覆盖，其余合并）。
 *      本工程里 reliability / idempotency / delay / jsonconverter 四组都是"两层都占"：
 *      既是 Bean 开关，也各自对应一个配置文件。
 *   3. usage_message：官方教程的保险丝，裸跑时明确告诉你该怎么传参，而不是静默无事发生。
 */
@SpringBootApplication
@EnableScheduling // Tut1Sender、ReliabilityProducer 上的 @Scheduled 都靠它才生效
public class SpringDemoApplication {

    /**
     * 仅当 usage_message 激活时注册：打印用法说明后退出。
     * 磁盘上没有 application-usage_message.yml —— 它纯粹是个标签名。
     */
    @Profile("usage_message")
    @Bean
    public CommandLineRunner usage() {
        return args -> {
            System.out.println();
            System.out.println("本工程用 profile 选择跑哪个 demo。");
            System.out.println("IDE 设置位置：Run/Debug Configurations -> Program arguments");
            System.out.println();
            System.out.println("  helloworld（tut1）    --spring.profiles.active=hello-world,sender,receiver");
            System.out.println("  reliability 标准实现   --spring.profiles.active=reliability");
            System.out.println("  reliability 练习骨架   --spring.profiles.active=reliability-skeleton");
            System.out.println("  idempotency 标准实现   --spring.profiles.active=idempotency");
            System.out.println("  idempotency 练习骨架   --spring.profiles.active=idempotency-skeleton");
            System.out.println("  delay 标准实现         --spring.profiles.active=delay");
            System.out.println("  delay 练习骨架         --spring.profiles.active=delay-skeleton");
            System.out.println("  jsonconverter 标准实现 --spring.profiles.active=jsonconverter");
            System.out.println("  jsonconverter 练习骨架 --spring.profiles.active=jsonconverter-skeleton");
            System.out.println();
            System.out.println("不传参数时跑 application.yml 里 spring.profiles.active 的默认值。");
            System.out.println("同一 demo 的标准版和 -skeleton 版不能同时激活（Bean 名冲突）。");
            System.out.println();
        };
    }

    /**
     * 只要 usage_message 没激活就生效（"!" 表示取反）：
     * 容器启动后存活 lab.client.duration 毫秒，给收发消息留出时间窗口，到点主动关闭容器。
     * 若 duration=0，应用会在消息发出去之前就退出 —— 这是"启动成功但什么都没发生"的另一个常见原因。
     */
    @Profile("!usage_message")
    @Bean
    public CommandLineRunner tutorial() {
        return new LabRunner();
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(SpringDemoApplication.class, args);
    }
}
