package org.wang.rabbitmqlab.demo10_persistence;

// RabbitMQ Java 客户端核心类
import com.rabbitmq.client.Channel;                 // 信道，所有 AMQP 操作的入口
import com.rabbitmq.client.Connection;              // 到 Broker 的 TCP 连接
import com.rabbitmq.client.ConnectionFactory;       // 用于创建连接的工厂类
import com.rabbitmq.client.GetResponse;             // basicGet 的返回结果（含消息体和元信息）
import com.rabbitmq.client.MessageProperties;       // 消息属性常量（持久化/非持久化文本）

import java.io.IOException;
import java.nio.charset.StandardCharsets;           // 字符编码，用于消息体转换

/**
 * Demo10 - 消息持久化（Message Persistence）
 *
 * ============================================================
 *  目的：演示「Broker 重启后消息还在不在」的三件套配置
 *  ============================================================
 *  持久化 = 三个独立的开关，缺一不可：
 *    1. durable Exchange  ：交换机定义在重启后还在（保「路由拓扑」，不存消息本身）
 *    2. durable Queue     ：队列定义在重启后还在（保「容器」）
 *    3. persistent Message：消息内容落盘（delivery_mode = 2）
 *
 *  记忆点：Exchange durable 保的是元数据/拓扑；Queue durable 保的是队列本身；
 *          只有 Message persistent 才是把「消息内容」写进磁盘。
 *          消息只有「落进 durable Queue 且 persistent」才真正落盘。
 *
 * ============================================================
 *  四象限存活矩阵（Broker 重启后）：
 *  ============================================================
 *    durable Queue + persistent Message     → ✅ 消息存活
 *    durable Queue + 非 persistent Message   → ❌ 队列还在，消息丢（消息在内存里）
 *    非 durable Queue + persistent Message   → ❌ 队列没了，消息跟着没
 *    非 durable Queue + 非 persistent Message → ❌ 队列和消息都没了
 *
 * ============================================================
 *  关键认知（面试高频）：
 *  ============================================================
 *  - 持久化只防「Broker 重启」，是「消息不丢」的【子集】，不等于不丢。
 *    生产端没 Confirm、路由不到队列、消费端自动 Ack，照样丢——那是「不丢」另外三段的事。
 *  - durable + persistent 只防「重启」，防不了「单机磁盘损坏/整机宕机」
 *    → 真高可用要 Quorum Queue 或镜像队列。
 *  - 持久化不是「发出去立刻落盘」：Broker 会批量刷盘，配合 Publisher Confirm
 *    才能确认「已写入磁盘」。
 *
 *  本 demo 用法：
 *    1. 先运行（无参）：声明 + 发布 4 条覆盖矩阵的消息
 *    2. 手动重启 Broker（如 docker restart rabbitmq 或 service rabbitmq-server restart）
 *    3. 再运行（加参数 read）：读取各队列，验证哪些消息活下来了
 *
 * @author wang
 * @date 2026-08-16
 */
public class Persistence {

    // 交换机：topic 类型 + durable，演示路由 + 拓扑持久化
    static final String EXCHANGE = "persist.exchange";
    // durable 队列：重启后队列定义还在
    static final String DURABLE_QUEUE = "persist.durable.queue";
    // 非 durable 队列：重启后队列定义消失（消息随队列一起没）
    static final String TRANSIENT_QUEUE = "persist.transient.queue";

    /**
     * 创建连接（与其他 demo 共用同一套连接参数）
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

    public static void main(String[] args) throws Exception {
        // 加参数 read = 重启后只读验证；无参 = 声明 + 发布
        if (args.length > 0 && args[0].equals("read")) {
            readAfterRestart();
        } else {
            declareAndPublish();
        }
    }

    /**
     * 第一阶段：声明拓扑 + 发布 4 条覆盖「存活矩阵」的消息
     */
    static void declareAndPublish() throws Exception {
        try (Connection conn = createConnection();
             Channel ch = conn.createChannel()) {

            // ============================================================
            // 1. 声明 durable 交换机
            //    参数3 durable=true：交换机定义持久化，Broker 重启后拓扑还在
            //    注意：这只是保「拓扑/元数据」，Exchange 本身从不存消息
            // ============================================================
            ch.exchangeDeclare(EXCHANGE, "topic", true);

            // ============================================================
            // 2. 声明两种队列，绑定到交换机
            //    参数2 durable：
            //      true  = 队列持久化，重启后队列还在（里面的消息是否还在取决于消息是否 persistent）
            //      false = 队列非持久化，重启后队列消失，消息随队列一起丢
            // ============================================================
            ch.queueDeclare(DURABLE_QUEUE, true, false, false, null);    // durable 队列
            ch.queueDeclare(TRANSIENT_QUEUE, false, false, false, null); // 非 durable 队列

            // 绑定：按 routing key 前缀路由（durable.* 进 durable 队列，transient.* 进 transient 队列）
            ch.queueBind(DURABLE_QUEUE, EXCHANGE, "durable.*");
            ch.queueBind(TRANSIENT_QUEUE, EXCHANGE, "transient.*");

            // ============================================================
            // 3. 发布 4 条消息，覆盖「存活矩阵」四个象限
            //    关键属性：delivery_mode
            //      MessageProperties.PERSISTENT_TEXT_PLAIN → delivery_mode = 2 → 消息【落盘】
            //      MessageProperties.TEXT_PLAIN            → delivery_mode = 1 → 消息【只在内存】
            // ============================================================

            // 象限1：durable 队列 + persistent 消息 → 重启后存活 ✅
            ch.basicPublish(EXCHANGE, "durable.persist",
                    MessageProperties.PERSISTENT_TEXT_PLAIN,                       // ← delivery_mode=2，落盘
                    "durable队列 + persistent消息".getBytes(StandardCharsets.UTF_8));

            // 象限2：durable 队列 + 非 persistent 消息 → 队列在但消息丢 ❌
            ch.basicPublish(EXCHANGE, "durable.transient",
                    MessageProperties.TEXT_PLAIN,                                  // ← delivery_mode=1，只在内存
                    "durable队列 + 非persistent消息".getBytes(StandardCharsets.UTF_8));

            // 象限3：非 durable 队列 + persistent 消息 → 队列没了消息跟着没 ❌
            ch.basicPublish(EXCHANGE, "transient.persist",
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    "非durable队列 + persistent消息".getBytes(StandardCharsets.UTF_8));

            // 象限4：非 durable 队列 + 非 persistent 消息 → 全丢 ❌
            ch.basicPublish(EXCHANGE, "transient.transient",
                    MessageProperties.TEXT_PLAIN,
                    "非durable队列 + 非persistent消息".getBytes(StandardCharsets.UTF_8));

            System.out.println(" [x] 已声明 durable 交换机 + durable/transient 两种队列，并发布 4 条矩阵消息");
            System.out.println();
            printMatrix();
            System.out.println();
            System.out.println(" >>> 下一步：手动重启 Broker，然后运行参数 read 验证哪些消息存活");
        }
    }

    /**
     * 打印「存活矩阵」说明（重启后哪些消息还在）
     */
    static void printMatrix() {
        System.out.println(" ┌───────────────────────┬────────────────────┬──────────────┐");
        System.out.println(" │ 队列 durability        │ 消息 delivery_mode │ 重启后        │");
        System.out.println(" ├───────────────────────┼────────────────────┼──────────────┤");
        System.out.println(" │ durable=true           │ 2 (persistent)      │ ✅ 存活       │");
        System.out.println(" │ durable=true           │ 1 (非 persistent)   │ ❌ 队列在消息丢│");
        System.out.println(" │ durable=false          │ 2 (persistent)      │ ❌ 队列消息全没│");
        System.out.println(" │ durable=false          │ 1 (非 persistent)   │ ❌ 全没        │");
        System.out.println(" └───────────────────────┴────────────────────┴──────────────┘");
    }

    /**
     * 第二阶段：Broker 重启后运行，验证各队列里消息是否存活
     *
     * 用 queueDeclarePassive 探测队列是否存在（不存在会抛 404），
     * 再用 basicGet 把消息读出来（autoAck=true 读即消费）。
     */
    static void readAfterRestart() throws Exception {
        try (Connection conn = createConnection();
             Channel ch = conn.createChannel()) {

            System.out.println("========== Broker 重启后的存活验证 ==========");

            // durable 队列：队列本身应该还在，看里面还剩几条（persistent 的存活）
            System.out.println("\n[durable 队列] " + DURABLE_QUEUE);
            if (queueExists(ch, DURABLE_QUEUE)) {
                drain(ch, DURABLE_QUEUE);   // 读出并打印所有现存消息
            } else {
                System.out.println("  ✗ 队列不存在（说明 durable 队列也丢了，异常）");
            }

            // transient 队列：重启后队列定义已消失，应探测不到（404）
            System.out.println("\n[transient 队列] " + TRANSIENT_QUEUE);
            if (queueExists(ch, TRANSIENT_QUEUE)) {
                drain(ch, TRANSIENT_QUEUE);
            } else {
                System.out.println("  ✗ 队列不存在（符合预期：非 durable 队列重启后消失，消息随队列丢）");
            }

            System.out.println("\n========== 结论 ==========");
            System.out.println("durable 队列里只有 persistent(delivery_mode=2) 的消息存活；");
            System.out.println("非 persistent 消息、非 durable 队列全部在重启后丢失。");
            System.out.println("持久化=防重启；要防节点宕机/磁盘损坏，需 Quorum Queue / 镜像队列。");
        }
    }

    /**
     * 用 passive 声明探测队列是否存在
     * 存在 → 返回 true；不存在 → Broker 回 404，channel 报错关闭
     * （所以探测失败后 channel 不可用，需换新 channel——demo 里简化处理：先探测再决定是否 drain）
     */
    static boolean queueExists(Channel ch, String queue) throws IOException {
        try {
            // passive=true：只检查不创建；队列不存在则抛 IOException(404 NOT_FOUND)
            ch.queueDeclarePassive(queue);
            return true;
        } catch (IOException e) {
            // 404：队列不存在。注意此刻 channel 已被 Broker 关闭，调用方不该再用它 drain
            return false;
        }
    }

    /**
     * 用 basicGet 循环读出队列里所有现存消息（autoAck=true，读即消费）
     */
    static void drain(Channel ch, String queue) throws IOException {
        int count = 0;
        GetResponse resp;
        // 循环 basicGet：返回 null 表示队列已空
        while ((resp = ch.basicGet(queue, true)) != null) {
            String body = new String(resp.getBody(), StandardCharsets.UTF_8);
            int deliveryMode = resp.getProps().getDeliveryMode() == null ? -1 : resp.getProps().getDeliveryMode();
            System.out.println("  ✓ 存活消息: '" + body + "' (delivery_mode=" + deliveryMode + ")");
            count++;
        }
        if (count == 0) {
            System.out.println("  ✗ 队列存在但为空（消息在重启时丢失）");
        } else {
            System.out.println("  共存活 " + count + " 条");
        }
    }
}
