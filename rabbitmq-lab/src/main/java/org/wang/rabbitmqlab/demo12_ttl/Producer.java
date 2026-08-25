package org.wang.rabbitmqlab.demo12_ttl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MessageProperties;
import org.wang.rabbitmqlab.common.ConnectionUtil;

import java.nio.charset.StandardCharsets;

/**
 * 生产端：分别演示“队列级 TTL”和“消息级 TTL”，并复现消息级 TTL 的坑。
 *
 * 关键对比（无消费者，纯看过期进死信的时间线）：
 *   - 队列级队列发 3 条无 expiration 消息 → Broker 队列定时器在 5s 统一把 3 条全部死信（精确）。
 *   - 消息级队列先发 B(expiration=8000) 在队头，再发 A(expiration=2000) 在队尾：
 *       A 的 TTL 明明只有 2s，但因为 B 在它前面，Broker 只在消息到“队头”才查 TTL，
 *       所以 A 不会在 2s 死，要等 B 在 8s 过期离开后 A 才轮到（≈8s 死）。→ 不精确 / 被阻塞。
 */
public class Producer {

    public static void main(String[] args) throws Exception {
        try (Connection connection = ConnectionUtil.createConnection();
             Channel channel = connection.createChannel()) {

            // ① 队列级 TTL 队列：发 3 条无 expiration 的消息（用默认交换机直接路由到队列名）
            for (int i = 1; i <= 3; i++) {
                String body = "队列级消息-" + i;
                channel.basicPublish("", TtlTopology.QLEVEL_QUEUE, false,
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        body.getBytes(StandardCharsets.UTF_8));
                System.out.println("[x] 发到队列级TTL队列: " + body);
            }

            // ② 消息级 TTL 队列：先发 B(8000ms, 队头)，再发 A(2000ms, 队尾)
            AMQP.BasicProperties pB = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2).expiration("8000").messageId("B-8s").build();
            channel.basicPublish("", TtlTopology.MLEVEL_QUEUE, false, pB,
                    "消息级B-expiration=8000(队头)".getBytes(StandardCharsets.UTF_8));
            System.out.println("[x] 发到消息级TTL队列: B expiration=8000 (队头)");

            AMQP.BasicProperties pA = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2).expiration("2000").messageId("A-2s").build();
            channel.basicPublish("", TtlTopology.MLEVEL_QUEUE, false, pA,
                    "消息级A-expiration=2000(队尾,被B阻塞)".getBytes(StandardCharsets.UTF_8));
            System.out.println("[x] 发到消息级TTL队列: A expiration=2000 (队尾，应被B阻塞)");

            System.out.println("[x] 发送完成。观察死信队列时间线（运行 Consumer 看时间戳）：");
            System.out.println("    t≈5s  → 队列级 3 条全部死信（队列级TTL精确）");
            System.out.println("    t≈8s  → 消息级 B(8s,队头) 死信");
            System.out.println("    t≈8s  → 消息级 A(2s,队尾) 紧接B之后死信（未在2s死，证明被阻塞/不精确）");
            Thread.sleep(500);
        }
    }
}
