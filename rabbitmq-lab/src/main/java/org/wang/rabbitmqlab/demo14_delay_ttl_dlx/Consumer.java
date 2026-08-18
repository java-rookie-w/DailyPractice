package org.wang.rabbitmqlab.demo14_delay_ttl_dlx;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import com.rabbitmq.client.DefaultConsumer;         // 消费者默认实现（可重写回调）
import com.rabbitmq.client.Envelope;                // 消息信封（含 deliveryTag、routing key 等）

import java.nio.charset.StandardCharsets;           // 字符编码
import java.time.LocalTime;

/**
 * Demo14 - 延迟订单消费者（方案二：TTL + DLX 多级延迟队列）
 *
 * 职责：从【业务队列】消费 = 订单已走完全部延迟段 → 查订单状态 → 未支付就关单
 *
 * ============================================================
 *  消息流向（站在 Consumer 视角）：
 *  ============================================================
 *  Producer → 延迟队列(10s) → [过期]DLX → 1s 队列 ×N → [过期]DLX → 业务队列 → 【本 Consumer】
 *  收到消息即代表「走完全部延迟段，距下单已过目标延迟时长」，该检查订单状态了
 *
 *  Consumer 只从【业务队列】消费，不接触延迟队列（延迟队列无消费者，纯等过期）
 *
 * ============================================================
 *  业务逻辑（延迟关单核心，生产落地三件套）：
 *  ============================================================
 *  1. 查订单状态：从业务队列收到消息 = 订单已超时
 *     - 仍【未支付】UNPAID → 执行关单（CANCEL），条件更新防并发
 *     - 已【支付】PAID     → 忽略（幂等），订单已被支付回调改掉
 *  2. 关单用条件更新（CAS 思想）：
 *       UPDATE order SET status='CANCEL' WHERE id=? AND status='UNPAID'
 *     受影响行数=1 → 关单成功；=0 → 状态已被改（已支付或已关单），忽略
 *     —— 防止「支付回调」和「关单任务」并发把已支付订单关掉
 *  3. MQ 不可靠兜底：多级串联涉及多次死信流转，任一段 Broker 故障都可能漏关
 *     → 定时任务扫「UNPAID 且下单超过 30 分钟」的订单关单，兜底防漏
 *
 *  本 demo 业务逻辑用 println 模拟（保持 lab 纯净），真实写法见注释
 *
 * ============================================================
 *  关键点（面试高频）：
 *  ============================================================
 *  1. 手动 Ack（autoAck=false）：业务处理完才确认，崩溃时消息重新入队不丢
 *  2. 幂等：延迟消息可能重复投递，关单动作必须幂等（条件更新天然幂等）
 *  3. 并发：支付回调与关单任务并发，用状态机条件更新保证不会「已支付被关单」
 *  4. 兜底：MQ 不可靠时定时任务扫表兜底（本方案串联段多，兜底尤为重要）
 *
 * @author wang
 * @date 2026-08-18
 */
public class Consumer {

    public static void main(String[] args) throws Exception {
        // 1. 建立连接和信道
        try (Connection conn = TtlDlxTopology.createConnection();
             Channel ch = conn.createChannel()) {

            // 2. 声明拓扑（幂等）
            TtlDlxTopology.declareTopology(ch);

            // 3. 预取 = 1，公平分发
            ch.basicQos(1);

            // 4. 注册消费者到【业务队列】（手动 ACK）
            System.out.println(" [*] 等待消费业务队列 " + TtlDlxTopology.BIZ_QUEUE + "（按 Ctrl+C 退出）");
            ch.basicConsume(TtlDlxTopology.BIZ_QUEUE, false, new DefaultConsumer(ch) {

                @Override
                public void handleDelivery(String consumerTag,
                                           Envelope envelope,
                                           com.rabbitmq.client.AMQP.BasicProperties properties,
                                           byte[] body) {
                    String orderId = new String(body, StandardCharsets.UTF_8);
                    long deliveryTag = envelope.getDeliveryTag();

                    try {
                        System.out.println(" [" + LocalTime.now() + "] [x] 收到延迟到点消息（订单已超时）：" + orderId);

                        // ============================================================
                        // 5. 业务处理：查订单状态并关单（模拟）
                        // ============================================================
                        // 真实场景：
                        //   Order order = orderMapper.selectById(orderId);
                        //   if (order.getStatus() == UNPAID) {
                        //       int rows = orderMapper.updateStatus(orderId, CANCEL, UNPAID); // 条件更新防并发
                        //       if (rows == 1) log.info("关单成功");
                        //       else log.warn("关单失败：状态已被改"); // 已被支付回调改掉
                        //   } else {
                        //       log.info("订单已支付/已关单，忽略"); // 幂等
                        //   }
                        System.out.println("     → 查询订单 " + orderId + " 状态：UNPAID");
                        System.out.println("     → 执行关单：UPDATE order SET status='CANCEL' WHERE id='"
                                + orderId + "' AND status='UNPAID'");
                        System.out.println("     → 关单成功（受影响行数=1）");

                        // 6. 手动 Ack
                        ch.basicAck(deliveryTag, false);

                    } catch (Exception e) {
                        // 7. 业务异常：拒绝消息，requeue=false 进死信（生产上单独消费做补偿/告警）
                        System.err.println(" [!] 处理失败：" + e.getMessage() + "，消息进死信");
                        try {
                            ch.basicNack(deliveryTag, false, false);
                        } catch (Exception ex) {
                            System.err.println(" [!] Nack 失败：" + ex.getMessage());
                        }
                    }
                }
            });

            // 8. 主线程阻塞，让 Consumer 持续运行
            Thread.sleep(30_000L);
        }
    }
}
