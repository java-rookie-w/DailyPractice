package org.wang.rabbitmqlab.springdemo.idempotency;

/**
 * 幂等去重的存储契约：实现类只需要保证两件事。
 *
 * 1. tryMark 必须是「一步原子」的占位操作：
 *    返回 true = 第一次见到这个 id（占位成功）
 *    返回 false = 已经处理过（重复消息）
 *    绝对不能拆成"先查再插"两步 —— 并发下两步之间会漏判。
 * 2. 业务失败必须调 release 归还 id，否则补偿重投会被误杀成"重复消息"。
 *
 * 三个可替换实现（面试时按这个顺序说）：
 *   a) 内存版：ConcurrentHashMap.newKeySet() 的 add() 返回值 —— demo/单机够用，重启即失效
 *   b) Redis：SET key value NX EX ttl —— 分布式首选，天然带过期
 *   c) MySQL：INSERT 撞唯一索引 —— 本 demo 用 H2 内存库代替 MySQL，行为完全一致
 */
public interface DedupStore {

    /** 占位：true = 第一次；false = 重复 */
    boolean tryMark(String msgId);

    /** 归还：业务失败时调用，让重试/补偿的消息能被重新处理 */
    void release(String msgId);
}
