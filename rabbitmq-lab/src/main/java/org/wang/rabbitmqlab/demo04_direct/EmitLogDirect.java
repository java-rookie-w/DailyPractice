package org.wang.rabbitmqlab.demo04_direct;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import org.wang.rabbitmqlab.common.ConnectionUtil; // 连接工具：集中管理 Broker 连接参数

import java.io.IOException;
import java.nio.charset.StandardCharsets;         // 字符编码，用于消息体转换
import java.util.concurrent.TimeoutException;

/**
 * Demo04 - 路由（Direct）生产者
 * 职责：向 direct 类型的 exchange 发送多种 routing key 的消息
 * 特点：direct 根据 routing key 精确匹配，只把消息路由到 binding key 相同的 queue
 *
 * 本示例覆盖的 case：
 *   - 单一 routing key 发送
 *   - 多种 routing key 循环发送（error / warning / info）
 *   - 中文消息内容
 *   - 模拟真实业务场景的日志消息
 */
public class EmitLogDirect {

    // exchange 名称，消费者需要用同一个名称来绑定
    public static final String EXCHANGE_NAME = "direct_logs";

    static void main() throws IOException, TimeoutException {

        // 1. 建立连接和信道（try-with-resources 自动关闭）
        //    连接参数集中在 ConnectionUtil，改 Broker 只改一处
        try (Connection connection = ConnectionUtil.createConnection();
             Channel channel = connection.createChannel()) {

            // 3. 声明 exchange，类型为 direct
            //    direct：精确匹配模式，把消息路由到 binding key == routing key 的 queue
            channel.exchangeDeclare(EXCHANGE_NAME, "direct");

            // ============================================================
            // 测试数据：覆盖多种 case
            // ============================================================
            String[][] testData = {
                    // routing key,             消息内容
                    // ---- 单一类型多发几条 ----
                    {"error",    "[ERROR] 数据库连接失败，请检查配置"},
                    {"error",    "[ERROR] 订单 #10086 支付超时"},
                    {"error",    "[ERROR] 文件写入失败：磁盘空间不足"},

                    // ---- warning 类型 ----
                    {"warning",  "[WARN] 内存使用率超过 80%"},
                    {"warning",  "[WARN] 接口响应时间超过 2000ms"},
                    {"warning",  "[WARN] 检测到异地登录"},

                    // ---- info 类型 ----
                    {"info",     "[INFO] 用户 admin 登录成功"},
                    {"info",     "[INFO] 定时任务执行完成"},
                    {"info",     "[INFO] 数据库连接池初始化完成"},

                    // ---- 其他自定义 routing key ----
                    {"debug",    "[DEBUG] 进入方法 processOrder(), 参数: orderId=123"},
                    {"critical", "[CRITICAL] 系统核心模块异常，即将重启"},
            };

            System.out.println(" [i] 准备发送 " + testData.length + " 条消息\n");

            // ============================================================
            // 循环发送消息
            // ============================================================
            for (String[] data : testData) {
                String routingKey = data[0];
                String message = data[1];

                // 4. 发布消息到 exchange
                //    参数：exchange 名, routing key（决定消息路由到哪些 queue）, 消息属性, 消息体
                channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes(StandardCharsets.UTF_8));

                // 打印发送日志
                System.out.println(" [x] Sent '" + routingKey + "':'" + message + "'");
            }

            System.out.println("\n========== 发送完成，共 " + testData.length + " 条 ==========");
        }
        // try-with-resources 自动关闭 channel 和 connection
    }
}
