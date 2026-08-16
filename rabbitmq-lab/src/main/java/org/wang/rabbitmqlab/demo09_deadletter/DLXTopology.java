package org.wang.rabbitmqlab.demo09_deadletter;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.BuiltinExchangeType;     // 内置交换机类型枚举（DIRECT/FANOUT/TOPIC...）
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;       // 用于创建连接的工厂类

import java.util.HashMap;                           // 哈希表（存放队列的额外参数 x-arguments）
import java.util.Map;                               // Map 接口

/**
 * Demo09 - 死信队列（Dead Letter Exchange / DLX）
 *
 * ============================================================
 *  本 demo 演示「订单延迟关单」场景：
 *  ============================================================
 *  下单后把消息发到带 TTL 的队列，消息过期后【自动】进入死信交换机，
 *  再路由到死信队列，消费者从死信队列消费时检查订单状态：
 *    - 仍未支付 → 取消订单（关单）
 *    - 已支付   → 忽略（幂等）
 *
 * ============================================================
 *  消息变成「死信」的三种触发条件（缺一不可的是队列要配 DLX）：
 *  ============================================================
 *  1. 消息 TTL 过期（本 demo 用这种）
 *  2. 消费端 basicNack / basicReject 且 requeue=false
 *  3. 队列达到最大长度（x-max-length，超出时队头消息变死信）
 *
 * ============================================================
 *  核心拓扑（4 个资源）：
 *  ============================================================
 *  Producer
 *     │  basicPublish → order.delay.exchange (topic, 普通)
 *     ▼
 *  order.delay.queue  ← 绑定 order.delay.exchange，routing key = order.delay
 *     │  - x-message-ttl = 10000  （10 秒后过期）
 *     │  - x-dead-letter-exchange = order.close.exchange  （过期后转发给死信交换机）
 *     │  - x-dead-letter-routing-key = order.close  （转发时用的 routing key）
 *     ▼
 *  [消息过期] → order.close.exchange (direct, 死信交换机)
 *     │  路由 by routing key = order.close
 *     ▼
 *  order.close.queue  ← 绑定 order.close.exchange，binding key = order.close
 *     │
 *     ▼
 *  Consumer（消费死信队列 → 检查订单状态 → 关单 / 忽略）
 *
 * ============================================================
 *  关键认知（面试点）：
 *  ============================================================
 *  - 死信交换机 DLX 本身就是一个【普通 Exchange】，可复用任何类型（这里用 direct）
 *  - DLX 参数配在【队列】上（x-dead-letter-exchange），不是配在 Exchange 上
 *  - TTL 队列本身【不消费】消息，纯粹用来「等过期」
 *  - 消息过期后 Broker 自动转发到 DLX，Producer / Consumer 无需干预
 *
 * @author wang
 * @date 2026-08-12
 */
public class DLXTopology {

    // ============================ 资源命名 ============================

    // 延迟层：Producer 发消息到这里
    static final String DELAY_EXCHANGE  = "order.delay.exchange";   // 延迟交换机（普通 topic）
    static final String DELAY_QUEUE     = "order.delay.queue";      // 延迟队列（带 TTL + DLX 参数）

    // 死信层：消息过期后落到这里
    static final String DLX_EXCHANGE    = "order.close.exchange";   // 死信交换机（DLX，用 direct）
    static final String DLX_QUEUE       = "order.close.queue";      // 死信队列（消费者从这里消费）

    // ============================ 参数 ============================

    // 延迟时长：10 秒（演示用，真实订单场景一般是 30 分钟）
    static final long TTL_MS = 10_000L;

    // 路由键
    static final String DELAY_ROUTING_KEY = "order.delay";          // Producer → 延迟队列
    static final String DLX_ROUTING_KEY   = "order.close";          // 延迟队列过期后 → 死信交换机

    /**
     * 创建并返回一个 RabbitMQ 连接
     * 与其他 demo 共用同一套连接参数（192.168.6.132 / admin / /mirror）
     */
    static Connection createConnection() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.6.132");           // Broker 的 IP 地址
        factory.setPort(5672);                      // Broker 的 AMQP 端口
        factory.setUsername("admin");               // 登录用户名
        factory.setPassword("passw0rd");            // 登录密码
        factory.setVirtualHost("/mirror");          // 虚拟主机
        return factory.newConnection();
    }

    /**
     * 声明整个死信拓扑：4 个资源 + 2 个绑定
     *
     * 注意：先声明死信层（DLX_EXCHANGE / DLX_QUEUE），再声明延迟队列
     *       —— 延迟队列引用了 DLX_EXCHANGE，如果 DLX 不存在会声明失败
     */
    static void declareTopology(Channel ch) throws Exception {

        // ============================================================
        // 第一步：声明【死信层】（消息过期后的去处）
        // ============================================================

        // 死信交换机：用 direct 类型（按精确 routing key 路由）
        // 参数：名称, 类型, durable=true(持久化), autoDelete=false, 内部参数=null
        ch.exchangeDeclare(DLX_EXCHANGE, BuiltinExchangeType.DIRECT, true, false, null);

        // 死信队列：普通持久化队列，消费者从这里消费
        ch.queueDeclare(DLX_QUEUE, true, false, false, null);

        // 绑定：死信队列按 binding key = order.close 绑到死信交换机
        // 延迟队列过期消息转发时用 DLX_ROUTING_KEY = order.close，会精确匹配到这里
        ch.queueBind(DLX_QUEUE, DLX_EXCHANGE, DLX_ROUTING_KEY);

        // ============================================================
        // 第二步：声明【延迟层】（消息先到这里等过期）
        // ============================================================

        // 延迟交换机：普通 topic 类型，Producer 把消息发到这里
        ch.exchangeDeclare(DELAY_EXCHANGE, BuiltinExchangeType.TOPIC, true, false, null);

        // 延迟队列的关键：通过 x-arguments 配置两个参数
        //   x-message-ttl            = 消息存活时间（毫秒），到期变死信
        //   x-dead-letter-exchange   = 过期后转发给哪个交换机（指向 DLX_EXCHANGE）
        //   x-dead-letter-routing-key= 转发时用的 routing key（覆盖原 routing key）
        Map<String, Object> delayArgs = new HashMap<>();
        delayArgs.put("x-message-ttl", TTL_MS);                  // TTL = 10 秒
        delayArgs.put("x-dead-letter-exchange", DLX_EXCHANGE);   // 过期后进死信交换机
        delayArgs.put("x-dead-letter-routing-key", DLX_ROUTING_KEY); // 转发时用 order.close

        // 声明延迟队列（durable=true 持久化，带上 DLX 参数）
        ch.queueDeclare(DELAY_QUEUE, true, false, false, delayArgs);

        // 绑定：延迟队列按 binding key = order.delay 绑到延迟交换机
        // Producer 发消息时 routing key = order.delay，会路由进延迟队列
        ch.queueBind(DELAY_QUEUE, DELAY_EXCHANGE, DELAY_ROUTING_KEY);
    }

    public static void main(String[] args) throws Exception {
        // 建立连接 → 声明拓扑 → 关闭
        // 拓扑只需声明一次，后续 Producer / Consumer 直接复用
        try (Connection conn = createConnection();
             Channel ch = conn.createChannel()) {
            declareTopology(ch);
            System.out.println("[拓扑] 死信拓扑声明完成：");
            System.out.println("  延迟层: " + DELAY_EXCHANGE + " → " + DELAY_QUEUE + " (TTL=" + TTL_MS + "ms)");
            System.out.println("  死信层: " + DLX_EXCHANGE + " → " + DLX_QUEUE);
            System.out.println("  过期后路由: " + DELAY_QUEUE + " --过期--> " + DLX_EXCHANGE
                    + " --(" + DLX_ROUTING_KEY + ")--> " + DLX_QUEUE);
        }
    }
}
