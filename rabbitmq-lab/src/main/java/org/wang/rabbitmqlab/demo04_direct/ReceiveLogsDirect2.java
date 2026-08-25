package org.wang.rabbitmqlab.demo04_direct;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.DeliverCallback;       // 消息投递回调接口，收到消息时触发
import org.wang.rabbitmqlab.common.ConnectionUtil; // 连接工具：集中管理 Broker 连接参数

import java.io.IOException;
import java.nio.charset.StandardCharsets;         // 字符编码，用于消息体转换
import java.util.concurrent.TimeoutException;

/**
 * Demo04 - 路由（Direct）消费者（多绑定版本）
 * 职责：创建临时队列绑定到 direct exchange，同时接收多种 routing key 的消息
 * 特点：一个 queue 可以绑定多个 binding key，接收多种类型的消息
 */
public class ReceiveLogsDirect2 {

    static void main() throws IOException, TimeoutException {

        System.out.println("ReceiveLogsDirect");

        // 1. 建立连接和信道（不用 try-with-resources，basicConsume 需要保持连接）
        //    连接参数集中在 ConnectionUtil，改 Broker 只改一处
        Connection connection = ConnectionUtil.createConnection();
        Channel channel = connection.createChannel();

        // 3. 声明 exchange（和 EmitLogDirect 一样，确保 exchange 存在）
        //    类型为 direct，与生产者保持一致
        channel.exchangeDeclare(EmitLogDirect.EXCHANGE_NAME, "direct");

        // 4. 声明一个临时队列（由 Broker 自动生成唯一名称）
        String queue = channel.queueDeclare().getQueue();

        // 5. 把同一个队列绑定到多个 binding key
        //    含义：这个消费者同时接收 "error"、"warning"、"info" 三种消息
        //    如果 EmitLogDirect 发送 routing key="debug"，这个消费者收不到
        channel.queueBind(queue, EmitLogDirect.EXCHANGE_NAME, "error");    // 绑定 error 级别
        channel.queueBind(queue, EmitLogDirect.EXCHANGE_NAME, "warning");  // 绑定 warning 级别
        channel.queueBind(queue, EmitLogDirect.EXCHANGE_NAME, "info");     // 绑定 info 级别

        // 6. 打印等待提示
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        // 7. 定义消息投递回调，当 Broker 推送消息时自动执行
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // 从 delivery 中取出消息体，转为字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);

            // 打印接收到的消息，包含 routing key（从 envelope 中获取）
            // 可以看到消息是 error、warning 还是 info
            System.out.println(" [x] Received '" +
                    delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };

        // 8. 开始消费消息（长订阅模式）
        //    参数：队列名, autoAck=true（自动确认）, 消息回调, 取消回调
        channel.basicConsume(queue, true, deliverCallback, consumerTag -> {});
    }
}
