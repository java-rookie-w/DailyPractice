package org.wang.rabbitmqlab.springdemo.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 幂等 + 业务，放在**同一个事务**里。【标准实现】
 *
 * 这是幂等方案从"能跑"到"生产可用"的分界线。很多人写的幂等只在业务外面套一层去重表，
 * 两张表不在同一个事务，于是出现两种都兜不住的情况：
 *   1. 业务成功、占位回滚  → 同一笔业务下次重投又被处理一遍（重复）
 *   2. 占位成功、业务失败  → 消息被 ack 掉，业务其实没做成（丢失）
 * 同一个事务里，这两条路都被堵死：要么都成，要么都回滚。
 *
 * 为什么单独拆一个 Service，而不是把 @Transactional 加在 IdemConsumer 的监听方法上：
 *   1. @Transactional 加在监听方法上 → ack 会在事务**提交之前**发出（ack 代码在方法体内），
 *      一旦提交失败回滚，就变成"消息已确认、业务没做"，消息永久丢失。
 *   2. 就算把 @Transactional 加在消费者类的另一个方法上也不行 ——
 *      同类内部自调用会**绕过代理**，事务根本不生效（Spring 最常见的坑之一）。
 *   所以：业务独立成一个 Bean，监听方法调它，事务提交后再 ack。
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

    /**
     * 一笔订单的"支付成功"处理。整个方法一个事务：占位 + 业务更新同生共死。
     *
     * @param bizKey 业务键（订单号 + 事件类型），不是 messageId
     */
    @Transactional
    public Result handle(String bizKey) {
        String orderId = bizKey;

        // ---- 1. 一步原子占位：false = 这条业务已经处理过 ----
        if (!dedupStore.tryMark(bizKey)) {
            log.warn("[Biz    ] 重复消息，业务跳过 bizKey={}", bizKey);
            return Result.DUPLICATE;
        }

        // ---- 2. 业务更新：状态机条件更新（幂等的第二层） ----
        //    WHERE status='UNPAID' 是"乐观锁 + 状态机"的合体：
        //    哪怕去重表被人清了、或有人绕过 MQ 直接重放，已支付的订单也不会被再处理一次。
        //    面试说法：幂等做两层 —— 去重表拦重复投递，状态机条件更新兜业务正确性。
        int rows = jdbc.update(
                "UPDATE biz_order SET status = 'PAID', updated_at = CURRENT_TIMESTAMP " +
                "WHERE order_id = ? AND status = 'UNPAID'", orderId);

        // ---- 3. 影响 0 行 = 订单不存在或状态已变更 → 抛异常让事务回滚 ----
        //    回滚后占位的 INSERT 一起消失，下次同一个 bizKey 还能重新进来
        //    （这就是"同事务"替代 release 的地方）。
        if (rows == 0) {
            throw new IllegalStateException("订单不存在或状态已变更，拒绝处理: " + orderId);
        }

        log.info("[Biz    ] 订单状态已更新 UNPAID -> PAID，orderId={}", orderId);
        return Result.PROCESSED;
    }
}
