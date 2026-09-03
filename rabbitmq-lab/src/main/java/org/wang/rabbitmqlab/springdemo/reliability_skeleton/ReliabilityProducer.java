package org.wang.rabbitmqlab.springdemo.reliability_skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

import static org.wang.rabbitmqlab.springdemo.reliability_skeleton.ReliabilityConfig.EXCHANGE;
import static org.wang.rabbitmqlab.springdemo.reliability_skeleton.ReliabilityConfig.ROUTING_KEY;

/**
 * 生产端：负责「消息不丢」里生产者这一半。
 *
 * 要演示的三件事：
 *   1. Publisher Confirm：broker 到底收没收到
 *   2. Return 回调：收到是收到了，但有没有进队列（路由失败）
 *   3. 消息持久化 + messageId：broker 重启不丢 + 给消费端幂等留钥匙
 *
 * 注意：本类不是 @Component，是 ReliabilityConfig 里用 @Bean new 出来的，
 * 所以构造注入 RabbitTemplate 即可（写法见 ReliabilityConfig.reliabilityProducer）。
 * 只要它是 Spring Bean，下面的 @Scheduled 就生效（helloworld 的 Tut1Sender 同理）。
 */
public class ReliabilityProducer {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public ReliabilityProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;

        // ========== TODO 1：注册 Confirm 回调 ==========
        // 回答的问题是："broker 收到并处理了这条消息吗？"
        // 三个参数：
        //   correlationData —— 发送时你自己传进去的那个对象，从中能取回 id
        //   ack             —— true/false
        //   cause           —— 只有 ack=false 时才有值（broker 内部错误），正常业务几乎见不到
        //
        // ⚠️ 最大的坑：ack=true 只代表 broker 收下了，**完全不代表进了队列**。
        //    路由失败时消息被丢弃，confirm 照样回 ack=true（见 TODO 4 的现象）。
        //
        // 写法：
         this.rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
             String id = correlationData != null ? correlationData.getId() : "null";
             if (ack) {
                 log.info("[Confirm] ack=true   id={}", id);
             } else {
                 log.error("[Confirm] ack=false  id={} cause={}", id, cause);
             }
         });

        // ========== TODO 2：注册 Return 回调 ==========
        // 回答的是另一个问题："这条消息进了至少一个队列吗？"
        // 只有路由不到任何队列时才会触发，且必须在 yml 里开 spring.rabbitmq.template.mandatory=true
        // （application-reliability.yml 里已经开了）。
        //
        // ReturnedMessage 能取到：getMessage() / getReplyCode()（312 = NO_ROUTE）
        //                        / getReplyText() / getExchange() / getRoutingKey()
        //
        // ⚠️ 顺序：broker 先发 basic.return，再发 basic.ack。
        //    所以 Return 触发时，Confirm **依然** 是 ack=true —— 这就是"confirm 全绿但消息丢了"。
        //
        // 写法：
         this.rabbitTemplate.setReturnsCallback(returned -> {
             log.error("[Return] 消息被退回！replyCode={} replyText={} exchange={} rk={} body={}",
                     returned.getReplyCode(), returned.getReplyText(),
                     returned.getExchange(), returned.getRoutingKey(),
                     returned.getMessage().getBody());
         });
    }

    // ================== 发送方法 ==================

    /**
     * TODO 3：发一条正常消息（走队列，消费端正常处理并 ack）
     *
     * 步骤：
     *   a) 生成业务唯一 id：String id = UUID.randomUUID().toString();
     *   b) 调下面的 persistentWithId(id) 拿到 MessagePostProcessor
     *   c) rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, body, mpp, new CorrelationData(id));
     *
     * 关于 CorrelationData：它只是"这一批发送的编号"，用来在回调里对上号，
     * **不是业务幂等键** —— 别把它和 id 混为一谈（这里我们让两者取同一个值，方便观察）。
     */
    public void sendOk(String body) {
        // TODO 3
        String id = UUID.randomUUID().toString();
        MessagePostProcessor mpp = persistentWithId(id);
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, body, mpp, new CorrelationData(id));
    }

    /**
     * TODO 4：发一条路由不到队列的消息 —— 本 demo 的核心现场
     *
     * 做法：跟 sendOk 一模一样，只把 routing key 写错（例如 ROUTING_KEY + ".typo"）。
     *
     * 预期现象（务必亲眼看到）：
     *   [Return]  replyCode=312  NO_ROUTE   ← 消息被退回
     *   [Confirm] ack=true                  ← 注意！照样是 ack
     * 生产上如果不开 mandatory + Return 回调，这条消息就是彻底静默消失。
     */
    public void sendUnroutable(String body) {
        // TODO 4
        String id = UUID.randomUUID().toString();
        MessagePostProcessor mpp = persistentWithId(id);
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY + ".typo", body, mpp, new CorrelationData(id));
    }

    /**
     * TODO 5：发一条让消费端失败的消息，用来触发 nack -> 死信
     *
     * 做法：还是发到 ROUTING_KEY，只是 body 里带上 "FAIL" 字样
     *      （消费端约定：body 含 FAIL 就抛异常，见 ReliabilityConsumer TODO 2）
     *
     * 预期现象：
     *   业务队列消费失败 → basicNack(requeue=false) → 消息从原队列消失 → 出现在死信队列
     *   → DlxConsumer 打印 x-death 信息
     *
     * 前提：ReliabilityConfig 里的死信交换机/死信队列/绑定（TODO 1~3）必须先补齐，
     *       否则这条消息 nack 之后**直接消失**，两边都看不到。
     */
    public void sendFail(String body) {
        // TODO 5
        String id = UUID.randomUUID().toString();
        MessagePostProcessor mpp = persistentWithId(id);
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, body, mpp, new CorrelationData(id));
    }

    // ================== 触发器 ==================

    /**
     * TODO 6：定时发一轮，方便边跑边观察。
     * 注意 @EnableScheduling 已经在 SpringDemoApplication 上开过了。
     *
     * 建议顺序（一次看全三种情况）：
     *   sendOk("order-1");
     *   sendOk("order-2");
     *   sendFail("order-3 FAIL");
     *   sendUnroutable("order-4 这条路由不到");
     *
     * 另一种玩法：不想定时重复发，就让本类 implements CommandLineRunner，
     * 在 run() 里按固定脚本发一次，现象更干净、更好截图进笔记。
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 20000)
    public void produce() {
        // TODO 6
        sendOk("order-1");
        sendOk("order-2");
        sendFail("order-3 FAIL");
        sendUnroutable("order-4 这条路由不到");
    }

    // ================== 小工具（已写好，直接用） ==================

    /**
     * 给消息加两样东西：
     *   deliveryMode=PERSISTENT —— 持久化，broker 重启后消息还在（队列本身也必须 durable）
     *   messageId               —— 消费端幂等去重的依据
     *
     * 补充知识：RabbitTemplate 默认发的就是持久化消息（MessageProperties 的默认值），
     * 这里显式写一遍是为了看得见、也想得起这回事，面试时说得出。
     */
    private MessagePostProcessor persistentWithId(String id) {
        return message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(id);
            return message;
        };
    }
}
