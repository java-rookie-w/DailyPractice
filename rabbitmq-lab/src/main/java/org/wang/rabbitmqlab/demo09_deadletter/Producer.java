package org.wang.rabbitmqlab.demo09_deadletter;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import com.rabbitmq.client.MessageProperties;       // 消息属性常量（PERSISTENT_TEXT_PLAIN 等）

import java.nio.charset.StandardCharsets;           // 字符编码

/**
 * Demo09 - 死信队列生产者
 *
 * 职责：分别往 TTL_QUEUE 和 MAXLEN_QUEUE 发消息，触发两种死信条件
 *
 * ============================================================
 *  消息流向：
 *  ============================================================
 *  发 TTL 队列：3 条消息，5 秒后【全部过期】→ 死信交换机 → 死信队列
 *  发超长队列：5 条消息，但队列 x-max-length=3 → 【队头 2 条】被挤成死信 → 死信队列
 *
 *  注意两个业务队列【本 demo 都不消费】，纯粹为了触发死信：
 *    - TTL 队列：等过期
 *    - 超长队列：等塞满后继续塞，把队头挤出去
 *
 *  生产上「消费失败进死信」是第三种触发，demo11_reliability 里演示，这里不重复。
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
            DLXTopology.declareTopology(ch);

            // ============================================================
            // 3. 往 TTL_QUEUE 发 3 条消息：5 秒后全部过期变死信
            // ============================================================
            // 队列级 TTL 由 Broker 统一定时器触发，与消息位置无关，过期时间精确
            for (int i = 1; i <= 3; i++) {
                String msg = "ttl消息-" + i;
                ch.basicPublish(
                        DLXTopology.DLX_EXCHANGE,             // 目标 exchange
                        DLXTopology.TTL_ROUTING,              // routing key = ttl → 进 TTL_QUEUE
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        msg.getBytes(StandardCharsets.UTF_8)
                );
                System.out.println(" [x] 发往TTL队列: " + msg + "（5秒后过期进死信）");
            }

            // ============================================================
            // 4. 往 MAXLEN_QUEUE 发 5 条消息：队列只放 3 条，后 2 条把队头挤成死信
            // ============================================================
            // x-max-length 行为：队列满后，新消息进来会把【队头】消息挤成死信
            // 所以发的顺序是 1,2,3,4,5 → 最终队列里留 3,4,5；1,2 被挤进死信队列
            for (int i = 1; i <= 5; i++) {
                String msg = "maxlen消息-" + i;
                ch.basicPublish(
                        DLXTopology.DLX_EXCHANGE,             // 目标 exchange
                        DLXTopology.MAXLEN_ROUTING,           // routing key = maxlen → 进 MAXLEN_QUEUE
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        msg.getBytes(StandardCharsets.UTF_8)
                );
                System.out.println(" [x] 发往超长队列: " + msg + (i > 3 ? "（挤掉队头进死信）" : ""));
            }

            System.out.println(" [*] 发送完成。运行 Consumer 观察死信原因：");
            System.out.println("    t≈5s  → TTL 队列 3 条全部死信（reason=expired）");
            System.out.println("    立即  → 超长队列队头 2 条死信（reason=maxlen）");
            Thread.sleep(1_000);
        }
    }
}
