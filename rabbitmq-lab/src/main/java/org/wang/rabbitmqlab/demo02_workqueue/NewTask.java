package org.wang.rabbitmqlab.demo02_workqueue;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;              // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;            // 到 Broker 的 TCP 连接
import com.rabbitmq.client.MessageProperties;     // 预定义的消息属性（如持久化）
import org.wang.rabbitmqlab.common.ConnectionUtil; // 连接工具：集中管理 Broker 连接参数

import java.io.IOException;
import java.util.Map;                             // 用于传递 queue 的额外参数
import java.util.concurrent.TimeoutException;

/**
 * Demo02 - 工作队列（Work Queue）生产者
 * 职责：向队列发送消息，模拟任务分发
 * 特点：消息持久化 + quorum 队列，保证消息不丢失
 */
public class NewTask {
    // 队列名称，生产者和消费者必须使用同一个队列名
    private final static String QUEUE_NAME = "hello-worker";

    public static void main(String[] args) throws IOException, TimeoutException {

        // 1. 建立连接和信道（try-with-resources 自动关闭）
        //    连接参数集中在 ConnectionUtil，改 Broker 只改一处
        //    Connection 代表一个 TCP 连接，Channel 是连接内的虚拟连接（多路复用）
        try (Connection connection = ConnectionUtil.createConnection();
             Channel channel = connection.createChannel()) {

            // 3. 声明队列（如果不存在则创建，存在则不做任何事）
            //    参数：队列名, 持久化, 排他, 自动删除, 额外参数
            //    durable=true：队列声明持久化，Broker 重启后队列会恢复，
            //                  连同其中以持久化方式（delivery mode=2）发布的消息
            //    exclusive=false：不排他，允许多个连接访问
            //    autoDelete=false：最后一个消费者断开后不自动删除
            //    arguments：指定队列类型为 quorum
            //    官方文档：quorum 是"复制型、注重数据安全与一致性"的队列类型
            //    基于 Raft 共识算法，数据在集群多节点间复制，官方要求必须 durable
            channel.queueDeclare(QUEUE_NAME, true, false, false, Map.of("x-queue-type", "quorum"));

            // 4. 循环发送 7 条消息
            for (int i = 0; i < 7; i++) {
                // 构造消息内容
                String message = "Message.......... " + (i + 1);

                // 5. 发布消息到队列
                //    参数：exchange（空字符串=默认直连exchange）, routingKey=队列名, 消息属性, 消息体
                //    MessageProperties.PERSISTENT_TEXT_PLAIN：消息持久化属性，保证消息存磁盘
                //    注意：队列和消息都持久化，才能真正做到消息不丢失
                channel.basicPublish("", QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("utf-8"));

                // 打印发送日志
                System.out.println(" [x] Sent '" + message + "'");
            }
        }
        // try-with-resources 自动关闭 channel 和 connection
    }
}
