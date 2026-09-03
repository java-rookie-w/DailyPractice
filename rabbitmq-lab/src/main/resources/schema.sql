-- idempotency demo 的幂等去重表。
-- Boot 对内嵌库（H2）默认执行本文件（spring.sql.init.mode=embedded），
-- 换成 MySQL 时把建表语句抄过去，并把 msg_id 上的约束换成 UNIQUE KEY —— 语义完全一致。
--
-- 面试口径：消费幂等 = 业务唯一键 + 唯一索引。
--   INSERT 成功 = 第一次消费（占住 key）
--   撞唯一索引  = 重复消息（直接 ack 丢弃）
--   业务失败    = DELETE 释放（release），否则补偿重投会被误杀

CREATE TABLE IF NOT EXISTS dedup_record (
    msg_id     VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
