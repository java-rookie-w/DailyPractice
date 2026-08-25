package org.wang.rabbitmqlab.demo12_ttl;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.wang.rabbitmqlab.common.ConnectionUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * demo12 — TTL（消息过期）拓扑声明
 *
 * RabbitMQ 的 TTL 有两种设置方式，面试常考区别：
 *   A) 队列级 TTL：声明队列时设 x-message-ttl —— 队列里“所有”消息统一过期时间，
 *      由 Broker 的队列级定时器统一触发，与消息在队列中的位置无关（精确）。
 *   B) 消息级 TTL：发消息时给单条设 expiration —— 每条独立过期时间，
 *      但 Broker 只在消息“到达队头”时才检查 TTL（惰性检查），所以会被队头长 TTL 消息阻塞（不精确）。
 *
 * 两种都配合死信（x-dead-letter-exchange）：过期消息自动转发到死信队列，便于观察。
 */
public class TtlTopology {

    // 队列级 TTL 队列：所有消息 5 秒过期
    static final String QLEVEL_QUEUE = "ttl.qlevel";
    static final String QLEVEL_TTL = "5000";

    // 消息级 TTL 队列：队列不设 TTL，过期由每条消息的 expiration 决定
    static final String MLEVEL_QUEUE = "ttl.mlevel";

    // 死信链路（两个队列的过期消息都进同一个死信队列，靠 routingKey 区分来源）
    static final String DLX_EXCHANGE = "ttl.dlx";
    static final String DLQ_QUEUE = "ttl.dlq";
    static final String DLX_QLEVEL = "dlx.qlevel";
    static final String DLX_MLEVEL = "dlx.mlevel";

    public static void main(String[] args) throws Exception {
        try (Connection connection = ConnectionUtil.createConnection();
             Channel channel = connection.createChannel()) {

            // 1) 死信交换机 + 死信队列（先建，业务队列要引用）
            channel.exchangeDeclare(DLX_EXCHANGE, BuiltinExchangeType.DIRECT, true);
            channel.queueDeclare(DLQ_QUEUE, true, false, false, null);
            channel.queueBind(DLQ_QUEUE, DLX_EXCHANGE, DLX_QLEVEL);   // 接收队列级过期消息
            channel.queueBind(DLQ_QUEUE, DLX_EXCHANGE, DLX_MLEVEL);   // 接收消息级过期消息

            // 2) 队列级 TTL 队列：x-message-ttl=5000，全部 5s 过期 → 死信
            Map<String, Object> qArgs = new HashMap<>();
            qArgs.put("x-message-ttl", Long.parseLong(QLEVEL_TTL));   // 队列级：毫秒
            qArgs.put("x-dead-letter-exchange", DLX_EXCHANGE);
            qArgs.put("x-dead-letter-routing-key", DLX_QLEVEL);
            channel.queueDeclare(QLEVEL_QUEUE, true, false, false, qArgs);

            // 3) 消息级 TTL 队列：不设队列 TTL，过期由每条消息的 expiration 决定 → 死信
            Map<String, Object> mArgs = new HashMap<>();
            mArgs.put("x-dead-letter-exchange", DLX_EXCHANGE);
            mArgs.put("x-dead-letter-routing-key", DLX_MLEVEL);
            channel.queueDeclare(MLEVEL_QUEUE, true, false, false, mArgs);

            System.out.println("[x] TTL 拓扑就绪：队列级(" + QLEVEL_TTL + "ms) + 消息级 + 死信队列");
        }
    }
}
