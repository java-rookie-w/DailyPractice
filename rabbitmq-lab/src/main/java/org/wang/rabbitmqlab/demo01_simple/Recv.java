package org.wang.rabbitmqlab.demo01_simple;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import org.wang.rabbitmqlab.common.ConnectionUtil;  // 连接工具：集中管理 Broker 连接参数

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Recv {
    // 定义队列名称常量，生产者和消费者通过相同名称绑定到同一个队列
    private static final String QUEUE_NAME = "hello";

     static void main(String[] args) throws Exception {
        // 建立 TCP 长连接（重量级资源，全局通常只维护一个）
        // 连接参数集中在 ConnectionUtil，改 Broker 只改一处
        Connection connection = ConnectionUtil.createConnection();
        // 在 Connection 上创建虚拟通道（轻量级，每个线程/业务独立使用）
        Channel channel = connection.createChannel();

        // 声明队列参数：指定为 quorum（仲裁）队列，RabbitMQ 3.8+ 支持，提供数据安全性
        Map<String, Object> map = Map.of("x-queue-type", "quorum");
        // 声明队列：durable=true 队列持久化到磁盘；exclusive=false 允许其他连接访问；
        // autoDelete=false 最后一个消费者断开后不自动删除；arguments 传入额外参数
        channel.queueDeclare(QUEUE_NAME, true, false, false, map);
        // 提示消费者已就绪，进入等待状态
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        // 定义消息到达时的回调逻辑（在 RabbitMQ 客户端内部线程执行）
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // 将消息体（byte[]）按 UTF-8 解码为字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            // 打印接收到的消息
            System.out.println(" [x] Received '" + message + "'");
        };

        // 开始消费队列：autoAck=true 自动确认（消息发出即认为消费成功，不等待消费者手动确认）；
        // deliverCallback 处理消息；cancelCallback 处理消费者被取消的情况
        channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> {
        });
    }
}
