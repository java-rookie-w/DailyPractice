package org.wang.rabbitmqlab.demo05_topic;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.DeliverCallback;       // 消息投递回调接口，收到消息时触发
import org.wang.rabbitmqlab.common.ConnectionUtil; // 连接工具：集中管理 Broker 连接参数

import java.io.IOException;
import java.nio.charset.StandardCharsets;         // 字符编码，用于消息体转换
import java.util.concurrent.TimeoutException;

import static org.wang.rabbitmqlab.demo05_topic.EmitLogTopic.EXCHANGE_NAME;

/**
 * Demo05 - 路由（Topic）消费者 1（过滤订阅模式）
 *
 * 职责：创建临时队列绑定到 topic exchange，只接收符合 binding key 模式的消息
 *
 * ============================================================
 *  本消费者订阅的 binding key 及含义：
 *  ============================================================
 *  "kern.*"       → 接收 kern 来源的所有级别（critical、info 等）
 *                   匹配 "kern.critical" ✅、"kern.info" ✅
 *                   不匹配 "auth.error" ❌
 *
 *  "*.critical"   → 接收所有来源的 critical 级别（kern、auth 等）
 *                   匹配 "kern.critical" ✅
 *                   不匹配 "kern.info" ❌（info ≠ critical）
 *
 *  "*.*.error"    → 接收三段式 routing key 且第三段是 error 的消息
 *                   不匹配 "auth.error" ❌（只有两段，需要三段）
 *                   匹配 "db.write.error" ✅（三段，第三段是 error）
 *
 *  "*.info"       → 接收所有来源的 info 级别
 *                   匹配 "kern.info" ✅、"auth.info" ✅
 *                   不匹配 "kern.critical" ❌
 *
 * ============================================================
 *  匹配结果预测（基于 EmitLogTopic 发送的 4 条消息）：
 *  ============================================================
 *  发送消息             是否收到    匹配的 binding key
 *  "kern.critical"    ✅          kern.*、*.critical
 *  "kern.info"        ✅          kern.*、*.info
 *  "auth.error"       ❌          无匹配（*.*.error 需要三段）
 *  "auth.info"        ✅          *.info
 *
 *  → 本消费者收到 3 条消息（kern.critical、kern.info、auth.info）
 *
 * ============================================================
 *
 * @author wang
 * @date 2026-07-30
 */
public class ReceiverLogTopic {

    static void main() throws IOException, TimeoutException {

        System.out.println("ReceiverLogTopic（过滤订阅模式）");

        // 1. 建立连接和信道（不用 try-with-resources，basicConsume 需要保持连接）
        //    连接参数集中在 ConnectionUtil，改 Broker 只改一处
        Connection connection = ConnectionUtil.createConnection();
        Channel channel = connection.createChannel();

        // 3. 声明 exchange（和 EmitLogTopic 一样，确保 exchange 存在）
        //    类型为 topic，与生产者保持一致
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

        // 4. 声明一个临时队列（由 Broker 自动生成唯一名称）
        //    返回的队列名是随机的，如 "amq.gen-xxx"
        String queueName = channel.queueDeclare().getQueue();

        // ============================================================
        // 5. 把临时队列绑定到 exchange，指定多个 binding key（通配符模式）
        // ============================================================
        // 含义：这个消费者只关心符合以下任一模式的消息
        String[] bindingKeys = new String[]{
                "kern.*",       // 绑定 kern 来源的所有级别
                "*.critical",   // 绑定所有来源的 critical 级别
                "*.*.error",    // 绑定三段式且第三段是 error 的消息
                "*.info"        // 绑定所有来源的 info 级别
        };

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
        // autoAck=true → 收到消息立即确认（本示例简化，生产环境建议用手动 ACK）
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });
    }
}
