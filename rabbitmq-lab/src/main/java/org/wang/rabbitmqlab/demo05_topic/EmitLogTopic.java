package org.wang.rabbitmqlab.demo05_topic;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;     // 用于创建连接的工厂类

import java.io.IOException;
import java.nio.charset.StandardCharsets;         // 字符编码，用于消息体转换
import java.util.concurrent.TimeoutException;

/**
 * Demo05 - 路由（Topic）生产者
 *
 * 职责：向 topic 类型的 exchange 发送多种 routing key 的消息
 *
 * ============================================================
 *  Topic Exchange 特点：
 *  ============================================================
 *  topic 根据 routing key 模式匹配，把消息路由到 binding key 匹配的 queue
 *  与 direct 的区别：direct 是精确匹配，topic 支持通配符模糊匹配
 *
 * ============================================================
 *  通配符规则：
 *  ============================================================
 *  *（星号）：匹配恰好一个单词（不能多也不能少）
 *  #（井号）：匹配零个或多个单词
 *
 *  示例分析（routing key = "kern.critical"）：
 *    "kern.*"         → ✅ 匹配（* = critical，恰好一个词）
 *    "*.critical"     → ✅ 匹配（* = kern，恰好一个词）
 *    "kern.#"         → ✅ 匹配（# = critical，一个词）
 *    "#.critical"     → ✅ 匹配（# = kern，一个词）
 *    "#"              → ✅ 匹配（万能匹配）
 *    "kern.critical"  → ✅ 匹配（精确匹配）
 *    "kern.error"     → ❌ 不匹配（error ≠ critical）
 *    "*.error"        → ❌ 不匹配（error ≠ critical）
 *
 *  示例分析（routing key = "auth.error"）：
 *    "*.error"        → ✅ 匹配（* = auth）
 *    "auth.*"         → ✅ 匹配（* = error）
 *    "kern.*"         → ❌ 不匹配（kern ≠ auth）
 *    "*.*.error"      → ❌ 不匹配（只有两段，需要三段）
 *
 * ============================================================
 *
 * @author wang
 * @date 2026-07-30
 */
public class EmitLogTopic {

    // exchange 名称，消费者需要用同一个名称来绑定
    static final String EXCHANGE_NAME = "topic_logs";

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

            // 3. 声明 exchange，类型为 topic
            //    topic：模式匹配，支持 * 和 # 通配符
            //    与 direct 的区别：direct 精确匹配，topic 支持通配符
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");

            // ============================================================
            // 测试数据：多种 routing key 覆盖不同匹配场景
            // ============================================================
            // routing key 格式：来源.级别
            // 来源：kern（内核）、auth（认证）
            // 级别：critical（严重）、info（信息）
            String[] routingKeys = new String[]{
                    "kern.critical",    // 内核严重错误
                    "kern.info",        // 内核信息
                    "auth.error",       // 认证错误
                    "auth.info"         // 认证信息
            };
            String[] messages = new String[]{
                    "Kernel critical error",            // 对应 kern.critical
                    "Kernel info message",              // 对应 kern.info
                    "Authentication error",             // 对应 auth.error
                    "Authentication info message"       // 对应 auth.info
            };

            // ============================================================
            // 循环发送消息
            // ============================================================
            for (int i = 0; i < routingKeys.length; i++) {
                String routingKey = routingKeys[i];
                String message = messages[i];

                // 4. 发布消息到 exchange
                //    参数：exchange 名, routing key（纯文本，不含通配符）, 消息属性, 消息体
                //    注意：生产者的 routing key 永远是纯文本，通配符只在消费者的 binding key 中生效
                channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes(StandardCharsets.UTF_8));

                // 打印发送日志
                System.out.println(" [x] Sent '" + routingKey + "':'" + message + "'");
            }
        }
        // try-with-resources 自动关闭 channel 和 connection
    }
}
