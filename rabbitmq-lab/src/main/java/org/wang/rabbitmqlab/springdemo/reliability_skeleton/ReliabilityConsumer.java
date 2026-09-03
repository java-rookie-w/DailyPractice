package org.wang.rabbitmqlab.springdemo.reliability_skeleton;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消费端：负责「消息不丢」里消费者这一半 —— 手动 ACK + 幂等 + 失败进死信。
 *
 * 两个改动（相比你原来的写法）：
 *   1. @RabbitListener 从类上挪到方法上。
 *      @RabbitHandler 是为"一个类里按 payload 类型分派多个方法"设计的，
 *      这里只有一个方法，留着它只会让 Message/Channel 参数解析多绕一层。
 *   2. 参数从 String 换成 (Message, Channel)。
 *      手动 ACK 必须拿得到 deliveryTag 和 channel，只收 String 没法 ack。
 */
public class ReliabilityConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityConsumer.class);

    /**
     * 幂等去重表（内存版）。
     * 生产环境换成：Redis SETNX 或 MySQL 唯一索引 —— 关键要求是「一步原子」，
     * 不能先 get 判存在再 put，并发下两步之间会漏。
     * 本 demo 用 ConcurrentHashMap.newKeySet() 的 add() 返回值，同样是一步原子。
     */
    private final Set<String> processedIds = ConcurrentHashMap.newKeySet();

    /**
     * ackMode = "MANUAL" 写在这里（也可以只靠 yml 的全局配置）。
     * 写出来的好处：一眼可见，且只作用于这一个监听器。
     */
    @RabbitListener(queues = ReliabilityConfig.QUEUE, ackMode = "MANUAL")
    public void receive(Message message, Channel channel) throws IOException {
        // deliveryTag：这条消息在**当前 channel** 上的序号，ack/nack 全靠它
        long tag = message.getMessageProperties().getDeliveryTag();
        String msgId = message.getMessageProperties().getMessageId();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        log.info("[Consumer] 收到 tag={} msgId={} body={}", tag, msgId, body);

        try {
            // ========== TODO 1：幂等判断（重复消息直接丢弃并 ack） ==========
            // 为什么必须幂等：broker 只保证"至少一次"，网络抖动 / 消费者 ack 前宕机
            // 都会导致同一条消息被重投。业务必须能扛住重复。
            //
            // 写法：
             if (msgId != null && !processedIds.add(msgId)) {
                 log.warn("[Consumer] 重复消息，直接丢弃 tag={} msgId={}", tag, msgId);
                 channel.basicAck(tag, false);
                 return;
             }
            //
            // ⚠️ add() 本身就是"不存在则插入并返回 true"，一步原子，别拆成 contains + add。

            // ========== TODO 2：业务处理 ==========
            // 约定：body 含 "FAIL" 就抛运行时异常，模拟业务失败（对应 Producer 的 sendFail）
            //
            // 写法：
             if (body.contains("FAIL")) {
                 throw new RuntimeException("模拟业务失败: " + body);
             }
             log.info("[Consumer] 业务处理完成 body={}", body);

            // ========== TODO 3：成功 -> 手动 ack ==========
            // 第二个参数 multiple=false：只确认这一条。
            // 批量 true 会把"当前 tag 及之前所有未确认消息"一起确认，用错会误确认别人的消息。
            //
            // 写法：
             channel.basicAck(tag, false);

            // TODO 1~3 写完之前，让编译通过：
//            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("[Consumer] 处理失败 tag={} msgId={} err={}", tag, msgId, e.getMessage());

            // ========== TODO 4：失败 -> nack，不回队，交给死信 ==========
            // 三个参数：tag、multiple=false、requeue=false
            //   requeue=false → 不回原队列，按队列上的 x-dead-letter-exchange 进死信队列
            //
            // ⚠️ 两个坑：
            //   1) requeue=true 是另一个经典事故：业务永远失败 → 无限回队 → 死循环打满 CPU。
            //      正确做法是 requeue=false + 死信队列兜底 + 人工/定时补偿。
            //   2) 失败时必须把 TODO 1 加进去的 msgId **移除**（release）。
            //      否则死信补偿重投时会被幂等表当成"重复消息"直接吞掉，消息等于永久丢弃。
            //      processedIds.remove(msgId);
            //
            // 写法：
//             processedIds.remove(msgId);
//             channel.basicNack(tag, false, false);

            // TODO 4 写完之前，让编译通过：
            processedIds.remove(msgId);
            channel.basicNack(tag, false, false);
        }
    }
}
