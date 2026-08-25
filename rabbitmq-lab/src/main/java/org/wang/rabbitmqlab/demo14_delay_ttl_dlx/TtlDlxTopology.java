package org.wang.rabbitmqlab.demo14_delay_ttl_dlx;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.BuiltinExchangeType;     // 内置交换机类型枚举（DIRECT/FANOUT/TOPIC...）
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import org.wang.rabbitmqlab.common.ConnectionUtil;       // 用于创建连接的工厂类

import java.util.HashMap;
import java.util.Map;

/**
 * Demo14 - 延迟订单取消（方案二：TTL + DLX 多级延迟队列，不依赖插件）
 *
 * ============================================================
 *  业务场景：延迟订单取消
 *  ============================================================
 *  用户下单后给一段支付时间（如 30 分钟），超时未支付 → 自动取消订单。
 *  本 demo 用纯原生 AMQP（TTL + DLX）实现，不装任何插件，兼容性最好。
 *
 * ============================================================
 *  生产方案选型：为什么不用「单队列 + 消息级 TTL + DLX」
 *  ============================================================
 *  最直觉的延迟方案：消息带 expiration，过期后进死信队列被消费。但有【队头阻塞】坑
 *  （demo12_ttl 已验证）——Broker 只在消息到【队头】才查 TTL，
 *  短 TTL 消息排在长 TTL 消息后面会延迟过期，订单超时精度无法保证。
 *
 *  解决队头阻塞有两种生产级方案：
 *
 *  A) 插件方案（demo13_delay_plugin）：每条消息独立延迟，无队头阻塞，代码简单
 *     —— 缺点是要装插件，Mnesia 单点风险
 *
 *  B) 多级 TTL+DLX 队列（本 demo）：用几个【固定 TTL】的延迟队列串联拼出任意延迟
 *     —— 每个延迟队列用【队列级 TTL】（统一过期），同一队列里所有消息 TTL 相同，
 *        没有队头阻塞；消息串联经过多个延迟队列，每经过一个消耗固定时长，最后落业务队列
 *
 * ============================================================
 *  核心拓扑（多级延迟队列，3 个延迟档位 + 1 个业务队列）：
 *  ============================================================
 *  思路：延迟档位 10s / 1s（生产上一般是 30min / 1min / 1s 等，这里用短时长便于演示）
 *  Producer 按目标延迟把消息拆成若干档位，串联投递：
 *
 *  举例：目标延迟 15s → 10s 队列(消耗10s) → 1s 队列 ×5(消耗5s) → 业务队列
 *
 *    Producer
 *       │  按 TTL 档位贪心拆分，决定先发往哪个延迟队列
 *       ▼
 *    order.cancel.relay.10s.queue  (x-message-ttl=10000, x-dead-letter-exchange=order.cancel.relay.dlx)
 *       │  过期(10s) → 死信
 *       ▼
 *    order.cancel.relay.dlx (direct)
 *       │  按 routing key 路由：还要继续延迟就进下一个延迟队列，延迟完了进业务队列
 *       ▼
 *    order.cancel.relay.1s.queue   (x-message-ttl=1000,  x-dead-letter-exchange=order.cancel.relay.dlx)
 *       │  过期(1s) → 死信 → 再回 DLX → 进下一个 1s 队列 或 业务队列
 *       ▼
 *    ... 串联 N 次 ...
 *       ▼
 *    order.cancel.relay.execute.queue  ← Consumer 消费 = 订单已超时 → 查状态关单
 *
 *  关键：每个延迟队列都是【队列级 TTL】，队列里所有消息 TTL 相同 → 无队头阻塞。
 *       串联次数由目标延迟 / 档位决定，类似「找零钱」——用大档位尽量凑，剩余用小档位。
 *
 * ============================================================
 *  关键认知（面试点 / 生产落地权衡）：
 *  ============================================================
 *  - 纯原生 AMQP，不依赖插件，任何 RabbitMQ 都能跑
 *  - 每个延迟队列用队列级 TTL，避免消息级 TTL 的队头阻塞（对比 demo12）
 *  - 串联经过的队列数 = 目标延迟的拆分段数，延迟越长 / 档位越细，串联次数越多
 *    —— 每段都会有一次 Broker 内部"过期→死信→入队"的流转，有额外开销
 *  - 档位设计权衡：
 *      档位多（如 1h/30min/10min/1min/1s）→ 任意延迟串联次数少，但队列多
 *      档位少（如 10s/1s）→ 队列少，但长延迟串联次数多，开销大
 *  - 选型结论（生产）：
 *      有插件用 demo13 插件方案（简单可靠）
 *      没插件 / 强兼容性要求 / 延迟档位固定（如只做整点任务）→ 用本方案
 *      延迟时长完全任意且量大 → 考虑 Redis ZSet 扫表或时间轮，MQ 不是最佳
 *
 *  业务落地三件套（本 demo 用 println 模拟，真实写法见 Consumer 注释）：
 *    1) 消费时查订单状态：UNPAID 才关单，已支付忽略（幂等）
 *    2) 关单用条件更新：UPDATE order SET status='CANCEL' WHERE id=? AND status='UNPAID'
 *    3) MQ 不可靠兜底：定时任务扫「UNPAID 且超时」的订单关单
 *
 * @author wang
 * @date 2026-08-18
 */
public class TtlDlxTopology {

    // ============================ 死信中转层 ============================
    // 所有延迟队列过期后都进这个死信交换机，按 routing key 决定下一步去向
    static final String DLX_EXCHANGE = "order.cancel.relay.dlx";

    // ============================ 延迟队列档位 ============================
    // 档位1：10 秒延迟队列
    static final String DELAY_10S_QUEUE  = "order.cancel.relay.10s.queue";
    static final long   DELAY_10S_TTL     = 10_000L;
    static final String ROUTING_10S       = "order.cancel.relay.10s";      // Producer → 10s 队列
    static final String DLX_FROM_10S      = "order.cancel.relay.from.10s"; // 10s 队列过期 → DLX（带剩余延迟信息）

    // 档位2：1 秒延迟队列（用来凑任意余数）
    static final String DELAY_1S_QUEUE   = "order.cancel.relay.1s.queue";
    static final long   DELAY_1S_TTL      = 1_000L;
    static final String ROUTING_1S        = "order.cancel.relay.1s";       // Producer / 10s 队列 → 1s 队列
    static final String DLX_FROM_1S       = "order.cancel.relay.from.1s";  // 1s 队列过期 → DLX

    // ============================ 业务层 ============================
    static final String BIZ_QUEUE         = "order.cancel.relay.execute.queue";   // 最终业务队列，消费者从这里消费
    static final String ROUTING_BIZ       = "order.cancel.relay.execute";         // 延迟用完 → 业务队列

    /**
     * 创建并返回一个 RabbitMQ 连接（委托给通用工具类）
     */
    static Connection createConnection() throws Exception {
        return ConnectionUtil.createConnection();
    }

    /**
     * 声明整个多级延迟拓扑：DLX 中转 + 2 个延迟队列 + 1 个业务队列
     *
     * 声明顺序：先 DLX，再延迟队列（延迟队列引用 DLX），最后业务队列
     */
    static void declareTopology(Channel ch) throws Exception {

        // ============================================================
        // 1) 死信中转交换机：所有延迟队列过期后都进这里，按 routing key 决定下一步
        // ============================================================
        ch.exchangeDeclare(DLX_EXCHANGE, BuiltinExchangeType.DIRECT, true);

        // ============================================================
        // 2) 业务队列：延迟用完后落到这里，消费者消费
        //    routing key = ROUTING_BIZ(=biz) → 最后一段延迟队列过期时带这个 key
        // ============================================================
        ch.queueDeclare(BIZ_QUEUE, true, false, false, null);
        ch.queueBind(BIZ_QUEUE, DLX_EXCHANGE, ROUTING_BIZ);

        // ============================================================
        // 3) 延迟档位 1：10s 队列（队列级 TTL，过期进 DLX）
        //    DLX_ROUTING_KEY 用 DLX_FROM_10S，DLX 收到后按"剩余延迟"决定下一步
        // ============================================================
        Map<String, Object> args10s = new HashMap<>();
        args10s.put("x-message-ttl", DELAY_10S_TTL);
        args10s.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args10s.put("x-dead-letter-routing-key", DLX_FROM_10S);
        ch.queueDeclare(DELAY_10S_QUEUE, true, false, false, args10s);
        ch.queueBind(DELAY_10S_QUEUE, DLX_EXCHANGE, ROUTING_10S);

        // ============================================================
        // 4) 延迟档位 2：1s 队列（队列级 TTL，过期进 DLX）
        //    DLX_ROUTING_KEY 用 DLX_FROM_1S，DLX 收到后按"剩余延迟"决定下一步
        // ============================================================
        Map<String, Object> args1s = new HashMap<>();
        args1s.put("x-message-ttl", DELAY_1S_TTL);
        args1s.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args1s.put("x-dead-letter-routing-key", DLX_FROM_1S);
        ch.queueDeclare(DELAY_1S_QUEUE, true, false, false, args1s);
        ch.queueBind(DELAY_1S_QUEUE, DLX_EXCHANGE, ROUTING_1S);
    }

    public static void main(String[] args) throws Exception {
        try (Connection conn = createConnection();
             Channel ch = conn.createChannel()) {
            declareTopology(ch);
            System.out.println("[拓扑] 多级 TTL+DLX 延迟拓扑就绪：");
            System.out.println("  DLX 中转: " + DLX_EXCHANGE);
            System.out.println("  延迟档位: " + DELAY_10S_QUEUE + "(TTL=" + DELAY_10S_TTL + "ms) + "
                    + DELAY_1S_QUEUE + "(TTL=" + DELAY_1S_TTL + "ms)");
            System.out.println("  业务队列: " + BIZ_QUEUE + " (延迟用完落这里)");
            System.out.println("  Producer 按目标延迟拆分档位串联投递，Consumer 从业务队列消费关单");
        }
    }
}
