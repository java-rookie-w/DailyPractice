package org.wang.rabbitmqlab.springdemo.idempotency_skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 【练习版】业务 Service：占位 + 业务更新，必须在**同一个事务**里。
 *
 * 为什么单独拆一个 Service，而不是把 @Transactional 加在监听方法上：
 *   1. 事务包住 ack → ack 先发出、提交才失败 → "消息已确认、业务没做" = 永久丢消息；
 *   2. 加在消费者类的另一个方法上也没用 —— 同类自调用绕过代理，事务不生效。
 */
public class IdemOrderService {

    private static final Logger log = LoggerFactory.getLogger(IdemOrderService.class);

    /** 处理结果：消费端据此决定日志和后续动作（无论哪种都要 ack） */
    public enum Result {
        /** 第一次处理，业务已执行 */
        PROCESSED,
        /** 重复消息，业务跳过 */
        DUPLICATE
    }

    private final JdbcTemplate jdbc;
    private final DedupStore dedupStore;

    public IdemOrderService(JdbcTemplate jdbc, DedupStore dedupStore) {
        this.jdbc = jdbc;
        this.dedupStore = dedupStore;
    }

    // ======== TODO 1：给 handle 加 @Transactional ========
    // 没有它，占位和业务更新就是两个独立事务，重复消费和消息丢失都堵不住。
    @Transactional
    public Result handle(String bizKey) {
        // ======== TODO 2：一步原子占位；false → 返回 DUPLICATE ========
        if (!dedupStore.tryMark(bizKey)) {
            log.warn("[Biz    ] 重复消息，业务跳过 bizKey={}", bizKey);
            return Result.DUPLICATE;
        }

        // ======== TODO 3：状态机条件更新（幂等第二层） ========
        // UPDATE biz_order SET status='PAID', updated_at=CURRENT_TIMESTAMP
        //  WHERE order_id = ? AND status = 'UNPAID'
        // 带上 status 条件，就算去重表被清了，已支付的订单也不会被再处理一次。
        int rows = jdbc.update(
                "UPDATE biz_order SET status = 'PAID', updated_at = CURRENT_TIMESTAMP " +
                "WHERE order_id = ? AND status = 'UNPAID'", bizKey);

        // ======== TODO 4：影响 0 行 → 抛异常，让事务回滚 ========
        // 回滚后占位的 INSERT 一起消失，同一个 bizKey 下次还能进来（这就是"同事务"替代 release 的地方）
        if (rows == 0) {
            throw new IllegalStateException("订单不存在或状态已变更，拒绝处理: " + bizKey);
        }

        log.info("[Biz    ] 订单状态已更新 UNPAID -> PAID，orderId={}", bizKey);
        return Result.PROCESSED;
    }
}
