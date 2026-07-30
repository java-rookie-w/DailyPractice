package org.wang.rabbitmqlab.demo05_topic;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;     // 用于创建连接的工厂类
import com.rabbitmq.client.DeliverCallback;       // 消息投递回调接口，收到消息时触发

import java.io.IOException;
import java.nio.charset.StandardCharsets;         // 字符编码，用于消息体转换
import java.util.concurrent.TimeoutException;

import static org.wang.rabbitmqlab.demo05_topic.EmitLogTopic.EXCHANGE_NAME;

/**
 * Demo05 - 路由（Topic）消费者 2（全量订阅模式 / 黑洞模式）
 *
 * 职责：创建临时队列绑定到 topic exchange，接收所有消息
 *
 * ============================================================
 *  本消费者订阅的 binding key：
 *  ============================================================
 *  "#"  → 匹配零个或多个单词，即匹配所有 routing key
 *
 *  无论生产者发送什么 routing key，本消费者都会收到
 *  典型用途：日志全量收集（如 ELK）、审计日志、消息归档
 *
 * ============================================================
 *  匹配结果预测（基于 EmitLogTopic 发送的 4 条消息）：
 *  ============================================================
 *  发送消息             是否收到
 *  "kern.critical"    ✅
 *  "kern.info"        ✅
 *  "auth.error"       ✅
 *  "auth.info"        ✅
 *
 *  → 本消费者收到全部 4 条消息
 *
 * ============================================================
 *  运行方式：
 * ============================================================
 *  可以同时启动多个 ReceiverLogTopic2 实例，RabbitMQ 会轮询分发消息（竞争消费）
 *  这是 MQ 的负载均衡特性：同一个队列的多个消费者自动分摊消息
 *
 * ============================================================
 *
 * @author wang
 * @date 2026-07-30
 */
public class ReceiverLogTopic2 {

    static void main() throws IOException, TimeoutException {

        System.out.println("ReceiverLogTopic2（全量订阅模式 #）");

        // 1. 创建连接工厂，配置 Broker 的连接信息
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.6.132");          // Broker 的 IP 地址
        factory.setPort(5672);                     // Broker 的 AMQP 端口
        factory.setUsername("admin");              // 登录用户名
        factory.setPassword("passw0rd");           // 登录密码
        factory.setVirtualHost("/mirror");         // 虚拟主机

        // 2. 建立连接和信道（不用 try-with-resources，basicConsume 需要保持连接）
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // 3. 声明 exchange（和 EmitLogTopic 一样，确保 exchange 存在）
        //    类型为 topic，与生产者保持一致
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

        // 4. 声明一个临时队列（由 Broker 自动生成唯一名称）
        String queueName = channel.queueDeclare().getQueue();

        // ============================================================
        // 5. 把临时队列绑定到 exchange，binding key 为 "#"
        // ============================================================
        // "#" → 匹配所有 routing key（零个或多个单词）
        // 含义：这个消费者接收所有消息，不做任何过滤
        String[] bindingKeys = new String[]{"#"};

        for (String bindingKey : bindingKeys) {
            channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);
        }

        // 6. 打印等待提示
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        // ============================================================
        // 7. 定义消息投递回调，当 Broker 推送消息时自动执行
        // ============================================================
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // 从 delivery 中取出消息体，转为字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);

            // 打印接收到的消息，包含 routing key（从 envelope 中获取）
            System.out.println(" [x] Received '" +
                    delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };

        // ============================================================
        // 8. 开始消费消息（长订阅模式）
        // ============================================================
        // 参数：队列名, autoAck=true（自动确认）, 消息回调, 取消回调
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });
    }
}
