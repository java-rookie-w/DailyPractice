-- idempotency demo 的两张表。
-- Boot 对内嵌库（H2）默认执行本文件（spring.sql.init.mode=embedded），
-- 换成 MySQL 时把建表语句抄过去，biz_key 上的 PRIMARY KEY 换成 UNIQUE KEY —— 语义完全一致。

-- ① 去重表
--    面试口径：消费幂等 = 业务唯一键 + 唯一索引。
--      INSERT 成功   = 第一次消费（占住 key）
--      撞唯一索引    = 重复消息（直接 ack 丢弃）
--    注意列名是 biz_key 而不是 msg_id —— 幂等键必须是**业务维度**的键（订单号 + 事件类型），
--    不能用 messageId：重发会产生新的 messageId，用它拦不住真正的重复（详见 IdemProducer 注释）。
CREATE TABLE IF NOT EXISTS dedup_record (
    biz_key    VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ② 业务表（订单）
--    这张表是幂等 demo 的关键：**去重表必须和业务表在同一个库、同一个事务里**。
--    反例：去重表放 Redis 或另一个库 → 业务成功但占位回滚（重复消费）/
--    占位成功但业务失败（消息丢失），两头都不兜底。
CREATE TABLE IF NOT EXISTS biz_order (
    order_id   VARCHAR(64) PRIMARY KEY,
    status     VARCHAR(16) NOT NULL,      -- UNPAID / PAID
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 内存库每次启动都是空的，直接预置两笔待支付订单（MySQL 上换成 INSERT IGNORE）
INSERT INTO biz_order (order_id, status) VALUES ('ORDER-1001', 'UNPAID');
INSERT INTO biz_order (order_id, status) VALUES ('ORDER-1002', 'UNPAID');
