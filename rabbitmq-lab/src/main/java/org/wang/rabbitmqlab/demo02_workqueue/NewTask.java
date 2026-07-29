package org.wang.rabbitmqlab.demo02_workqueue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class NewTask {
    private final static String QUEUE_NAME = "hello-worker";
    public static void main(String[] args) throws IOException, TimeoutException {

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.6.132");
        factory.setPort(5672);
        factory.setUsername("admin");
        factory.setPassword("passw0rd");
        factory.setVirtualHost("/mirror");
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(QUEUE_NAME, true, false, false, Map.of("x-queue-type", "quorum"));
//            String message = String.join("1", args);
//            String message = "Hello, World-》four message";
//            String message = "ACK1...............";
            for (int i = 0; i < 7; i++) {
                String message = "Message.......... " + (i + 1);
                channel.basicPublish("", QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("utf-8"));
                System.out.println(" [x] Sent '" + message + "'");
            }
//            channel.basicPublish("", QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes("utf-8"));
//            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}
