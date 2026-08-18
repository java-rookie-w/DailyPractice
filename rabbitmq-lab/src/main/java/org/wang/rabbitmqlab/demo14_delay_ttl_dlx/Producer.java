package org.wang.rabbitmqlab.demo14_delay_ttl_dlx;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import com.rabbitmq.client.DefaultConsumer;         // 消费者默认实现（可重写回调）
import com.rabbitmq.client.Envelope;                // 消息信封（含 deliveryTag、routing key 等）

import java.nio.charset.StandardCharsets;           // 字符编码
import java.time.LocalTime;

/**
 * Demo14 - 延迟订单生产者（方案二：TTL + DLX 多级延迟队列）
 *
 * 职责：模拟「用户下单」—— 把订单按目标延迟【拆档位】串联投递到延迟队列
 *
 * ============================================================
 *  消息流向（以目标延迟 15s 为例，档位 10s/1s）：
 *  ============================================================
 *  Producer
 *     │  目标 15s = 10s ×1 + 1s ×5；带 header「remaining」记录剩余延迟
 *     ▼  发往 10s 队列
 *  delay.10s.queue (TTL=10s, 过期→DLX)
 *     ▼  DLX 中转：header remaining=5s 还要继续延迟 → 转 1s 队列
 *  delay.1s.queue  (TTL=1s, 过期→DLX)
 *     ▼  DLX 中转：remaining 减到 0 → 进业务队列
 *  biz.queue → Consumer 关单
 *
 * ============================================================
 *  拆分逻辑（贪心，类似找零钱）：
 *  ============================================================
 *  目标延迟 / 10s 大档 → 用 10s 队列串联几次；余数用 1s 队列串联几次。
 *  每经过一个延迟队列，header 里「remaining」减掉该档位 TTL，
 *  DLX 中转时按 remaining 决定下一步：
 *    remaining > 0 → 进下一个延迟队列（按档位选 10s 或 1s）
 *    remaining = 0 → 进业务队列（路由 key = biz）
 *
 *  这里用 DLX 中转的【DLX 转发处理器】实现：DLX 收到死信消息时，
 *  由本类注册的临时消费者读出，按 remaining 重新投递到下一个队列。
 *  生产上也可用 Spring AMQP 的 DeadLetterListener 统一处理。
 *
 * ============================================================
 *  关键点（生产落地）：
 *  ============================================================
 *  1. 串联段数 = 目标延迟拆分后的档位数，延迟越长段数越多（开销 vs 精度权衡）
 *  2. 每个延迟队列用队列级 TTL，无队头阻塞（对比 demo12 消息级 TTL 的坑）
 *  3. remaining header 由 DLX 中转消费者维护，跨多段串联累减
 *  4. 真实下单接口收到请求 → 生成订单（UNPAID）→ 按超时时间拆档位发延迟消息
 *     30 分钟（生产常用）用 30min+1min 档位只需 1-2 段，本 demo 5s/15s 便于观察
 *
 * @author wang
 * @date 2026-08-18
 */
public class Producer {

    // 档位：大档 10s，小档 1s（便于演示；生产上一般 30min + 1min + 1s 等）
    static final long BIG_TTL  = TtlDlxTopology.DELAY_10S_TTL;
    static final long SMALL_TTL = TtlDlxTopology.DELAY_1S_TTL;

    public static void main(String[] args) throws Exception {
        try (Connection conn = TtlDlxTopology.createConnection();
             Channel ch = conn.createChannel()) {

            // 1. 声明拓扑（幂等）
            TtlDlxTopology.declareTopology(ch);

            // 2. 启动 DLX 中转处理器：拦截从延迟队列死信出来的消息，按 remaining 转投下一站
            startDlxRelay(ch);

            // 3. 模拟下 3 笔订单，每笔【独立目标延迟】（验证多级串联）
            String[] orders = {
                    "ORDER-001",   // 延迟 5s  → 1s ×5（不用 10s 档）
                    "ORDER-002",   // 延迟 10s → 10s ×1（用大档）
                    "ORDER-003"    // 延迟 15s → 10s ×1 + 1s ×5（大档 + 小档）
            };
            long[] targetDelays = {5_000L, 10_000L, 15_000L};

            for (int i = 0; i < orders.length; i++) {
                String orderId = orders[i];
                long remaining = targetDelays[i];

                // 4. 按目标延迟决定【首站】投递到哪个延迟队列
                //    贪心：能用大档（10s）先发大档，否则用小档（1s）
                String firstRouting = remaining >= BIG_TTL
                        ? TtlDlxTopology.ROUTING_10S
                        : TtlDlxTopology.ROUTING_1S;

                // 5. 消息属性：header 里带 remaining 记录剩余延迟，messageId 供幂等
                //    deliveryMode=2 持久化，配合 durable Queue 防 Broker 重启丢消息
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .deliveryMode(2)
                        .messageId(orderId)
                        .headers(java.util.Map.of("remaining", remaining))
                        .build();

                ch.basicPublish(
                        TtlDlxTopology.DLX_EXCHANGE,    // 都从 DLX 进，按 routing key 路由到首站延迟队列
                        firstRouting,
                        props,
                        orderId.getBytes(StandardCharsets.UTF_8)
                );

                System.out.println(" [" + LocalTime.now() + "] [x] 下单成功 " + orderId
                        + "（目标延迟 " + (remaining / 1000) + "s，首站 " + firstRouting + "）");
            }

            System.out.println(" [*] Producer 发完，等串联延迟到点后业务队列收到关单");
            Thread.sleep(20_000L); // 留够时间让最后一条 15s 的延迟消息走完串联
        }
    }

    /**
     * DLX 中转处理器：消费延迟队列死信出来的消息，按 remaining 决定下一步
     *
     * 这一段是【多级延迟】的核心机制：把"任意延迟"拆成"多个固定 TTL 队列串联"。
     * 生产上也可改成 Spring AMQP 的 @RabbitListener + DeadLetterListener 实现。
     */
    private static void startDlxRelay(Channel ch) throws Exception {
        // 监听 DLX 上「从延迟队列死信出来」的消息：
        //   routing key = DLX_FROM_10S（10s 队列过期） / DLX_FROM_1S（1s 队列过期）
        // 用一个临时匿名队列绑定这两个 key
        String relayQueue = ch.queueDeclare().getQueue();
        ch.queueBind(relayQueue, TtlDlxTopology.DLX_EXCHANGE, TtlDlxTopology.DLX_FROM_10S);
        ch.queueBind(relayQueue, TtlDlxTopology.DLX_EXCHANGE, TtlDlxTopology.DLX_FROM_1S);

        ch.basicConsume(relayQueue, false, new DefaultConsumer(ch) {
            @Override
            public void handleDelivery(String consumerTag,
                                       Envelope envelope,
                                       AMQP.BasicProperties properties,
                                       byte[] body) {
                long deliveryTag = envelope.getDeliveryTag();
                try {
                    String orderId = new String(body, StandardCharsets.UTF_8);

                    // 读出上一段消耗后剩余的延迟时长
                    long remaining = ((Number) properties.getHeaders().get("remaining")).longValue();

                    // 减去本段刚消耗的 TTL（按来源 routing key 判断）
                    String fromRouting = envelope.getRoutingKey();
                    long consumed = fromRouting.equals(TtlDlxTopology.DLX_FROM_10S) ? BIG_TTL : SMALL_TTL;
                    long next = remaining - consumed;

                    if (next <= 0) {
                        // 延迟用完 → 进业务队列（routing key = biz）
                        ch.basicPublish(
                                TtlDlxTopology.DLX_EXCHANGE,
                                TtlDlxTopology.ROUTING_BIZ,
                                false,
                                properties,
                                body
                        );
                        System.out.println(" [" + LocalTime.now() + "] [relay] " + orderId
                                + " 延迟用完 → 业务队列");
                    } else {
                        // 还有剩余 → 按档位选下一站延迟队列，更新 remaining
                        String nextRouting = next >= BIG_TTL
                                ? TtlDlxTopology.ROUTING_10S
                                : TtlDlxTopology.ROUTING_1S;
                        AMQP.BasicProperties nextProps = new AMQP.BasicProperties.Builder()
                                .deliveryMode(2)
                                .messageId(properties.getMessageId())
                                .headers(java.util.Map.of("remaining", next))
                                .build();
                        ch.basicPublish(
                                TtlDlxTopology.DLX_EXCHANGE,
                                nextRouting,
                                false,
                                nextProps,
                                body
                        );
                        System.out.println(" [" + LocalTime.now() + "] [relay] " + orderId
                                + " 剩余 " + (next / 1000) + "s → 下一站 " + nextRouting);
                    }

                    ch.basicAck(deliveryTag, false);
                } catch (Exception e) {
                    System.err.println(" [relay] 处理失败：" + e.getMessage());
                    try { ch.basicNack(deliveryTag, false, false); } catch (Exception ignored) {}
                }
            }
        });

        System.out.println(" [*] DLX 中转处理器已启动（串联多级延迟队列）");
    }
}
