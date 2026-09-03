package org.wang.rabbitmqlab.springdemo.reliability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.wang.rabbitmqlab.springdemo.reliability.ReliabilityConfig.EXCHANGE;
import static org.wang.rabbitmqlab.springdemo.reliability.ReliabilityConfig.ROUTING_KEY;

/**
 * 生产端：负责「消息不丢」里生产者这一半。【标准实现】
 *
 * 三件事：
 *   1. Publisher Confirm：broker 到底收没收到
 *   2. Return 回调：收到是收到了，但有没有进队列（路由失败）
 *   3. 消息持久化 + messageId：broker 重启不丢 + 给消费端幂等留钥匙
 *
 * 本类不是 @Component，是 ReliabilityConfig 里用 @Bean new 出来的，
 * 所以构造注入 RabbitTemplate 即可。只要它是 Spring Bean，下面的 @Scheduled 就生效。
 */
public class ReliabilityProducer {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityProducer.class);

    private final RabbitTemplate rabbitTemplate;

    private final AtomicInteger round = new AtomicInteger(0);

    public ReliabilityProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;

        // ========== Confirm 回调 ==========
        // 回答："broker 收到并处理了这条消息吗？"
        //   correlationData —— 发送时传进去的那个对象，能取回 id
        //   ack             —— true/false
        //   cause           —— 只有 ack=false 时才有值（broker 内部错误），业务上几乎见不到
        //
        // ⚠️ ack=true 只代表 broker 收下了，**完全不代表进了队列**。
        //    路由失败时消息被丢弃，confirm 照样回 ack=true。
        this.rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String id = correlationData != null ? correlationData.getId() : "null";
            if (ack) {
                log.info("[Confirm] ack=true    id={}  ← 只代表 broker 收下，不代表进了队列", id);
            } else {
                log.error("[Confirm] ack=false   id={} cause={}", id, cause);
            }
        });

        // ========== Return 回调 ==========
        // 回答的是另一个问题："这条消息进了至少一个队列吗？"
        // 只有路由不到任何队列时才触发，前提是 yml 里开 spring.rabbitmq.template.mandatory=true。
        // replyCode=312 就是 NO_ROUTE。
        //
        // ⚠️ broker 先发 basic.return，再发 basic.ack。
        //    所以 Return 触发时，Confirm **依然** 是 ack=true —— 这就是"confirm 全绿但消息丢了"。
        this.rabbitTemplate.setReturnsCallback(returned -> {
            log.error("[Return ] 消息被退回！replyCode={} replyText={} exchange={} rk={} body={}",
                    returned.getReplyCode(),
                    returned.getReplyText(),
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    new String(returned.getMessage().getBody(), StandardCharsets.UTF_8));
        });
    }

    // ================== 发送方法 ==================

    /** 发一条正常消息：走队列，消费端正常处理并 ack */
    public void sendOk(String body) {
        send(ROUTING_KEY, body, UUID.randomUUID().toString());
    }

    /**
     * 发一条路由不到队列的消息 —— 本 demo 的核心现场。
     * 唯一区别就是 routing key 写错，对应消费端收不到、Return 回调触发。
     */
    public void sendUnroutable(String body) {
        send(ROUTING_KEY + ".typo", body, UUID.randomUUID().toString());
    }

    /**
     * 发一条让消费端失败的消息（body 里带 FAIL），触发 nack -> 死信。
     * 走的还是正常 routing key，会进队列，只是消费端处理时抛异常。
     */
    public void sendFail(String body) {
        send(ROUTING_KEY, body, UUID.randomUUID().toString());
    }

    /**
     * 用**指定 id** 发消息：用来模拟"同一条消息被投递了两次"。
     * 消费端的幂等表靠 messageId 去重，所以第二次发同 id 的消息会被拦下。
     */
    public void sendWithId(String id, String body) {
        send(ROUTING_KEY, body, id);
    }

    /**
     * 第三种失败：交换机根本不存在。
     * 这时候连 confirm 都不走 —— broker 直接关掉 channel，RabbitTemplate 抛 AmqpException。
     * 默认没接进 produce()，想看这个现象就手动加一行调用。
     */
    public void sendToMissingExchange(String body) {
        String id = UUID.randomUUID().toString();
        try {
            rabbitTemplate.convertAndSend(EXCHANGE + ".not-exist", ROUTING_KEY, body,
                    persistentWithId(id), new CorrelationData(id));
        } catch (Exception e) {
            log.error("[Send   ] 交换机不存在，直接抛异常（不走 confirm）：{}", e.getMessage());
        }
    }

    // ================== 触发器 ==================

    /**
     * 定时发一轮，方便边跑边观察。
     * @EnableScheduling 已经在 SpringDemoApplication 上开过了。
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 20000)
    public void produce() {
        log.info("================ 第 {} 轮发送 ================", round.incrementAndGet());
        sendOk("order-1 正常消息");
        sendOk("order-2 正常消息");
        sendFail("order-3 FAIL 业务处理会失败 -> 进死信");
        sendUnroutable("order-4 这条 rk 写错 -> 被退回");
        // 同一个 id 连发两次，第二次应被消费端幂等拦下（业务只执行一次）
        sendWithId("dup-demo-001", "order-dup 同一 id 发两次");
        sendWithId("dup-demo-001", "order-dup 同一 id 发两次");
    }

    // ================== 内部实现 ==================

    private void send(String routingKey, String body, String id) {
        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, body,
                persistentWithId(id), new CorrelationData(id));
        log.info("[Send   ] 已发送 rk={} id={} body={}", routingKey, id, body);
    }

    /**
     * 给消息加两样东西：
     *   deliveryMode=PERSISTENT —— 持久化，broker 重启后消息还在（队列本身也必须 durable）
     *   messageId               —— 消费端幂等去重的依据
     *
     * 补充：RabbitTemplate 默认发的就是持久化消息（MessageProperties 的默认值），
     * 这里显式写一遍是为了看得见、也想得起这回事。
     */
    private MessagePostProcessor persistentWithId(String id) {
        return message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(id);
            return message;
        };
    }
}
