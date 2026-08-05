package org.wang.rabbitmqlab.demo08_stream;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;                  // 信道,所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;                // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;         // 用于创建连接的工厂类

import java.io.IOException;
import java.nio.charset.StandardCharsets;             // 字符编码,用于消息体转换
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Demo08 - Stream 流式队列 生产者
 *
 * 职责:向 stream 类型的队列发送消息(传感器数据流)
 *
 * ============================================================
 *  Stream 队列特点:
 * ============================================================
 *  Stream 是 RabbitMQ 3.9+ 引入的新队列类型,基于 append-only 日志模型
 *  与经典队列的关键区别:
 *    - 经典队列:消费即删除,消息被消费后从队列移除
 *    - Stream:  消息不删除,按保留策略(retention)留存,可重复消费
 *
 *  Stream 队列声明方式:
 *    通过 queueDeclare 的 arguments 参数指定 x-queue-type = "stream"
 *    它本质是一个持久化队列,消息持续追加到日志尾部
 *
 *  适用场景:
 *    - 日志采集、事件流、IoT 传感器数据
 *    - 事件溯源(event sourcing)
 *    - 需要可重复消费 / 回溯消费的数据管道
 *
 * ============================================================
 *  运行说明:
 * ============================================================
 *  本生产者向 stream 队列发送 N 条传感器消息
 *  消费者通过 x-stream-offset 可以从任意位置开始消费
 *
 * @author wang
 * @date 2026-08-04
 */
public class EmitLogStream {

    // stream 队列名称,消费者需要用同一个名称来绑定
    static final String STREAM_NAME = "sensor_stream";

    static void main() throws IOException, TimeoutException {

        // 1. 创建连接工厂,配置 Broker 的连接信息
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.6.132");          // Broker 的 IP 地址
        factory.setPort(5672);                     // Broker 的 AMQP 端口
        factory.setUsername("admin");              // 登录用户名
        factory.setPassword("passw0rd");           // 登录密码
        factory.setVirtualHost("/mirror");         // 虚拟主机

        // 2. 建立连接和信道(try-with-resources 自动关闭)
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            // ============================================================
            // 3. 声明 stream 队列
            // ============================================================
            // 关键参数:
            //   x-queue-type = "stream"  → 声明为 stream 队列(区别于经典队列)
            //   x-max-length-bytes       → 可选,最大保留字节数(20GB)
            //   x-max-age                → 可选,数据保留时长(如 "7D" 表示 7 天)
            //
            // 注意:stream 队列必须为 durable=true(持久化)
            //      队列名由我们指定(不是临时队列),因为 stream 需要保持数据
            Map<String, Object> streamArgs = new HashMap<>();
            streamArgs.put("x-queue-type", "stream");
            streamArgs.put("x-max-length-bytes", 20_000_000_000L);   // 最多保留 20GB
            streamArgs.put("x-max-age", "7D");                        // 数据保留 7 天

            channel.queueDeclare(STREAM_NAME, true, false, false, streamArgs);

            System.out.println(" [i] 已声明 stream 队列: " + STREAM_NAME);
            System.out.println(" [i] 准备发送传感器数据...\n");

            // ============================================================
            // 4. 循环发送传感器数据(模拟设备上报)
            // ============================================================
            for (int i = 1; i <= 10; i++) {
                String message = "sensor_01 温度=" + (20 + i) + "°C, 湿度=" + (50 + i) + "% (第 " + i + " 条)";

                // 5. 发布消息到 stream 队列
                //    参数:exchange 名(空字符串=默认交换机), routing key(队列名), 消息属性, 消息体
                //    注意:往 stream 队列发消息,直接指定队列名作为 routing key,走默认交换机
                channel.basicPublish("", STREAM_NAME, null, message.getBytes(StandardCharsets.UTF_8));

                System.out.println(" [x] Sent '" + message + "'");
            }

            System.out.println("\n========== 发送完成,共 10 条 ==========");
            System.out.println(" [i] 提示:Stream 消息不会消费即删,可被重复消费");
        }
        // try-with-resources 自动关闭 channel 和 connection
    }
}