package org.wang.rabbitmqlab.demo07_headers;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.AMQP;                     // AMQP 协议相关类,用于构建消息属性
import com.rabbitmq.client.Channel;                  // 信道,所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;                // 到 Broker 的 TCP 连接
import org.wang.rabbitmqlab.common.ConnectionUtil;         // 用于创建连接的工厂类

import java.io.IOException;
import java.nio.charset.StandardCharsets;             // 字符编码,用于消息体转换
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Demo07 - Headers Exchange 生产者
 *
 * 职责:向 headers 类型的 exchange 发送带有不同 header 的消息
 *
 * ============================================================
 *  Headers Exchange 特点:
 * ============================================================
 *  Headers 不依赖 routing key,而是根据消息的 headers(键值对)来路由消息
 *  binding 时通过 x-match 参数指定匹配策略:
 *    - "all": 所有指定的 header 都匹配才能投递(AND 逻辑)
 *    - "any": 任意一个 header 匹配就投递(OR 逻辑)
 *
 * ============================================================
 *  Headers Exchange 与 Direct/Topic 对比:
 * ============================================================
 *  Direct:     精确匹配 routing key
 *  Topic:      通配符模式匹配 routing key
 *  Headers:   匹配消息的 headers 键值对(更灵活,但性能略低)
 *
 * ============================================================
 *  适用场景:
 * ============================================================
 *  - 路由条件复杂,无法用单一路由键表达
 *  - 需要根据多个维度(如:级别+来源+格式)组合过滤
 *  - 消息头携带元数据,消费者按元数据路由
 *
 * @author wang
 * @date 2026-08-04
 */
public class EmitLogHeader {

    // exchange 名称,消费者需要用同一个名称来绑定
    static final String EXCHANGE_NAME = "header_logs";

    static void main() throws IOException, TimeoutException {

        // 1. 建立连接:连接参数集中在 ConnectionUtil,改 Broker 只改一处
        // 2. 建立连接和信道(try-with-resources 自动关闭)
        try (Connection connection = ConnectionUtil.createConnection();
             Channel channel = connection.createChannel()) {

            // 3. 声明 exchange,类型为 headers
            channel.exchangeDeclare(EXCHANGE_NAME, "headers");

            // ============================================================
            // 测试数据:多种 header 组合覆盖不同匹配场景
            // ============================================================
            // 每条消息包含:消息内容 + 一组 headers
            // 消费者可以通过绑定不同的 header 组合来过滤消息
            System.out.println(" [i] 准备发送测试消息...\n");

            // ---- 场景 1:error 级别 + db 来源 ----
            Map<String, Object> headers1 = new HashMap<>();
            headers1.put("level", "error");
            headers1.put("source", "db");
            sendMessage(channel, "数据库连接失败:连接超时", headers1);

            // ---- 场景 2:error 级别 + auth 来源 ----
            Map<String, Object> headers2 = new HashMap<>();
            headers2.put("level", "error");
            headers2.put("source", "auth");
            sendMessage(channel, "用户认证失败:token 已过期", headers2);

            // ---- 场景 3:warning 级别 + db 来源 ----
            Map<String, Object> headers3 = new HashMap<>();
            headers3.put("level", "warning");
            headers3.put("source", "db");
            sendMessage(channel, "数据库连接池使用率超过 80%", headers3);

            // ---- 场景 4:info 级别 + api 来源 ----
            Map<String, Object> headers4 = new HashMap<>();
            headers4.put("level", "info");
            headers4.put("source", "api");
            sendMessage(channel, "用户登录成功:admin", headers4);

            // ---- 场景 5:只有 level=critical,无 source ----
            Map<String, Object> headers5 = new HashMap<>();
            headers5.put("level", "critical");
            sendMessage(channel, "系统核心模块异常:内存溢出", headers5);

            // ---- 场景 6:多 header 组合(level + source + format) ----
            Map<String, Object> headers6 = new HashMap<>();
            headers6.put("level", "error");
            headers6.put("source", "db");
            headers6.put("format", "json");
            sendMessage(channel, "{\"code\":500,\"msg\":\"DB timeout\"}", headers6);

            System.out.println("\n========== 发送完成,共 6 条 ==========");
        }
        // try-with-resources 自动关闭 channel 和 connection
    }

    /**
     * 发送带有指定 headers 的消息
     *
     * @param channel 信道
     * @param message 消息内容
     * @param headers 消息头键值对
     */
    private static void sendMessage(Channel channel, String message, Map<String, Object> headers)
            throws IOException {

        // 4. 构建消息属性,设置 headers
        //    Headers exchange 不关心 routing key,这里传空字符串
        //    注意:headers 要设置在 BasicProperties 中,而不是作为参数传给 basicPublish
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .headers(headers)
                .build();

        // 5. 发布消息到 exchange
        //    参数:exchange 名, routing key(headers 忽略此参数), 消息属性(含 headers), 消息体
        channel.basicPublish(EXCHANGE_NAME, "", props, message.getBytes(StandardCharsets.UTF_8));

        // 打印发送日志
        System.out.println(" [x] Sent '" + message + "'");
        System.out.println("     headers: " + headers);
        System.out.println();
    }
}