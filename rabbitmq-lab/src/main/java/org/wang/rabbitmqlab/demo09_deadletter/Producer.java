package org.wang.rabbitmqlab.demo09_deadletter;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import com.rabbitmq.client.MessageProperties;       // 消息属性常量（PERSISTENT_TEXT_PLAIN 等）

import java.nio.charset.StandardCharsets;           // 字符编码

/**
 * Demo09 - 死信队列生产者（延迟关单发消息端）
 *
 * 职责：模拟「用户下单」动作 —— 把订单消息发到【延迟队列】
 *
 * ============================================================
 *  消息流向：
 *  ============================================================
 *  Producer → DELAY_EXCHANGE(topic) → DELAY_QUEUE(带 TTL=10s)
 *                                        │
 *                                        │ (10 秒后过期)
 *                                        ▼
 *                               DLX_EXCHANGE(direct) → DLX_QUEUE → Consumer
 *
 * ============================================================
 *  关键点：
 *  ============================================================
 *  1. Producer 只管发消息到延迟交换机，完全不关心后续死信流程
 *  2. 消息用 persistent（delivery_mode=2），配合 durable Queue 才能真正防丢
 *  3. 发送时【不需要】设单条 expiration，TTL 由队列级 x-message-ttl 统一控制
 *     —— 队列级 TTL 更准，消息级 TTL 在队列堆积时不准（只在队头检查）
 *
 * @author wang
 * @date 2026-08-12
 */
public class Producer {

    public static void main(String[] args) throws Exception {
        // 1. 建立连接和信道（try-with-resources 自动关闭）
        try (Connection conn = DLXTopology.createConnection();
             Channel ch = conn.createChannel()) {

            // 2. 声明拓扑（如果还没建过，这里会幂等创建；已存在则复用）
            //    Producer 自己声明拓扑是为了「自给自足」，避免依赖运行顺序
            DLXTopology.declareTopology(ch);

            // ============================================================
            // 3. 模拟下 3 笔订单：用不同 orderId 模拟不同支付情况
            // ============================================================
            String[] orderIds = {
                    "ORDER-001",   // 这笔不支付 → 10 秒后被关单
                    "ORDER-002",   // 这笔不支付 → 10 秒后被关单
                    "ORDER-003"    // 这笔不支付 → 10 秒后被关单
                    // （演示场景：3 笔都不支付，全部应该进死信队列被关单）
                    // 真实场景下支付回调会更新订单状态，Consumer 检查到已支付就忽略
            };

            for (String orderId : orderIds) {
                // 消息体：直接用 orderId，真实场景会是 JSON（含订单金额、商品等）
                String message = orderId;

                // 4. 发送到延迟交换机，routing key = order.delay
                //    MessageProperties.PERSISTENT_TEXT_PLAIN = persistent + text/plain
                //    persistent（delivery_mode=2）让消息落盘，配合 durable Queue 防 Broker 重启丢消息
                ch.basicPublish(
                        DLXTopology.DELAY_EXCHANGE,            // 目标 exchange
                        DLXTopology.DELAY_ROUTING_KEY,         // routing key = order.delay
                        MessageProperties.PERSISTENT_TEXT_PLAIN, // 持久化 + 文本类型
                        message.getBytes(StandardCharsets.UTF_8) // 消息体
                );

                System.out.println(" [x] 下单成功，等待支付：'" + message + "'（10 秒后未支付将自动关单）");
            }

            // 5. 保持进程不退出，等消息过期进入死信队列后由 Consumer 处理
            //    这里用 sleep 仅为演示，真实应用是常驻服务
            System.out.println(" [*] Producer 已发完，等 12 秒让消息过期进死信队列...");
            Thread.sleep(12_000L);
        }
    }
}
