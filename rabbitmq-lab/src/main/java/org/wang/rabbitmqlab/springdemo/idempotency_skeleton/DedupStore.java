package org.wang.rabbitmqlab.springdemo.idempotency_skeleton;

/**
 * 幂等去重的存储契约（练习骨架直接给全 —— 它是"设计"，不是"实现"）。
 * 实现类看 JdbcDedupStore 里的 TODO。
 *
 * ⚠️ 参数是 bizKey（业务键），不是 messageId：重发会生成新的 messageId，拦不住重复。
 */
public interface DedupStore {

    /** 占位：true = 第一次；false = 重复。必须一步原子 */
    boolean tryMark(String bizKey);

    /**
     * 归还占位。同库同事务的方案用不到（失败靠回滚），
     * 只有 Redis / 跨库去重表才必须调它 —— 那种方案本身保证不了原子性。
     */
    void release(String bizKey);
}
