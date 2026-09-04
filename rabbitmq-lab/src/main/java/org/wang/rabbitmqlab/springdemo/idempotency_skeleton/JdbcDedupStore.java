package org.wang.rabbitmqlab.springdemo.idempotency_skeleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MySQL「唯一索引幂等」的 H2 内存库版。【TODO 由你实现】
 * 表结构见 resources/schema.sql（启动时自动建好）：dedup_record(biz_key) + biz_order(order_id, status)。
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
    //     jdbc.update("INSERT INTO dedup_record (biz_key) VALUES (?)", bizKey);
    //     return true;                     // 插入成功 = 第一次
    // } catch (DuplicateKeyException e) {
    //     return false;                    // 撞唯一索引 = 重复
    // }
    //
    // ⚠️ 必须是"一条 INSERT + 捕获唯一键冲突"的一步原子判断，
    //    不能先 SELECT 再 INSERT（两步之间并发的另一条同 key 消息会漏判）。
    // ⚠️ 撞唯一索引是**正常业务分支**，别打 error 日志。
    @Override
    public boolean tryMark(String bizKey) {
        // TODO 1
         try {
             jdbc.update("INSERT INTO dedup_record (biz_key) VALUES (?)", bizKey);
             return true;                     // 插入成功 = 第一次
         } catch (DuplicateKeyException e) {
             return false;                    // 撞唯一索引 = 重复
         }
    }

    // ======== TODO 2：release ========
    // 写法：
    // jdbc.update("DELETE FROM dedup_record WHERE biz_key = ?", bizKey);
    //
    // 💡 本 demo 的"同库同事务"方案里其实用不到它（业务失败 = 事务回滚 = 占位消失）。
    //    保留这个方法是为了让你想清楚：换成 Redis / 跨库去重表时，
    //    业务失败不归还 key → 补偿重投的同 key 消息会被误杀，消息永久丢失。
    @Override
    public void release(String bizKey) {
        // TODO 2
        jdbc.update("DELETE FROM dedup_record WHERE biz_key = ?", bizKey);
    }
}
