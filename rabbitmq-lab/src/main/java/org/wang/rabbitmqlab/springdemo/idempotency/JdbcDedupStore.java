package org.wang.rabbitmqlab.springdemo.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MySQL「唯一索引幂等」方案的 H2 内存库版本，行为完全一致：
 *   INSERT 成功            = 第一次消费（tryMark 返回 true）
 *   撞 PRIMARY KEY / 唯一索引 = 重复消息（DuplicateKeyException → false）
 *
 * 为什么不用 "INSERT IGNORE" / "ON DUPLICATE KEY"：
 *   靠异常判断"是否第一次"在 MySQL 上是最直白的写法，而且和唯一索引的语义一一对应，
 *   面试官一听就知道你真的写过。H2 会抛 DuplicateKeyException（Spring 统一翻译的异常体系）。
 *
 * 真实生产的表一般是：
 *   CREATE TABLE dedup_record (
 *     biz_key  VARCHAR(64) NOT NULL,        -- 业务键：订单号 + 事件类型
 *     biz_no   VARCHAR(64) NOT NULL,        -- 冗余业务单号，排查用
 *     status   TINYINT,                     -- 0 处理中 / 1 成功
 *     created_at DATETIME DEFAULT NOW(),
 *     UNIQUE KEY uk_biz_key (biz_key)
 *   );
 *
 * ⚠️ 前提：**这张表必须和业务表在同一个库**，这样占位和业务更新才能进同一个事务
 *    （见 IdemOrderService）。分库放就没法原子了。
 */
public class JdbcDedupStore implements DedupStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcDedupStore.class);

    private final JdbcTemplate jdbc;

    public JdbcDedupStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 一步原子占位：INSERT 成功 = 第一次；撞唯一索引 = 重复。
     * 撞唯一索引是**正常业务分支**，不是错误，不要打 error 日志。
     */
    @Override
    public boolean tryMark(String bizKey) {
        try {
            jdbc.update("INSERT INTO dedup_record (biz_key) VALUES (?)", bizKey);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /**
     * 归还占位。同库同事务的方案里用不到（失败靠回滚），
     * 保留在这里是为了对照 Redis / 跨库方案 —— 那两种必须调它。
     */
    @Override
    public void release(String bizKey) {
        int rows = jdbc.update("DELETE FROM dedup_record WHERE biz_key = ?", bizKey);
        log.debug("[Dedup ] release bizKey={} 删除 {} 行", bizKey, rows);
    }
}
