package org.wang.rabbitmqlab.demo13_delay_plugin;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.BuiltinExchangeType;     // 内置交换机类型枚举（DIRECT/FANOUT/TOPIC...）
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import org.wang.rabbitmqlab.common.ConnectionUtil;       // 用于创建连接的工厂类

/**
 * Demo13 - 延迟订单取消（方案一：rabbitmq_delayed_message_exchange 插件）
 *
 * ============================================================
 *  业务场景：延迟订单取消
 *  ============================================================
 *  用户下单后给一段支付时间（如 30 分钟），超时未支付 → 自动取消订单。
 *  本 demo 用 RabbitMQ 的延迟交换机插件实现，是生产上【最常用】的延迟方案。
 *
 * ============================================================
 *  生产方案选型：为什么用插件而不是 TTL+DLX
 *  ============================================================
 *  RabbitMQ 原生没有"延迟投递"能力，常见有两条路：
 *
 *  A) 插件方案（本 demo）：rabbitmq_delayed_message_exchange
 *     - 交换机类型变成 x-delayed-message，发消息时带 x-delay 头（毫秒）
 *     - 每条消息独立延迟时长，无队头阻塞
 *     - 代码最简单，生产最常用
 *     - 缺点：要装插件；延迟消息存在 Mnesia，单节点有上限，集群下也有单点风险
 *
 *  B) TTL + DLX 多级队列方案（见 demo14_delay_ttl_dlx）
 *     - 不依赖插件，纯原生 AMQP
 *     - 用几个固定 TTL 的延迟队列"拼"出任意延迟
 *     - 兼容性最好，但要预先规划延迟档位
 *
 *  为什么不用"单队列 + 消息级 TTL + DLX"（demo12_ttl 已验证过的坑）：
 *     消息级 TTL 只在消息到达【队头】时才检查，短 TTL 消息排在长 TTL 消息后面会延迟过期，
 *     订单 30 分钟超时场景下精度无法保证，不能直接用。
 *
 * ============================================================
 *  核心拓扑（插件方案，资源少）：
 *  ============================================================
 *  Producer
 *     │  basicPublish + x-delay header（每条独立延迟时长）
 *     ▼
 *  order.cancel.schedule.exchange (x-delayed-message 插件交换机)
 *     │  插件内部按 x-delay 延迟，到点才把消息投给绑定的队列
 *     ▼
 *  order.cancel.schedule.queue  ← 绑定 order.cancel.schedule.exchange
 *     │
 *     ▼
 *  Consumer（消费 = 订单已超时 → 查订单状态 → 未支付就关单）
 *
 *  对比 demo09 的 TTL+DLX：那里延迟队列有 TTL + 死信参数，要两个交换机两个队列；
 *  插件方案只要一个交换机一个队列，"延迟"逻辑在交换机里完成，不经过死信。
 *
 * ============================================================
 *  关键认知（面试点）：
 *  ============================================================
 *  - 插件交换机声明时 type=x-delayed-message，并带 x-delayed-type 参数指定真实路由类型
 *    （插件交换机本身不路由，转发给绑定队列时才按 x-delayed-type 路由）
 *  - 发消息时通过 header 的 x-delay 指定延迟毫秒数，每条独立，无队头阻塞
 *  - 插件把延迟消息存在 Mnesia，Broker 重启后【仍在】且继续计时（这点比纯内存方案可靠）
 *  - 单点风险：Mnesia 是节点本地存储，节点宕机未做镜像时延迟消息会受影响
 *  - 业务落地三件套（本 demo 用 println 模拟，注释标清真实写法）：
 *      1) 消费时查订单状态：UNPAID 才关单，已支付则忽略（幂等）
 *      2) 关单用条件更新：UPDATE order SET status='CANCEL' WHERE id=? AND status='UNPAID'
 *         受影响行数=0 说明已被支付回调改掉，忽略（防并发）
 *      3) MQ 不可靠兜底：定时任务扫「UNPAID 且超时」的订单关单，防止漏关
 *
 *  前置条件：Broker 要装 rabbitmq_delayed_message_exchange 插件
 *    rabbitmq-plugins enable rabbitmq_delayed_message_exchange
 *    （或 Docker 镜像带插件版本；装了之后 management 界面 exchange type 会多出 x-delayed-message）
 *
 * @author wang
 * @date 2026-08-18
 */
public class DelayPluginTopology {

    // ============================ 资源命名 ============================
    static final String DELAY_EXCHANGE = "order.cancel.schedule.exchange";   // 订单取消调度交换机（x-delayed-message 插件类型）
    static final String DELAY_QUEUE    = "order.cancel.schedule.queue";      // 订单取消调度队列（消费者从这里取到期未支付订单）
    static final String DELAY_ROUTING  = "order.cancel.schedule";            // 路由键

    // 延迟交换机的插件类型名（RabbitMQ 内置交换机类型枚举里没有，用字符串）
    static final String X_DELAYED_MESSAGE_TYPE = "x-delayed-message";

    /**
     * 声明整个延迟拓扑：1 个插件交换机 + 1 个队列 + 1 个绑定
     */
    static void declareTopology(Channel ch) throws Exception {

        // 1) 声明延迟交换机：type = x-delayed-message（插件提供）
        //    参数 args 里 x-delayed-type 指定【真实路由类型】（direct/topic/fanout/headers）
        //    插件交换机本身只负责"延迟"，到点后按 x-delayed-type 把消息路由给绑定的队列
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("x-delayed-type", BuiltinExchangeType.DIRECT.getType());   // 真实路由用 direct
        ch.exchangeDeclare(DELAY_EXCHANGE, X_DELAYED_MESSAGE_TYPE, true, false, args);

        // 2) 声明延迟队列：普通持久化队列，消费者直接从这里消费（不需要 DLX 参数）
        ch.queueDeclare(DELAY_QUEUE, true, false, false, null);

        // 3) 绑定：订单取消调度队列按 order.cancel.schedule 绑到订单取消调度交换机
        //    Producer 发消息 routing key = order.cancel.schedule，延迟到点后投递到这里
        ch.queueBind(DELAY_QUEUE, DELAY_EXCHANGE, DELAY_ROUTING);
    }

    public static void main(String[] args) throws Exception {
        try (Connection conn = ConnectionUtil.createConnection();
             Channel ch = conn.createChannel()) {
            declareTopology(ch);
            System.out.println("[拓扑] 延迟插件拓扑就绪：");
            System.out.println("  " + DELAY_EXCHANGE + " (x-delayed-message, direct) → " + DELAY_QUEUE);
            System.out.println("  发消息时带 x-delay 头（毫秒），每条独立延迟，无队头阻塞");
            System.out.println("  运行 Producer 下单，Consumer 看延迟到点关单");
        }
    }
}
