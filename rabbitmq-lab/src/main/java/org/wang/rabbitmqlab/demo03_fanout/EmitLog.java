package org.wang.rabbitmqlab.demo03_fanout;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;     // 用于创建连接的工厂类

import java.io.IOException;
import java.nio.charset.StandardCharsets;         // 字符编码，用于消息体转换
import java.util.concurrent.TimeoutException;

/**
 * Demo03 - 发布/订阅（Fanout）生产者
 * 职责：向 fanout 类型的 exchange 发送消息
 * 特点：fanout 会忽略 routing key，把消息广播给所有绑定的 queue
 */
public class EmitLog {

    // exchange 名称，消费者需要用同一个名称来绑定
    public static final String EXCHANGE_NAME = "logs";

    static void main() throws IOException, TimeoutException {

        System.out.println("EmitLog");

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

            // 3. 声明 exchange，类型为 fanout
            //    fanout：广播模式，把消息路由到所有绑定的 queue，忽略 routing key
            //    其他类型：direct（精确匹配）、topic（模式匹配）、headers（header 匹配）
            channel.exchangeDeclare(EXCHANGE_NAME, "fanout");

            // 4. 声明一个临时队列（由 Broker 自动生成唯一名称）
            //    返回的队列名是随机的，如 "amq.gen-xxx"
            String queue = channel.queueDeclare().getQueue();

            // 5. 把临时队列绑定到 exchange，routing key 为空（fanout 不关心 routing key）
            channel.queueBind(queue, EXCHANGE_NAME, "");

            // 6. 构造消息内容
            String message = "Hello World! exchange fanout";

            // 7. 发布消息到 exchange
            //    参数：exchange 名, routing key（fanout 忽略此参数）, 消息属性, 消息体
            //    消息会广播给所有绑定到 "logs" exchange 的 queue
            channel.basicPublish(EXCHANGE_NAME, "", null, message.getBytes(StandardCharsets.UTF_8));

            // 打印发送日志
            System.out.println(" [x] Sent '" + message + "'");
        }
        // try-with-resources 自动关闭 channel 和 connection
    }
}
