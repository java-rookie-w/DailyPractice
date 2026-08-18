package org.wang.rabbitmqlab.demo11_reliability;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.wang.rabbitmqlab.common.ConnectionUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * demo11 — 消息不丢（端到端可靠性）拓扑声明
 *
 * 本类只负责“建拓扑”，把 生产端 / Broker / 消费端 三段要用到的资源一次性声明好：
 *   1) 业务 Exchange（持久） + 业务 Queue（持久，并挂死信）
 *   2) 死信 Exchange + 死信 Queue（消费端处理失败的消息归宿）
 *
 * 为什么单独抽一个拓扑类：三段 demo 都依赖同一套资源，先跑本类把资源建出来，
 * 后面 Producer / Consumer 才能直接收发，避免“资源不存在”报错。
 *
 * 对应面试题里 Broker 那一段：
 *   - exchangeDeclare(..., true)        → 持久交换机，重启不丢“拓扑元数据”
 *   - queueDeclare(..., true, ...)      → 持久队列，重启不丢“队列容器”
 *   - 消息 deliveryMode=2（见 Producer） → 持久化消息，重启不丢“消息内容”
 *   三者缺一不可，但“持久化”只是“Broker 重启不丢”，不等于万无一失（见图 demo10）。
 */
public class ReliabilityTopology {

    // ===== 业务链路（正常下单）=====
    static final String BIZ_EXCHANGE = "reliability.exchange";   // 持久 direct 交换机
    static final String BIZ_QUEUE = "reliability.queue";          // 持久队列
    static final String BIZ_ROUTING = "order.create";             // 绑定 / 路由键

    // ===== 死信链路（消费失败）=====
    static final String DLX_EXCHANGE = "reliability.dlx";         // 死信交换机
    static final String DLQ_QUEUE = "reliability.dlq";            // 死信队列
    static final String DLX_ROUTING = "dlx.order.create";         // 死信路由键

    public static void main(String[] args) throws Exception {
        // 复用统一连接工具（rabbitmq-lab 已全部收敛到 ConnectionUtil）
        try (Connection connection = ConnectionUtil.createConnection();
             Channel channel = connection.createChannel()) {

            // 1) 先建死信交换机 + 死信队列（业务队列要引用它）
            channel.exchangeDeclare(DLX_EXCHANGE, BuiltinExchangeType.DIRECT, true); // 第三个参数 durable=true
            channel.queueDeclare(DLQ_QUEUE, true, false, false, null);
            channel.queueBind(DLQ_QUEUE, DLX_EXCHANGE, DLX_ROUTING);

            // 2) 业务交换机（持久）
            channel.exchangeDeclare(BIZ_EXCHANGE, BuiltinExchangeType.DIRECT, true);

            // 3) 业务队列（持久），并挂死信参数：
            //    消费端 basicNack(requeue=false) 的消息会按这两个参数转发到死信队列，不丢。
            Map<String, Object> argsMap = new HashMap<>();
            argsMap.put("x-dead-letter-exchange", DLX_EXCHANGE);
            argsMap.put("x-dead-letter-routing-key", DLX_ROUTING);
            channel.queueDeclare(BIZ_QUEUE, true, false, false, argsMap);

            // 4) 绑定业务队列到业务交换机
            channel.queueBind(BIZ_QUEUE, BIZ_EXCHANGE, BIZ_ROUTING);

            System.out.println("[x] 拓扑声明完成：业务队列 + 死信队列 均已就绪（均为持久）");
        }
    }
}
