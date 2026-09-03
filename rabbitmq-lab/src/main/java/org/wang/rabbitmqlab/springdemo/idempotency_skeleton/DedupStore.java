package org.wang.rabbitmqlab.springdemo.idempotency_skeleton;

/**
 * 幂等去重的存储契约（练习骨架直接给全 —— 它是"设计"，不是"实现"）。
 * 实现类看 JdbcDedupStore 里的 TODO。
 */
public interface DedupStore {

    /** 占位：true = 第一次；false = 重复。必须一步原子 */
    boolean tryMark(String msgId);

    /** 归还：业务失败时调用，让重试/补偿的消息能被重新处理 */
    void release(String msgId);
}
