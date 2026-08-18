package org.wang.rabbitmqlab.demo12_ttl;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import org.wang.rabbitmqlab.common.ConnectionUtil;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;

/**
 * 死信消费者：只消费 ttl.dlq，打印每条死信到达的本地时间，用来观察 TTL 过期时间线。
 *
 * 用法：先跑 TtlTopology 建拓扑，再跑 Producer 发消息，最后跑本类后台挂着，
 * 看控制台打印的时间戳是否符合预期（队列级 5s / 消息级 B≈8s、A≈8s 紧随）。
 */
public class Consumer {

    public static void main(String[] args) throws Exception {
        try (Connection connection = ConnectionUtil.createConnection();
             Channel channel = connection.createChannel()) {

            channel.basicQos(1);
            DeliverCallback cb = (consumerTag, delivery) -> {
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                long tag = delivery.getEnvelope().getDeliveryTag();
                // x-death 头里记录着过期原因；这里打印时间即可看出 TTL 时间线
                System.out.println("[" + LocalTime.now() + "] [死信] " + body);
                channel.basicAck(tag, false);
            };
            channel.basicConsume(TtlTopology.DLQ_QUEUE, false, cb, consumerTag -> {});

            System.out.println("[*] 死信消费者已启动，按 Ctrl+C 退出（观察 TTL 过期时间线）");
            Thread.sleep(Long.MAX_VALUE); // 保持运行等待死信
        }
    }
}
