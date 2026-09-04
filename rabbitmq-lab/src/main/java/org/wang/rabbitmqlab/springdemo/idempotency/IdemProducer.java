package org.wang.rabbitmqlab.springdemo.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

import static org.wang.rabbitmqlab.springdemo.idempotency.IdemConfig.EXCHANGE;
import static org.wang.rabbitmqlab.springdemo.idempotency.IdemConfig.ROUTING_KEY;

/**
 * 幂等 demo 的发送端：制造「重复投递」和「失败后重投」两种现场。【标准实现】
 *
 * 🔑 这个 demo 最重要的一课，全在 sendOrder() 那两行：
 *
 *   messageId = UUID.randomUUID()   ← 每条消息都是新的，重发时**一定不一样**
 *   header["order_id"] = orderId    ← 业务键，重发保持**不变**
 *
 * 为什么幂等键不能用 messageId：
 *   broker 重投（ack 前宕机、nack 回队）时消息属性不变，messageId 确实一样，看着能用；
 *   但**生产端自己重发**（发送超时重试、补偿任务、从死信捞回来重投）会产生一条
 *   messageId 全新的消息 —— 用 messageId 去重，这种重复一条都拦不住。
 *   而幂等要表达的是"**这笔业务只做一次**"，所以键必须是业务维度的（订单号 + 事件类型）。
 *
 * 这也是为什么本 demo 刻意让三次投递带上三个不同的 messageId ——
 * 只要你在日志里看到"messageId 不同、但被判成重复"，这一课就到手了。
 */
public class IdemProducer {

    /** 业务键放在消息头里，消费端从 header 取（也可以放消息体，头里更好排查） */
    public static final String ORDER_ID_HEADER = "order_id";

    private static final Logger log = LoggerFactory.getLogger(IdemProducer.class);

    private final RabbitTemplate rabbitTemplate;

    private int round = 0;

    public IdemProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发一笔订单：messageId 每次都新生成，业务键固定。
     * 这样"同一笔业务被投了 N 次"就是真实世界的样子。
     */
    public void sendOrder(String orderId, String body) {
        String messageId = UUID.randomUUID().toString();
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, body, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(messageId);
            message.getMessageProperties().setHeader(ORDER_ID_HEADER, orderId);
            return message;
        }, new CorrelationData(messageId));
        log.info("[Send   ] messageId={} orderId={} body={}", messageId, orderId, body);
    }

    /** 一次性把四种现场都造出来 */
    @Scheduled(initialDelay = 1000, fixedDelay = 20000)
    public void produce() {
        log.info("================ 幂等 demo 第 {} 轮 ================", ++round);

        // 现场一：同一笔订单连发 3 次（3 个不同 messageId）
        //   模拟：broker 重投 + 生产端超时重试叠加。应只处理第 1 次，另 2 次被业务键拦下。
        for (int i = 1; i <= 3; i++) {
            sendOrder("ORDER-1001", "订单 ORDER-1001 第 " + i + " 次投递");
        }

        // 现场二：另一笔订单正常处理一次
        sendOrder("ORDER-1002", "订单 ORDER-1002 正常支付");

        // 现场三：订单不存在 → 业务更新影响 0 行 → 抛异常 → 事务回滚
        //   关键看日志：占位随事务一起回滚，所以**不需要 release**。
        sendOrder("ORDER-9999", "订单 ORDER-9999 不存在，业务必然失败");

        // 现场四：把失败的订单再发一次（模拟人工补偿重投）
        //   因为上一次的占位已经回滚了，这次能重新进来 —— 仍然失败，但**它有重试的机会**。
        //   反过来想：如果占位没回滚（比如用了 Redis 却忘了 release），
        //   这条会被直接判成"重复消息"丢掉，补偿永远无效。这就是 release 为什么存在。
        sendOrder("ORDER-9999", "订单 ORDER-9999 补偿重投");

        // 想看表里的数据：http://localhost:8081/h2-console
        //   JDBC URL: jdbc:h2:mem:rabbitmq-lab（见 application-idempotency.yml）用户 sa 密码空
        //   SELECT * FROM dedup_record;  SELECT * FROM biz_order;
    }
}
