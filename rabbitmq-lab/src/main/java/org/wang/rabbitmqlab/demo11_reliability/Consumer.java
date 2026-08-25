package org.wang.rabbitmqlab.demo11_reliability;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import org.wang.rabbitmqlab.common.ConnectionUtil;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消费端：如何保证“消息不丢 / 不重复处理”
 *
 * 面试里这段通常答三条防线：
 *
 *   A) 手动 ACK（autoAck=false）—— 防“处理到一半宕机白丢”
 *      - 业务处理成功才 basicAck；处理中宕机 → 消息重新入队，不会丢
 *      - 绝不能用 autoAck=true（拿到就算消费完，宕机即丢）
 *
 *   B) 失败进死信 —— 防“业务失败又 requeue 造成死循环”
 *      - 处理失败 basicNack(tag, false, false)：requeue=false → 消息按队列死信参数进死信队列
 *      - 死信队列单独消费做补偿 / 告警，原队列不被毒消息堵塞
 *
 *   C) 幂等 —— 防“重复投递产生重复副作用”
 *      - 用 messageId 去重（ConcurrentHashMap 仅演示；生产放 Redis/DB，SETNX 或唯一约束）
 *      - Broker 重投 / 网络重传时，重复消息只处理一次
 */
public class Consumer {

    // 已处理消息 id（内存模拟幂等；生产环境应放 Redis/DB）
    private static final ConcurrentHashMap<String, Boolean> PROCESSED = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        try (Connection connection = ConnectionUtil.createConnection();
             Channel channel = connection.createChannel()) {

            channel.basicQos(1); // 公平分发：消费端一次只取一条，处理完再取下一条

            // —— 业务队列消费者（手动 ACK）——
            DeliverCallback bizCallback = (consumerTag, delivery) -> {
                String msgId = delivery.getProperties().getMessageId();   // 幂等键（生产端设的）
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                long tag = delivery.getEnvelope().getDeliveryTag();
                try {
                    // ① 幂等：已处理过直接 ACK，避免重复副作用
                    if (PROCESSED.containsKey(msgId)) {
                        System.out.println("[幂等] 跳过重复消息 msgId=" + msgId);
                        channel.basicAck(tag, false);
                        return;
                    }
                    // ② 模拟业务失败 → 进死信（requeue=false 不回原队，防死循环）
                    if (body.contains("FAIL")) {
                        System.err.println("[业务失败] 进死信 msgId=" + msgId + " body=" + body);
                        channel.basicNack(tag, false, false);
                        return;
                    }
                    // ③ 正常处理
                    System.out.println("[处理] msgId=" + msgId + " body=" + body);
                    PROCESSED.put(msgId, Boolean.TRUE); // 先记幂等，再 ACK
                    channel.basicAck(tag, false);      // 处理成功才 ACK（关键）
                } catch (Exception e) {
                    // ④ 处理异常 → 进死信，等待补偿（不外抛，避免断连）
                    System.err.println("[异常] 进死信 msgId=" + msgId + " err=" + e.getMessage());
                    channel.basicNack(tag, false, false);
                }
            };
            channel.basicConsume(ReliabilityTopology.BIZ_QUEUE, false, bizCallback, consumerTag -> {});

            // —— 死信队列消费者（补偿 / 告警）——
            DeliverCallback dlqCallback = (consumerTag, delivery) -> {
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.err.println("[死信] 收到待补偿消息 body=" + body);
            };
            channel.basicConsume(ReliabilityTopology.DLQ_QUEUE, true, dlqCallback, consumerTag -> {});

            System.out.println("[*] 消费端已启动（手动ACK + 幂等 + 死信），按 Ctrl+C 退出");
            // 保持连接运行，等待消息；Ctrl+C 退出时 try-with-resources 自动关连接
            Thread.sleep(Long.MAX_VALUE);
        }
    }
}
