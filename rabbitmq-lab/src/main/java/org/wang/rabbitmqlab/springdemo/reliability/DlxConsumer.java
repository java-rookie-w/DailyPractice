package org.wang.rabbitmqlab.springdemo.reliability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.nio.charset.StandardCharsets;

/**
 * 死信消费者：兜底 + 补偿。【标准实现】
 *
 * ⚠️ 一个容易踩的连带坑：
 * application-reliability.yml 里把 acknowledge-mode 全局设成了 manual，
 * 这个配置对**所有** @RabbitListener 生效，死信消费者也不例外。
 * 所以这里要么像下面这样显式写 ackMode = "AUTO"，要么也手动 ack，
 * 否则死信消息会一直处于 Unacked，看着像"消费了"其实没确认。
 */
public class DlxConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlxConsumer.class);

    @RabbitListener(queues = ReliabilityConfig.DLX_QUEUE, ackMode = "AUTO")
    public void receive(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String msgId = message.getMessageProperties().getMessageId();

        // x-death 是 broker 塞进消息头的一个 List<Map>，每一层死信会追加一项，包含：
        //   queue  —— 它原来在哪个队列
        //   reason —— rejected（被 nack）/ expired（TTL 到期）/ maxlen（队列超长）
        //   count  —— 被死信了几次
        //   time   —— 第一次死信的时间
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");

        log.warn("[DLX    ] 收到死信 msgId={} body={}", msgId, body);
        log.warn("[DLX    ] x-death={}", xDeath);

        // ===== 补偿动作（demo 阶段打印就算完成，生产上三板斧）=====
        //   a) 落库：写一张 t_dead_letter 表，字段含 msgId / body / x-death / 处理状态
        //   b) 告警：连续 N 条死信就发钉钉/企微，说明下游挂了而不是偶发脏数据
        //   c) 重试：定时扫表重投（必须带最大次数 + 指数退避，避免又打爆下游）
        //
        // ⚠️ 别在这里直接 requeue 回原队列 —— 那就是无限循环现场。
    }
}
