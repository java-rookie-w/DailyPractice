package org.wang.rabbitmqlab.demo09_deadletter;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import com.rabbitmq.client.DefaultConsumer;         // 消费者默认实现（可重写回调）
import com.rabbitmq.client.Envelope;                // 消息信封（含 deliveryTag、routing key 等）

import java.nio.charset.StandardCharsets;           // 字符编码

/**
 * Demo09 - 死信队列消费者（延迟关单执行端）
 *
 * 职责：从【死信队列】消费过期消息 → 检查订单状态 → 关单或忽略
 *
 * ============================================================
 *  消息流向（站在 Consumer 视角）：
 *  ============================================================
 *  Producer → 延迟队列(TTL=10s) → [过期] → 死信交换机 → 死信队列 → 【本 Consumer】
 *
 *  Consumer 只从【死信队列】消费，不从延迟队列消费
 *  延迟队列纯粹用来「等过期」，没有消费者
 *
 * ============================================================
 *  业务逻辑（延迟关单核心）：
 *  ============================================================
 *  1. 收到死信消息（说明消息已过期 = 距下单已过 10 秒）
 *  2. 检查订单当前状态：
 *     - 仍【未支付】 → 执行关单（CANCEL 状态），业务层用条件更新防并发
 *     - 已【支付】   → 忽略（幂等），消息已过期但订单已支付，不重复操作
 *  3. 手动 Ack 告诉 Broker 处理完成
 *
 * ============================================================
 *  关键点（面试高频）：
 *  ============================================================
 *  1. 手动 Ack（autoAck=false）：业务处理完才确认，崩溃时消息会重新入队
 *  2. 幂等性：死信消息可能重复投递，关单动作必须幂等
 *     —— 业务层用「UPDATE order SET status='CANCEL' WHERE id=? AND status='UNPAID'」
 *        受影响行数=0 说明已被支付或已关单，直接忽略
 *  3. 并发问题：支付回调与关单任务可能并发执行
 *     —— 用状态机条件更新（CAS 思想）保证不会「已支付的订单被关单」
 *  4. 补偿扫描：MQ 不可靠时（如 Broker 宕机），定时任务扫表兜底
 *     —— 找「UNPAID 且下单超过 30 分钟」的订单关单，防止漏关
 *
 * @author wang
 * @date 2026-08-12
 */
public class Consumer {

    public static void main(String[] args) throws Exception {
        // 1. 建立连接和信道
        try (Connection conn = DLXTopology.createConnection();
             Channel ch = conn.createChannel()) {

            // 2. 声明拓扑（幂等，已存在则复用）
            DLXTopology.declareTopology(ch);

            // 3. 设置【预取计数】= 1
            //    限制每个 Consumer 未确认消息数 ≤ 1，处理完一条才收下一条
            //    —— 公平分发，避免某个消费者积压；值太小影响吞吐，需按业务压测
            ch.basicQos(1);

            // 4. 注册消费者到【死信队列】
            //    参数3 autoAck=false：手动确认，业务处理完显式 Ack
            //    —— 自动 Ack 在 Consumer 崩溃时会丢消息，生产环境必须手动
            System.out.println(" [*] 等待消费死信队列 " + DLXTopology.DLX_QUEUE + "（按 Ctrl+C 退出）");
            ch.basicConsume(DLXTopology.DLX_QUEUE, false, new DefaultConsumer(ch) {

                /**
                 * 收到消息时的回调
                 * 消息从死信队列投递过来 = 原消息已 TTL 过期 = 距下单已过 10 秒
                 */
                @Override
                public void handleDelivery(String consumerTag,
                                           Envelope envelope,
                                           com.rabbitmq.client.AMQP.BasicProperties properties,
                                           byte[] body) {
                    String orderId = new String(body, StandardCharsets.UTF_8);
                    long deliveryTag = envelope.getDeliveryTag();

                    try {
                        System.out.println(" [x] 收到死信消息（订单已超时）：'" + orderId + "'");

                        // ============================================================
                        // 5. 业务处理：检查订单状态并关单（模拟）
                        // ============================================================
                        // 真实场景这里会查 DB：
                        //   Order order = orderMapper.selectById(orderId);
                        //   if (order.getStatus() == UNPAID) {
                        //       int rows = orderMapper.updateStatus(orderId, CANCEL, UNPAID); // 条件更新防并发
                        //       if (rows == 1) { log.info("关单成功"); }
                        //       else { log.warn("关单失败：状态已被改"); } // 已被支付回调改掉
                        //   } else {
                        //       log.info("订单已支付，忽略关单"); // 幂等
                        //   }
                        //
                        // 演示用：直接打印，假设都未支付
                        System.out.println("     → 查询订单 " + orderId + " 状态：UNPAID");
                        System.out.println("     → 执行关单：UPDATE order SET status='CANCEL' WHERE id='"
                                + orderId + "' AND status='UNPAID'");
                        System.out.println("     → 关单成功（受影响行数=1）");

                        // 6. 手动 Ack：业务处理成功，告诉 Broker 可以删消息了
                        //    参数2 multiple=false：只确认当前这条（不批量确认）
                        ch.basicAck(deliveryTag, false);

                    } catch (Exception e) {
                        // 7. 业务异常：拒绝消息，requeue=false 进死信队列（这里死信队列的死信）
                        //    注意：真实场景不能无限 requeue，否则形成热循环
                        //    一般是：限重试次数 → 超阈值进【死信队列的死信队列】或告警人工介入
                        System.err.println(" [!] 处理失败：" + e.getMessage() + "，消息进死信");
                        try {
                            ch.basicNack(deliveryTag, false, false); // multiple=false, requeue=false
                        } catch (Exception ex) {
                            System.err.println(" [!] Nack 失败：" + ex.getMessage());
                        }
                    }
                }
            });

            // 8. 主线程阻塞，让 Consumer 持续运行
            //    演示用：等 30 秒后自动退出
            Thread.sleep(30_000L);
        }
    }
}
