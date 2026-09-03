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
 *     msg_id VARCHAR(64) NOT NULL,
 *     biz_no VARCHAR(64) NOT NULL,          -- 冗余业务单号，排查用
 *     status TINYINT,                       -- 0 处理中 / 1 成功
 *     created_at DATETIME DEFAULT NOW(),
 *     UNIQUE KEY uk_msg_id (msg_id)
 *   );
 */
public class JdbcDedupStore implements DedupStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcDedupStore.class);

    private final JdbcTemplate jdbc;

    public JdbcDedupStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryMark(String msgId) {
        try {
            jdbc.update("INSERT INTO dedup_record (msg_id) VALUES (?)", msgId);
            return true;
        } catch (DuplicateKeyException e) {
            // 撞唯一索引 = 这个 id 已经有人占了 = 重复消息。这是"正常业务分支"，不是错误。
            return false;
        }
    }

    @Override
    public void release(String msgId) {
        int rows = jdbc.update("DELETE FROM dedup_record WHERE msg_id = ?", msgId);
        log.debug("[Dedup ] release msgId={} 删除 {} 行", msgId, rows);
    }
}
