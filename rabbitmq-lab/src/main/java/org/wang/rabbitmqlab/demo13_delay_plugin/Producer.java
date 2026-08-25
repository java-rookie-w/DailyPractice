package org.wang.rabbitmqlab.demo13_delay_plugin;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import org.wang.rabbitmqlab.common.ConnectionUtil;

import java.nio.charset.StandardCharsets;           // 字符编码
import java.time.LocalTime;

/**
 * Demo13 - 延迟订单生产者（方案一：插件）
 *
 * 职责：模拟「用户下单」—— 把订单消息发到延迟交换机，每条带【独立延迟时长】
 *
 * ============================================================
 *  消息流向：
 *  ============================================================
 *  Producer → order.cancel.schedule.exchange(x-delayed-message)
 *               │  插件按 x-delay header 延迟，到点才投递
 *               ▼
 *             order.cancel.schedule.queue → Consumer（延迟到点消费 → 查状态关单）
 *
 * ============================================================
 *  关键点（生产落地）：
 *  ============================================================
 *  1. 每条消息独立延迟：通过 header 的 x-delay（毫秒）指定
 *     —— 插件方案的核心优势，订单超时时间按下单时刻算，每条不同
 *  2. 业务 routing key 走 DELAY_ROUTING，延迟由交换机插件处理，不经过死信
 *  3. 消息体：演示用纯 orderId，真实场景是 JSON（含下单时间、金额等）
 *  4. 真实下单接口收到请求 → 生成订单（DB 状态 UNPAID）→ 发延迟消息
 *     30 分钟（生产常用）后插件投递，消费者关单；本 demo 用 5s/10s/15s 便于观察
 *
 * @author wang
 * @date 2026-08-18
 */
public class Producer {

    public static void main(String[] args) throws Exception {
        try (Connection conn = ConnectionUtil.createConnection();
             Channel ch = conn.createChannel()) {

            // 1. 声明拓扑（幂等）
            DelayPluginTopology.declareTopology(ch);

            // 2. 模拟下 3 笔订单，每笔【独立延迟时长】（体现真实场景：每笔下单时刻不同）
            //    生产上延迟一般是固定 30 分钟，这里用不同时长验证"每条独立、无队头阻塞"
            String[] orders = {
                    "ORDER-001",   // 延迟 5 秒
                    "ORDER-002",   // 延迟 10 秒
                    "ORDER-003"    // 延迟 15 秒
            };
            long[] delays = {5_000L, 10_000L, 15_000L};

            for (int i = 0; i < orders.length; i++) {
                String orderId = orders[i];
                long delayMs = delays[i];

                // 3. 消息属性：x-delay 头指定延迟毫秒数（插件识别这个 header）
                //    deliveryMode=2 持久化，配合 durable Queue 防 Broker 重启丢消息
                //    messageId 供消费端做幂等
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .deliveryMode(2)
                        .messageId(orderId)
                        .headers(java.util.Map.of("x-delay", delayMs))
                        .build();

                ch.basicPublish(
                        DelayPluginTopology.DELAY_EXCHANGE,    // 延迟交换机
                        DelayPluginTopology.DELAY_ROUTING,      // routing key
                        props,
                        orderId.getBytes(StandardCharsets.UTF_8)
                );

                System.out.println(" [" + LocalTime.now() + "] [x] 下单成功 " + orderId
                        + "（" + (delayMs / 1000) + "秒后未支付将关单）");
            }

            System.out.println(" [*] Producer 发完，等延迟到点后 Consumer 收到关单");
            Thread.sleep(20_000L); // 留够时间让最后一条 15s 的延迟消息被消费
        }
    }
}
