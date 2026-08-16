package org.wang.rabbitmqlab.demo03_fanout;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.DeliverCallback;       // 消息投递回调接口，收到消息时触发
import org.wang.rabbitmqlab.common.ConnectionUtil; // 连接工具：集中管理 Broker 连接参数

import java.io.IOException;
import java.nio.charset.StandardCharsets;         // 字符编码，用于消息体转换
import java.util.concurrent.TimeoutException;

/**
 * Demo03 - 发布/订阅（Fanout）消费者
 * 职责：创建临时队列绑定到 fanout exchange，接收广播消息
 * 特点：每个消费者有自己的临时队列，都能收到同一条消息（广播效果）
 */
public class ReceiverLogs {

    static void main() throws IOException, TimeoutException {

        System.out.println("Receiver");

        // 1. 建立连接和信道（不用 try-with-resources，basicConsume 需要保持连接）
        //    连接参数集中在 ConnectionUtil，改 Broker 只改一处
        Connection connection = ConnectionUtil.createConnection();
        Channel channel = connection.createChannel();

        // 3. 声明 exchange（和 EmitLog 一样，确保 exchange 存在）
        //    类型为 fanout，与生产者保持一致
        channel.exchangeDeclare(EmitLog.EXCHANGE_NAME, "fanout");

        // 4. 声明一个临时队列（由 Broker 自动生成唯一名称）
        //    每个消费者启动时都会创建自己的临时队列
        //    这样多个消费者都能收到同一条消息（广播效果）
        String queue = channel.queueDeclare().getQueue();

        // 5. 把临时队列绑定到 exchange，routing key 为空（fanout 不关心 routing key）
        channel.queueBind(queue, EmitLog.EXCHANGE_NAME, "");

        // 6. 打印等待提示
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        // 7. 定义消息投递回调，当 Broker 推送消息时自动执行
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // 从 delivery 中取出消息体，转为字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);

            // 打印接收到的消息
            System.out.println(" [x] Received '" + message + "'");
        };

        // 8. 开始消费消息（长订阅模式）
        //    参数：队列名, autoAck=true（自动确认）, 消息回调, 取消回调
        //    autoAck=true：消息一送达就自动确认，Broker 立刻删除消息
        //    注意：如果消费者处理失败，消息会丢失（因为没有手动 ACK 重试机制）
        channel.basicConsume(queue, true, deliverCallback, consumerTag -> {});
    }
}
