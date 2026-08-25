package org.wang.rabbitmqlab.demo09_deadletter;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import com.rabbitmq.client.DefaultConsumer;         // 消费者默认实现（可重写回调）
import com.rabbitmq.client.Envelope;                // 消息信封（含 deliveryTag、routing key 等）

import java.nio.charset.StandardCharsets;           // 字符编码
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Demo09 - 死信队列消费者
 *
 * 职责：从【死信队列】消费，打印每条死信的【原因】（看 x-death 头）
 *
 * ============================================================
 *  消息流向（站在 Consumer 视角）：
 *  ============================================================
 *  Producer → 业务队列(TTL / MAXLEN) → [过期 / 超长] → 死信交换机 → 死信队列 → 【本 Consumer】
 *
 *  Consumer 只从【死信队列】消费，不从业务队列消费
 *  业务队列纯粹用来「等触发死信」，没有消费者
 *
 * ============================================================
 *  关键点：用 x-death 头区分死信原因
 *  ============================================================
 *  死信消息会在 header 里带 x-death 数组，每条记录：
 *    - reason: expired（TTL 过期）/ maxlen（队列超长）/ rejected（消费拒绝）
 *    - queue : 变成死信前所在的原始队列名
 *    - time  : 变成死信的时间
 *  消费端据此区分处理（生产上常用于告警路由 / 补偿策略分流）
 *
 *  三种 reason 对照（本 demo 演示 expired 和 maxlen 两种；rejected 见 demo11）：
 *    expired → 业务队列消息 TTL 到期
 *    maxlen  → 业务队列 x-max-length 超长，队头被挤掉
 *    rejected→ 消费端 basicNack/reject 且 requeue=false
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

            // 3. 设置【预取计数】= 1，公平分发
            ch.basicQos(1);

            // 4. 注册消费者到【死信队列】
            //    参数3 autoAck=false：手动确认
            System.out.println(" [*] 等待消费死信队列 " + DLXTopology.DLQ_QUEUE + "（按 Ctrl+C 退出）");
            ch.basicConsume(DLXTopology.DLQ_QUEUE, false, new DefaultConsumer(ch) {

                @Override
                public void handleDelivery(String consumerTag,
                                           Envelope envelope,
                                           com.rabbitmq.client.AMQP.BasicProperties properties,
                                           byte[] body) {
                    String msg = new String(body, StandardCharsets.UTF_8);
                    long deliveryTag = envelope.getDeliveryTag();

                    try {
                        // 5. 从 x-death 头解析死信原因
                        //    x-death 是一个 List<Map>，每条记录一次死信经历（消息可能多次死信）
                        String reason = "?";
                        String origQueue = "?";
                        List<Map<String, Object>> xDeaths =
                                (List<Map<String, Object>>) properties.getHeaders().get("x-death");
                        if (xDeaths != null && !xDeaths.isEmpty()) {
                            Map<String, Object> latest = xDeaths.get(0); // 取最近一次死信记录
                            reason = String.valueOf(latest.get("reason"));
                            origQueue = String.valueOf(latest.get("queue"));
                        }

                        System.out.println(" [" + LocalTime.now() + "] [死信] " + msg
                                + "  reason=" + reason + "  from=" + origQueue);

                        // 6. 手动 Ack
                        ch.basicAck(deliveryTag, false);

                    } catch (Exception e) {
                        System.err.println(" [!] 处理失败：" + e.getMessage());
                        try {
                            ch.basicNack(deliveryTag, false, false);
                        } catch (Exception ex) {
                            System.err.println(" [!] Nack 失败：" + ex.getMessage());
                        }
                    }
                }
            });

            // 7. 主线程阻塞，让 Consumer 持续运行
            Thread.sleep(30_000L);
        }
    }
}
