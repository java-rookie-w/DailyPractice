package org.wang.rabbitmqlab.springdemo.idempotency;

/**
 * 幂等去重的存储契约：实现类只需要保证两件事。
 *
 * 1. tryMark 必须是「一步原子」的占位操作：
 *    返回 true = 第一次见到这个 key（占位成功）
 *    返回 false = 已经处理过（重复消息）
 *    绝对不能拆成"先查再插"两步 —— 并发下两步之间会漏判。
 * 2. 参数是 **bizKey（业务键：订单号 + 事件类型）**，不是 messageId。
 *    重发会产生新的 messageId，用它做幂等键拦不住真正的重复（详见 IdemProducer 注释）。
 *
 * 三个可替换实现（面试时按这个顺序说）：
 *   a) 内存版：ConcurrentHashMap.newKeySet() 的 add() 返回值 —— demo/单机够用，重启即失效
 *   b) Redis：SET key value NX EX ttl —— 分布式首选，天然带过期（但跨库，失败要 release）
 *   c) MySQL：INSERT 撞唯一索引 —— 本 demo 用 H2 内存库代替 MySQL，行为完全一致
 */
public interface DedupStore {

    /** 占位：true = 第一次；false = 重复。必须一步原子 */
    boolean tryMark(String bizKey);

    /**
     * 归还占位：让同一业务键的消息能被重新处理。
     *
     * ⚠️ 什么时候才需要它：**去重存储和业务存储不在同一个事务里**的时候。
     *    - Redis / 另一个库存去重表 → 业务失败必须手动 release，否则补偿重投被误杀；
     *    - 同库同事务（本 demo 的做法）→ 业务失败 = 事务回滚 = 占位自动消失，**不需要 release**。
     * 换句话说：一个方案需要 release，本身就说明它保证不了原子性 —— 这点面试时说出来很加分。
     */
    void release(String bizKey);
}
