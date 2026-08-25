package org.wang.rabbitmqlab.demo11_reliability;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.wang.rabbitmqlab.common.ConnectionUtil;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 生产端：如何保证“发出去的消息不丢”
 *
 * 面试里这段通常答两条防线：
 *
 *   A) Publisher Confirm（发布确认）—— 防“消息没到 Broker”
 *      - channel.confirmSelect() 开启异步确认
 *      - Broker 把消息落盘后回 ACK；若写盘失败/不可达回 NACK
 *      - 生产端收到 NACK 或超时未确认 → 本地重试（这里只打印，生产应加重试+本地落库）
 *      比事务（txSelect/txCommit，同步阻塞）性能好很多，是首选方案。
 *
 *   B) mandatory=true + ReturnListener —— 防“消息到 Broker 但路由不到队列”
 *      - mandatory=true：消息不可路由时 Broker 把消息“退回”生产端（触发 Return 回调）
 *      - 更彻底的是 Alternate Exchange（备用交换机）：把不可路由消息转存到备用交换机，绝不丢
 *      （本项目 demo 用 Return 演示；生产高可靠场景建议用 AE）
 *
 *   配合 Broker 端：消息用 deliveryMode=2（持久化）发送，见下方 AMQP.BasicProperties。
 */
public class Producer {

    public static void main(String[] args) throws Exception {
        try (Connection connection = ConnectionUtil.createConnection();
             Channel channel = connection.createChannel()) {

            // ① 开启发布确认（异步模式）
            channel.confirmSelect();

            // ② 发布确认回调：Broker 落盘 ACK / NACK
            channel.addConfirmListener(
                (deliveryTag, multiple) ->
                    System.out.println("[Confirm] ACK deliveryTag=" + deliveryTag + " multiple=" + multiple),
                (deliveryTag, multiple) ->
                    System.err.println("[Confirm] NACK deliveryTag=" + deliveryTag + " → 需重发（此处仅打印）")
            );

            // ③ Return 回调：mandatory=true 且消息不可路由时触发（消息被退回生产端）
            //   签名 handleReturn(replyCode, replyText, exchange, routingKey, properties, body)
            channel.addReturnListener((replyCode, replyText, exchange, routingKey, properties, body) -> {
                String b = new String(body, StandardCharsets.UTF_8);
                System.err.println("[Return] 消息不可路由被退回 replyCode=" + replyCode
                        + " routingKey=" + routingKey + " body=" + b);
            });

            // ④ 发送 3 条“正常 + 持久化”消息
            for (int i = 1; i <= 3; i++) {
                String msgId = UUID.randomUUID().toString();          // messageId 供消费端做幂等
                String body = "订单-" + i + " 创建消息";
                // deliveryMode(2) = 持久化（重启不丢内容）；messageId 给消费端去重用
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .deliveryMode(2)                 // 关键：持久化消息
                        .messageId(msgId)               // 关键：消费端幂等键
                        .contentType("text/plain")
                        .build();
                // 第三个参数 mandatory=true：不可路由就 Return
                channel.basicPublish(
                        ReliabilityTopology.BIZ_EXCHANGE,
                        ReliabilityTopology.BIZ_ROUTING,
                        true,
                        props,
                        body.getBytes(StandardCharsets.UTF_8)
                );
                System.out.println("[x] 已发送(持久化) msgId=" + msgId + " body=" + body);
            }

            // ⑤ 发一条“不可路由”消息，演示 Return 回调（路由键无任何队列绑定）
            AMQP.BasicProperties props2 = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2).messageId(UUID.randomUUID().toString()).contentType("text/plain").build();
            channel.basicPublish(
                    ReliabilityTopology.BIZ_EXCHANGE,
                    "no.such.route",          // 无队列绑定 → 触发 Return
                    true,
                    props2,
                    "这条会触发 Return".getBytes(StandardCharsets.UTF_8)
            );
            System.out.println("[x] 已发送一条不可路由消息（应触发 Return 回调）");

            // 6. 发一条模拟业务失败的消息，消费端会 NACK 进死信队列（演示用）
            AMQP.BasicProperties props3 = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2).messageId(UUID.randomUUID().toString()).contentType("text/plain").build();
            channel.basicPublish(
                    ReliabilityTopology.BIZ_EXCHANGE,
                    ReliabilityTopology.BIZ_ROUTING,
                    true,
                    props3,
                    "FAIL".getBytes(StandardCharsets.UTF_8)
            );
            System.out.println("[x] 已发送一条模拟消费端 NACK 的消息（应进死信队列）");

            // ⑥ 给异步回调一点时间打印（演示用；生产可走 waitForConfirmsOrDie 同步等）
            Thread.sleep(600);
            System.out.println("[x] 生产端发送完毕：Confirm + Return(mandatory) + 持久化 三段已就位");
        }
    }
}
