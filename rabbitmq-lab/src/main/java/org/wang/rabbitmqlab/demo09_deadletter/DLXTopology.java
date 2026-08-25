package org.wang.rabbitmqlab.demo09_deadletter;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.BuiltinExchangeType;     // 内置交换机类型枚举（DIRECT/FANOUT/TOPIC...）
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import org.wang.rabbitmqlab.common.ConnectionUtil;       // 用于创建连接的工厂类

import java.util.HashMap;                           // 哈希表（存放队列的额外参数 x-arguments）
import java.util.Map;                               // Map 接口

/**
 * Demo09 - 死信队列（Dead Letter Exchange / DLX）
 *
 * ============================================================
 *  本 demo 主题：死信队列本身，不是某个业务场景
 *  ============================================================
 *  聚焦「消息为什么会变成死信」——死信交换机 DLX 只是一个普通 Exchange，
 *  真正决定哪些消息变死信的是队列上配的 x-dead-letter-exchange 参数。
 *
 * ============================================================
 *  消息变成「死信」的三种触发条件（队列必须配 DLX 才会转发，否则直接丢弃）：
 *  ============================================================
 *  1. 消息被消费端 basicNack / basicReject 且 requeue=false  ← 本 demo 用 TTL_QUEUE 不演示这条
 *     （消费失败进死信在 demo11_reliability 里有完整演示，这里不重复）
 *  2. 消息 TTL 过期（队列级 x-message-ttl 到期）            ← TTL_QUEUE 演示这条
 *  3. 队列达到最大长度（x-max-length，超出时队头消息被挤掉） ← MAXLEN_QUEUE 演示这条
 *
 *  本 demo 把 2、3 两种触发放在同一套拓扑里对照演示：
 *    TTL_QUEUE  ：配 x-message-ttl，消息过期变死信
 *    MAXLEN_QUEUE：配 x-max-length，队列满后队头消息被挤成死信
 *
 * ============================================================
 *  核心拓扑（DLX 一个，业务队列两个，死信队列一个）：
 *  ============================================================
 *  Producer
 *     │  basicPublish → dlx.demo.exchange (direct, 普通)
 *     ▼
 *    ┌─────────────────────────────────────────────────────┐
 *    │  TTL_QUEUE      ← x-message-ttl=5000                │   过期 → 死信
 *    │  MAXLEN_QUEUE   ← x-max-length=3                    │   超长 → 队头被挤成死信
 *    └─────────────────────────────────────────────────────┘
 *     │  (消息过期 / 队列超长)
 *     ▼
 *  dlx.demo.exchange (direct, 死信交换机)
 *     │  路由 by routing key（两个业务队列各自的死信 routing key 都指向死信队列）
 *     ▼
 *  DLQ_QUEUE ← 消费者统一从这里消费，看 x-death 头判断死信原因
 *
 * ============================================================
 *  关键认知（面试点）：
 *  ============================================================
 *  - 死信交换机 DLX 本身就是一个【普通 Exchange】，可复用任何类型（这里用 direct）
 *  - DLX 参数配在【队列】上（x-dead-letter-exchange），不是配在 Exchange 上
 *  - 死信转发时用的 routing key 默认是原消息的，可用 x-dead-letter-routing-key 覆盖
 *  - 死信消息会在 header 里带 x-death 数组，记录死信原因（expired / max-length）、
 *    原始队列、时间等，消费端可据此区分处理（本 demo Consumer 会打印它）
 *  - 「订单延迟关单」「延迟任务」这类场景是 DLX+TTL 的【应用】，不是 DLX 本身，
 *    生产级延迟方案见 demo13_delay_plugin / demo14_delay_ttl_dlx
 *
 * @author wang
 * @date 2026-08-12
 */
public class DLXTopology {

    // ============================ 死信层 ============================
    // 两个业务队列的死信都进同一个死信交换机 + 死信队列，靠 x-death 头区分原因
    static final String DLX_EXCHANGE  = "dlx.demo.exchange";   // 死信交换机（普通 direct）
    static final String DLQ_QUEUE     = "dlx.demo.queue";       // 死信队列（消费者从这里消费）

    // ============================ 业务队列 1：TTL 过期触发 ============================
    static final String TTL_QUEUE     = "dlx.ttl.queue";        // 带 x-message-ttl，消息到期变死信
    static final String TTL_VALUE      = "5000";                 // 队列级 TTL：5 秒
    static final String TTL_ROUTING    = "ttl";                  // 发往 TTL_QUEUE 的 routing key
    static final String TTL_DLX_ROUTING = "dlx.ttl";            // 过期后转发到死信队列的 routing key

    // ============================ 业务队列 2：队列超长触发 ============================
    static final String MAXLEN_QUEUE   = "dlx.maxlen.queue";    // 带 x-max-length，超长队头变死信
    static final int    MAXLEN_VALUE   = 3;                     // 队列最多放 3 条，第 4 条挤掉队头
    static final String MAXLEN_ROUTING = "maxlen";             // 发往 MAXLEN_QUEUE 的 routing key
    static final String MAXLEN_DLX_ROUTING = "dlx.maxlen";     // 超长后转发到死信队列的 routing key

    /**
     * 创建并返回一个 RabbitMQ 连接（委托给通用工具类，连接参数集中管理）
     */
    static Connection createConnection() throws Exception {
        return ConnectionUtil.createConnection();
    }

    /**
     * 声明整个死信拓扑：死信层 + 两个业务队列
     *
     * 注意：先声明死信层（DLX_EXCHANGE / DLQ_QUEUE），再声明业务队列
     *       —— 业务队列引用了 DLX_EXCHANGE，如果 DLX 不存在会声明失败
     */
    static void declareTopology(Channel ch) throws Exception {

        // ============================================================
        // 第一步：声明【死信层】（消息变死信后的去处）
        // ============================================================

        // 死信交换机：普通 direct 类型（按精确 routing key 路由）
        ch.exchangeDeclare(DLX_EXCHANGE, BuiltinExchangeType.DIRECT, true, false, null);

        // 死信队列：普通持久化队列，消费者从这里消费
        ch.queueDeclare(DLQ_QUEUE, true, false, false, null);

        // 绑定：两个业务队列的死信 routing key 都绑到同一个死信队列
        //   TTL 队列过期消息用 TTL_DLX_ROUTING 转发 → 落到这里
        //   MAXLEN 队列超长消息用 MAXLEN_DLX_ROUTING 转发 → 也落到这里
        ch.queueBind(DLQ_QUEUE, DLX_EXCHANGE, TTL_DLX_ROUTING);
        ch.queueBind(DLQ_QUEUE, DLX_EXCHANGE, MAXLEN_DLX_ROUTING);

        // ============================================================
        // 第二步：声明【业务队列 1】—— TTL 过期触发死信
        // ============================================================
        Map<String, Object> ttlArgs = new HashMap<>();
        ttlArgs.put("x-message-ttl", Long.parseLong(TTL_VALUE));          // 消息 5 秒后过期变死信
        ttlArgs.put("x-dead-letter-exchange", DLX_EXCHANGE);              // 过期后转发给死信交换机
        ttlArgs.put("x-dead-letter-routing-key", TTL_DLX_ROUTING);        // 转发时用的 routing key
        ch.queueDeclare(TTL_QUEUE, true, false, false, ttlArgs);

        // 绑定：发往 TTL_QUEUE 的消息用 routing key = ttl
        // 注意业务 routing key 和死信 routing key 是两回事：
        //   - Producer 发消息用 TTL_ROUTING(=ttl) 进 TTL_QUEUE
        //   - 消息变死信后用 TTL_DLX_ROUTING(=dlx.ttl) 进死信队列
        ch.queueBind(TTL_QUEUE, DLX_EXCHANGE, TTL_ROUTING);

        // ============================================================
        // 第三步：声明【业务队列 2】—— 队列超长触发死信
        // ============================================================
        Map<String, Object> maxLenArgs = new HashMap<>();
        maxLenArgs.put("x-max-length", MAXLEN_VALUE);                    // 队列最多 3 条，超出挤掉队头
        maxLenArgs.put("x-dead-letter-exchange", DLX_EXCHANGE);          // 被挤掉的消息转发给死信交换机
        maxLenArgs.put("x-dead-letter-routing-key", MAXLEN_DLX_ROUTING); // 转发时用的 routing key
        ch.queueDeclare(MAXLEN_QUEUE, true, false, false, maxLenArgs);

        // 绑定：发往 MAXLEN_QUEUE 的消息用 routing key = maxlen
        ch.queueBind(MAXLEN_QUEUE, DLX_EXCHANGE, MAXLEN_ROUTING);
    }

    public static void main(String[] args) throws Exception {
        // 建立连接 → 声明拓扑 → 关闭
        // 拓扑只需声明一次，后续 Producer / Consumer 直接复用
        try (Connection conn = createConnection();
             Channel ch = conn.createChannel()) {
            declareTopology(ch);
            System.out.println("[拓扑] 死信拓扑声明完成：");
            System.out.println("  死信层: " + DLX_EXCHANGE + " → " + DLQ_QUEUE);
            System.out.println("  TTL队列: " + TTL_QUEUE + " (x-message-ttl=" + TTL_VALUE + "ms, 过期→死信)");
            System.out.println("  超长队列: " + MAXLEN_QUEUE + " (x-max-length=" + MAXLEN_VALUE + ", 超长→队头死信)");
            System.out.println("  运行 Producer 发消息，运行 Consumer 看死信原因（x-death 头）");
        }
    }
}
