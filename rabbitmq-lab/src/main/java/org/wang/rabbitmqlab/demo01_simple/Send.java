package org.wang.rabbitmqlab.demo01_simple;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.wang.rabbitmqlab.common.ConnectionUtil;  // 连接工具：集中管理 Broker 连接参数

import java.util.Map;

public class Send {
    // 定义队列名称常量，必须与消费者一致才能发送到正确队列
    private static final String QUEUE_NAME = "hello";

    public static void main(String[] args) throws Exception {
        // try-with-resources：自动关闭 Connection 和 Channel
        // 连接参数集中在 ConnectionUtil，改 Broker 只改一处
        try (Connection connection = ConnectionUtil.createConnection();
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
