package org.wang.rabbitmqlab.demo08_stream;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道,所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;     // 用于创建连接的工厂类
import com.rabbitmq.client.DeliverCallback;       // 消息投递回调接口,收到消息时触发

import java.io.IOException;
import java.nio.charset.StandardCharsets;         // 字符编码,用于消息体转换
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.wang.rabbitmqlab.demo08_stream.EmitLogStream.STREAM_NAME;

/**
 * Demo08 - Stream 流式队列 消费者 2(从最新消息开始消费)
 *
 * 职责:连接到 stream 队列,从最后一条消息开始消费,只消费新消息
 *
 * ============================================================
 *  x-stream-offset = "last" 含义:
 * ============================================================
 *  从最后一条消息(最新写入的消息)开始,之后的新消息才会被消费
 *  已有的消息不会重新消费
 *
 *  类比:就像你打开一个直播流,不看回放,只从你加入的那一刻开始看
 *
 * ============================================================
 *  x-stream-offset = "next" 与 "last" 的区别:
 * ============================================================
 *  "last" → 从最后一条消息开始(能消费到最后一条消息)
 *  "next" → 跳过所有已有消息,只消费之后发送的新消息
 *
 *  本消费者使用 "next",更符合"只消费新消息"的直觉
 *
 * ============================================================
 *  适用场景:实时监控、告警、新消息推送
 *
 * @author wang
 * @date 2026-08-04
 */
public class ReceiveLogsStreamNext {

    static void main() throws IOException, TimeoutException {

        System.out.println("ReceiveLogsStreamNext(x-stream-offset = next)");
        System.out.println(" [i] 将跳过所有已存在消息,只消费新消息\n");

        // 1. 创建连接工厂,配置 Broker 的连接信息
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.6.132");          // Broker 的 IP 地址
        factory.setPort(5672);                     // Broker 的 AMQP 端口
        factory.setUsername("admin");              // 登录用户名
        factory.setPassword("passw0rd");           // 登录密码
        factory.setVirtualHost("/mirror");         // 虚拟主机

        // 2. 建立连接和信道(不用 try-with-resources,basicConsume 需要保持连接)
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // 3. 确保 stream 队列存在
        channel.queueDeclare(STREAM_NAME, true, false, false,
                Collections.singletonMap("x-queue-type", "stream"));

        // ============================================================
        // 4. 设置 QoS prefetch(Stream 消费必须设置)
        // ============================================================
        channel.basicQos(100);

        // ============================================================
        // 5. 开始消费,从下一条消息开始
        // ============================================================
        // x-stream-offset = "next" → 跳过所有已有消息,只消费新消息
        Map<String, Object> consumerArgs = Collections.singletonMap("x-stream-offset", "next");

        // 6. 打印等待提示
        System.out.println(" [*] Waiting for new messages only. To exit press CTRL+C");

        // ============================================================
        // 7. 定义消息投递回调,当 Broker 推送消息时自动执行
        // ============================================================
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // 从 delivery 中取出消息体,转为字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);

            // 获取消息在 stream 中的偏移量
            long offset = delivery.getEnvelope().getDeliveryTag();

            // 打印接收到的消息及其偏移量
            System.out.println(" [x] Received [offset=" + offset + "] '" + message + "'");

            // 8. 手动 ack(Stream 消费必须手动 ack)
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };

        // ============================================================
        // 9. 开始消费消息(长订阅模式)
        // ============================================================
        // 参数:队列名, autoAck=false(必须手动 ack), 消费者参数(offset 控制), 消息回调, 取消回调
        channel.basicConsume(STREAM_NAME, false, consumerArgs, deliverCallback, consumerTag -> { });
    }
}