package org.wang.rabbitmqlab.springdemo.idempotency_skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MySQL「唯一索引幂等」的 H2 内存库版。【TODO 由你实现】
 * 表结构见 resources/schema.sql（启动时自动建好）。
 */
public class JdbcDedupStore implements DedupStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcDedupStore.class);

    private final JdbcTemplate jdbc;

    public JdbcDedupStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ======== TODO 1：tryMark ========
    // 写法：
    // try {
    //     jdbc.update("INSERT INTO dedup_record (msg_id) VALUES (?)", msgId);
    //     return true;                     // 插入成功 = 第一次
    // } catch (DuplicateKeyException e) {
    //     return false;                    // 撞唯一索引 = 重复
    // }
    //
    // ⚠️ 必须是"一条 INSERT + 捕获唯一键冲突"的一步原子判断，
    //    不能先 SELECT 再 INSERT（两步之间并发的另一条同 id 消息会漏判）。
    @Override
    public boolean tryMark(String msgId) {
        // TODO 1
        return false;
    }

    // ======== TODO 2：release ========
    // 写法：
    // jdbc.update("DELETE FROM dedup_record WHERE msg_id = ?", msgId);
    //
    // ⚠️ release 是幂等设计里最容易漏的一步：
    //    业务失败不归还 id → 补偿重投的同 id 消息会被误杀成"重复"，消息等于永久丢失。
    @Override
    public void release(String msgId) {
        // TODO 2
    }
}
