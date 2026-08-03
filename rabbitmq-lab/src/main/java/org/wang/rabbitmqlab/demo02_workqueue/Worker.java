package org.wang.rabbitmqlab.demo02_workqueue;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;     // 用于创建连接的工厂类
import com.rabbitmq.client.DeliverCallback;       // 消息投递回调接口，收到消息时触发

import java.io.IOException;
import java.util.Map;                             // 用于传递 queue 的额外参数
import java.util.concurrent.TimeoutException;

/**
 * Demo02 - 工作队列（Work Queue）消费者
 * 职责：从队列接收消息并处理，模拟任务执行
 * 特点：手动 ACK + 公平分发（basicQos），保证消息可靠处理
 */
public class Worker {
    // 队列名称，必须和 NewTask 中一致
    private final static String QUEUE_NAME = "hello-worker";

    public static void main(String[] args) throws IOException, TimeoutException {

        // 1. 创建连接工厂，配置 Broker 的连接信息
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.6.132");          // Broker 的 IP 地址
        factory.setPort(5672);                     // Broker 的 AMQP 端口
        factory.setUsername("admin");              // 登录用户名
        factory.setPassword("passw0rd");           // 登录密码
        factory.setVirtualHost("/mirror");         // 虚拟主机

        // 2. 建立连接和信道（不用 try-with-resources，因为 basicConsume 是长订阅，需要保持连接）
        final Connection connection = factory.newConnection();
        final Channel channel = connection.createChannel();

        // 3. 声明队列（和 NewTask 一样，确保队列存在）
        //    durable=true：持久化队列，Broker 重启后队列及其中的持久化消息不丢
        //    arguments：quorum 队列类型（复制型、注重数据安全与一致性，官方要求必须 durable）
        channel.queueDeclare(QUEUE_NAME, true, false, false, Map.of("x-queue-type", "quorum"));

        // 4. 设置服务质量（QoS），控制预取数量
        //    prefetchCount=1：每个 Worker 同时最多只处理 1 条消息
        //    作用：公平分发，防止快的 Worker 空闲、慢的 Worker 积压
        //    如果不设置，Broker 会一次性把所有消息推给当前空闲的 Worker
        channel.basicQos(1);

        // 5. 定义消息投递回调，当 Broker 推送消息时自动执行
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // 从 delivery 中取出消息体，转为字符串
            String message = new String(delivery.getBody(), "UTF-8");

            // 打印接收到的消息
            System.out.println(" [x] Received '" + message + "'");

            try {
                // 6. 模拟处理任务（根据消息中的 "." 数量休眠）
                doWork(message);
            } finally {
                // 7. 处理完成后打印日志
                System.out.println(" [x] Done");

                // 8. 手动发送 ACK 确认，告诉 Broker "这条消息我处理完了，可以删除了"
                //    参数：deliveryTag（消息序号，标识哪条消息）, multiple=false（只确认这一条）
                //    注意：如果处理失败不调用 basicAck，消息会重新投递给其他 Worker
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };

        // 9. 打印等待提示
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        // 10. 开始消费消息（长订阅模式）
        //     参数：队列名, autoAck=false（手动确认）, 消息回调, 取消回调
        //     autoAck=false：必须手动调用 basicAck，否则 Broker 认为消息未处理
        //     调用后程序不会退出，会一直等待 Broker 推送消息
        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> { });
    }

    /**
     * 模拟任务处理：消息中每有一个 "." 就休眠 1 秒
     * 例如 "Message.......... 1" 有 10 个 "."，会休眠 10 秒
     */
    private static void doWork(String task) {
        // 遍历消息中的每个字符
        for (char ch : task.toCharArray()) {
            // 遇到 "." 就休眠
            if (ch == '.') {
                try {
                    Thread.sleep(1000);            // 休眠 1 秒，模拟耗时任务
                } catch (InterruptedException _ignored) {
                    // 如果被中断，恢复中断标志（不吞掉异常）
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
