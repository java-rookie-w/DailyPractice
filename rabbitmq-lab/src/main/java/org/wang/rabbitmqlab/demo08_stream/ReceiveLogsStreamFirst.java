package org.wang.rabbitmqlab.demo08_stream;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道,所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;     // 用于创建连接的工厂类
import com.rabbitmq.client.DeliverCallback;       // 消息投递回调接口,收到消息时触发

import java.io.IOException;
import java.nio.charset.StandardCharsets;         // 字符编码,用于消息体转换
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.wang.rabbitmqlab.demo08_stream.EmitLogStream.STREAM_NAME;

/**
 * Demo08 - Stream 流式队列 消费者 1(从第一条消息开始消费)
 *
 * 职责:连接到 stream 队列,从第一条可用消息开始消费
 *
 * ============================================================
 *  Stream 消费的核心机制:offset 控制
 * ============================================================
 *  经典队列:新消费者加入,从队列头部开始消费(消费即删)
 *  Stream:  消费者通过 x-stream-offset 指定从哪个位置开始读
 *
 *  x-stream-offset 可选值:
 *    "first"    → 从第一条可用消息开始(全量回放)
 *    "last"     → 从最后一条消息开始(只消费新消息)
 *    "next"     → 从下一条消息开始(跳过已有,只消费新消息)
 *    数字 N     → 从第 N 条消息开始(精确偏移)
 *    Date 对象  → 从该时间戳之后的消息开始
 *
 *  Stream 消费的注意事项:
 *    1. 必须设置 QoS prefetch(basicQos)
 *    2. 必须手动 ack(basicAck)
 *    3. 不支持全局 QoS(global=false)
 *
 * ============================================================
 *  本消费者的行为:
 * ============================================================
 *  x-stream-offset = "first"
 *  → 从第一条消息开始消费,全量回放所有历史数据
 *
 *  适用场景:日志归档、全量数据同步、故障恢复
 *
 * @author wang
 * @date 2026-08-04
 */
public class ReceiveLogsStreamFirst {

    static void main() throws IOException, TimeoutException {

        System.out.println("ReceiveLogsStreamFirst(x-stream-offset = first)");
        System.out.println(" [i] 将从第一条消息开始消费\n");

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

        // 3. 确保 stream 队列存在(和生产者声明一致)
        //    如果生产者已运行过,队列已存在,这里只是确认
        //    注意:第二次声明必须和第一次的参数完全一致,否则会报错
        //    这里声明与生产者一致: x-queue-type / x-max-length-bytes / x-max-age
        Map<String, Object> streamArgs = new HashMap<>();
        streamArgs.put("x-queue-type", "stream");
        streamArgs.put("x-max-length-bytes", 20_000_000_000L);
        streamArgs.put("x-max-age", "7D");
        channel.queueDeclare(STREAM_NAME, true, false, false, streamArgs);

        // ============================================================
        // 4. 设置 QoS prefetch(Stream 消费必须设置,否则报错)
        // ============================================================
        // QoS = 100 表示消费者一次最多缓存 100 条消息
        // 注意:Stream 不支持全局 QoS,所以必须传 false
        channel.basicQos(100);

        // ============================================================
        // 5. 开始消费,从第一条消息开始
        // ============================================================
        // x-stream-offset = "first" → 从第一条可用消息开始消费
        Map<String, Object> consumerArgs = Collections.singletonMap("x-stream-offset", "first");

        // 6. 打印等待提示
        System.out.println(" [*] Waiting for messages from the beginning. To exit press CTRL+C");

        // ============================================================
        // 7. 定义消息投递回调,当 Broker 推送消息时自动执行
        // ============================================================
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // 从 delivery 中取出消息体,转为字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);

            // 获取消息在 stream 中的偏移量(从 envelope 中获取 deliveryTag)
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