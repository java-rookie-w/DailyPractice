package org.wang.rabbitmqlab.demo01_simple;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.Map;

public class Send {
    // 定义队列名称常量，必须与消费者一致才能发送到正确队列
    private static final String QUEUE_NAME = "hello";

    public static void main(String[] args) throws Exception {
        // 创建连接工厂，用于配置和生成 Connection
        ConnectionFactory factory = new ConnectionFactory();
        // 指定 RabbitMQ 服务器地址
        factory.setHost("192.168.6.132");
        // 指定 AMQP 协议端口（默认 5672）
        factory.setPort(5672);
        // 设置认证用户名
        factory.setUsername("admin");
        // 设置认证密码
        factory.setPassword("passw0rd");
        // 设置虚拟主机，实现资源隔离（类似 namespace）
        factory.setVirtualHost("/mirror");

        // try-with-resources：自动关闭 Connection 和 Channel
        // 发送方发完消息就结束，适合自动释放资源
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            // 声明队列参数：指定为 quorum（仲裁）队列，要求 durable=true
            Map<String, Object> map = Map.of("x-queue-type", "quorum");
            // 声明队列：durable=true 队列持久化到磁盘；exclusive=false 允许其他连接访问；
            // autoDelete=false 最后一个消费者断开后不自动删除
            channel.queueDeclare(QUEUE_NAME, true, false, false, map);

            // 定义要发送的消息内容
            String message = "Hello World!";
            // 发布消息：exchange="" 使用默认直连交换机（按路由键=队列名路由）；
            // routingKey=QUEUE_NAME 指定目标队列；props=null 无额外属性；body=消息字节
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
            // 打印发送成功提示
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}
