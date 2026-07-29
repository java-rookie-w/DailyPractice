package org.wang.rabbitmqlab.demo04_direct;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;     // 用于创建连接的工厂类

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Demo04 - 路由（Direct）生产者
 * 职责：向 direct 类型的 exchange 发送消息，指定 routing key
 * 特点：direct 根据 routing key 精确匹配，只把消息路由到 binding key 相同的 queue
 */
public class EmitLogDirect {

    // exchange 名称，消费者需要用同一个名称来绑定
    public static final String EXCHANGE_NAME = "direct_logs";

    static void main() throws IOException, TimeoutException {

        // 1. 创建连接工厂，配置 Broker 的连接信息
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.6.132");          // Broker 的 IP 地址
        factory.setPort(5672);                     // Broker 的 AMQP 端口
        factory.setUsername("admin");              // 登录用户名
        factory.setPassword("passw0rd");           // 登录密码
        factory.setVirtualHost("/mirror");         // 虚拟主机

        // 2. 建立连接和信道（try-with-resources 自动关闭）
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            // 3. 声明 exchange，类型为 direct
            //    direct：精确匹配模式，把消息路由到 binding key == routing key 的 queue
            //    例如 routing key="error" 只会路由到 binding key="error" 的 queue
            channel.exchangeDeclare(EXCHANGE_NAME, "direct");

            // 4. 设置消息的 routing key（严重级别）
            //    只有 binding key 和这个 routing key 完全匹配的 queue 才能收到消息
            String severity = "error";

            // 5. 构造消息内容
            String message = "Hello World! exchange direct";

            // 6. 发布消息到 exchange
            //    参数：exchange 名, routing key（决定消息路由到哪些 queue）, 消息属性, 消息体
            //    这里 routing key="error"，只有绑定 "error" 的 queue 能收到
            channel.basicPublish(EXCHANGE_NAME, severity, null, message.getBytes());

            // 打印发送日志
            System.out.println(" [x] Sent '" + severity + "':'" + message + "'");
        }
        // try-with-resources 自动关闭 channel 和 connection
    }
}
