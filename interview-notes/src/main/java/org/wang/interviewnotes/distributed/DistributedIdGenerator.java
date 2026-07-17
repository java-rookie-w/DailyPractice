package org.wang.interviewnotes.distributed;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分布式ID生成面试题 - 九年经验高级工程师必备
 * 
 * 面试高频问题：
 * 1. 分布式ID有哪些生成方案？
 * 2. 雪花算法的原理和优缺点？
 * 3. 如何保证ID的全局唯一性？
 * 4. 如何处理时钟回拨问题？
 */
public class DistributedIdGenerator {

    // ============================================================
    // 1. UUID
    // ============================================================
    
    /**
     * UUID (Universally Unique Identifier)
     * 
     * 格式：UUID由32个十六进制数字组成，分为5段
     * 示例：550e8400-e29b-41d4-a716-446655440000
     * 
     * 特点：
     * - 全局唯一，不需要协调
     * - 无序，不适合做主键
     * - 长度36字符，占用空间大
     * 
     * 适用场景：非主键的唯一标识
     */
    static class UuidGenerator {
        
        public String generate() {
            return java.util.UUID.randomUUID().toString();
        }
        
        public void demonstrate() {
            System.out.println("\n【UUID】");
            System.out.println("  格式：32位十六进制数字，5段（8-4-4-4-12）");
            System.out.println("  示例：" + generate());
            System.out.println("  优点：简单、无序、全局唯一");
            System.out.println("  缺点：无序、长度36字符、不适合做主键");
            System.out.println("  适用：非主键的唯一标识（如请求ID）");
        }
    }

    // ============================================================
    // 2. 数据库自增ID
    // ============================================================
    
    /**
     * 数据库自增ID
     * 
     * 实现：使用数据库的AUTO_INCREMENT
     * 
     * 优点：
     * - 实现简单
     * - 有序递增
     * - 性能好（单库）
     * 
     * 缺点：
     * - 单点瓶颈
     * - 扩展性差
     * - 依赖数据库
     * 
     * 适用场景：单库、低并发场景
     */
    static class DatabaseAutoIncrement {
        private long id = 0;
        
        public synchronized long generate() {
            return ++id;
        }
        
        public void demonstrate() {
            System.out.println("\n【数据库自增ID】");
            System.out.println("  实现：AUTO_INCREMENT或序列");
            System.out.println("  示例ID: " + generate() + ", " + generate() + ", " + generate());
            System.out.println("  优点：简单、有序、性能好");
            System.out.println("  缺点：单点瓶颈、扩展性差");
            System.out.println("  适用：单库、低并发场景");
        }
    }

    // ============================================================
    // 3. 号段模式 (Segment)
    // ============================================================
    
    /**
     * 号段模式
     * 
     * 原理：每次从数据库批量获取一段ID，本地分配
     * 
     * 优点：
     * - 减少数据库访问
     * - 性能高
     * - 扩展性好
     * 
     * 缺点：
     * - ID不连续
     * - 需要预分配
     * 
     * 适用场景：中高并发场景（如美团Leaf）
     */
    static class SegmentIdGenerator {
        private final int segmentSize;
        private long currentId;
        private long maxId;
        private final AtomicInteger lock = new AtomicInteger(0);
        
        public SegmentIdGenerator(int segmentSize) {
            this.segmentSize = segmentSize;
            this.currentId = 0;
            this.maxId = 0;
        }
        
        /**
         * 生成ID
         */
        public long generate() {
            // 检查是否需要加载新号段
            if (currentId >= maxId) {
                loadSegment();
            }
            return currentId++;
        }
        
        /**
         * 加载新号段（模拟从数据库获取）
         */
        private synchronized void loadSegment() {
            // 双重检查
            if (currentId >= maxId) {
                // 实际从数据库获取：SELECT max_id FROM id_alloc WHERE biz_tag = ?
                long newMaxId = maxId + segmentSize;
                maxId = newMaxId;
                currentId = maxId - segmentSize + 1;
                
                System.out.println("  [号段模式] 加载新号段: " + (maxId - segmentSize + 1) + " ~ " + maxId);
            }
        }
        
        public void demonstrate() {
            System.out.println("\n【号段模式】");
            System.out.println("  原理：批量获取ID，本地分配");
            
            SegmentIdGenerator generator = new SegmentIdGenerator(100);
            for (int i = 0; i < 5; i++) {
                System.out.println("  生成ID: " + generator.generate());
            }
            
            System.out.println("  优点：减少DB访问、性能高、扩展性好");
            System.out.println("  缺点：ID不连续、需要预分配");
            System.out.println("  适用：中高并发（美团Leaf）");
        }
    }

    // ============================================================
    // 4. 雪花算法 (Snowflake)
    // ============================================================
    
    /**
     * 雪花算法 - Twitter开源
     * 
     * 结构（64位）：
     * - 1位符号位（固定0）
     * - 41位时间戳（毫秒级，可用69年）
     * - 10位机器ID（5位数据中心 + 5位工作机器）
     * - 12位序列号（毫秒内4096个ID）
     * 
     * 优点：
     * - 全局唯一
     * - 趋势递增
     * - 性能高（单机400万/秒）
     * - 不依赖外部存储
     * 
     * 缺点：
     * - 依赖时钟（时钟回拨问题）
     * - 机器ID需要手动分配
     * 
     * 适用场景：绝大多数场景（推荐）
     */
    static class SnowflakeIdGenerator {
        // 起始时间戳 (2024-01-01 00:00:00)
        private final long EPOCH = 1704067200000L;
        
        // 数据中心ID占5位
        private final long DATA_ID_BITS = 5L;
        private final long MAX_DATA_ID = ~(-1L << DATA_ID_BITS);
        
        // 工作机器ID占5位
        private final long WORKER_ID_BITS = 5L;
        private final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
        
        // 序列号占12位
        private final long SEQUENCE_BITS = 12L;
        private final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
        
        // 各部分的位移
        private final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        private final long DATA_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
        private final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_ID_BITS;
        
        private final long dataId;
        private final long workerId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;
        
        public SnowflakeIdGenerator(long dataId, long workerId) {
            if (dataId > MAX_DATA_ID || dataId < 0) {
                throw new IllegalArgumentException("Data ID 超出范围");
            }
            if (workerId > MAX_WORKER_ID || workerId < 0) {
                throw new IllegalArgumentException("Worker ID 超出范围");
            }
            this.dataId = dataId;
            this.workerId = workerId;
        }
        
        /**
         * 生成下一个ID
         */
        public synchronized long nextId() {
            long timestamp = currentTimeMillis();
            
            // 时钟回拨检查
            if (timestamp < lastTimestamp) {
                throw new RuntimeException("时钟回拨，拒绝生成ID");
            }
            
            if (timestamp == lastTimestamp) {
                // 同一毫秒内，序列号递增
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    // 序列号用完，等待下一毫秒
                    timestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }
            
            lastTimestamp = timestamp;
            
            // 组装ID
            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) |
                   (dataId << DATA_ID_SHIFT) |
                   (workerId << WORKER_ID_SHIFT) |
                   sequence;
        }
        
        /**
         * 等待下一毫秒
         */
        private long waitNextMillis(long lastTimestamp) {
            long timestamp = currentTimeMillis();
            while (timestamp <= lastTimestamp) {
                timestamp = currentTimeMillis();
            }
            return timestamp;
        }
        
        private long currentTimeMillis() {
            return System.currentTimeMillis();
        }
        
        /**
         * 解析ID
         */
        public static void parseId(long id) {
            long timestamp = (id >> 22) + 1704067200000L;
            long dataId = (id >> 17) & 0x1F;
            long workerId = (id >> 12) & 0x1F;
            long sequence = id & 0xFFF;
            
            System.out.println("  ID: " + id);
            System.out.println("  时间戳: " + new java.util.Date(timestamp));
            System.out.println("  数据中心: " + dataId);
            System.out.println("  工作机器: " + workerId);
            System.out.println("  序列号: " + sequence);
        }
        
        public void demonstrate() {
            System.out.println("\n【雪花算法 (Snowflake)】");
            System.out.println("  结构：1位符号 + 41位时间戳 + 5位数据中心 + 5位工作机器 + 12位序列号");
            
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
            for (int i = 0; i < 5; i++) {
                long id = generator.nextId();
                System.out.println("  生成ID: " + id);
            }
            
            System.out.println("  优点：全局唯一、趋势递增、性能高");
            System.out.println("  缺点：依赖时钟、机器ID需分配");
            System.out.println("  适用：绝大多数场景（推荐）");
        }
    }

    // ============================================================
    // 5. Redis生成ID
    // ============================================================
    
    /**
     * Redis INCR命令生成ID
     * 
     * 原理：利用Redis的INCR命令原子递增
     * 
     * 优点：
     * - 性能高
     * - 有序
     * - 分布式友好
     * 
     * 缺点：
     * - 依赖Redis
     * - 需要持久化
     * 
     * 适用场景：Redis可用的场景
     */
    static class RedisIdGenerator {
        private final AtomicLong redisCounter = new AtomicLong(0);
        
        /**
         * 生成ID（模拟Redis INCR）
         */
        public long generate() {
            // 实际使用: redisTemplate.opsForValue().increment("id:generator")
            return redisCounter.incrementAndGet();
        }
        
        /**
         * 批量生成ID（模拟Redis EVAL）
         */
        public long[] batchGenerate(int count) {
            long start = redisCounter.addAndGet(count);
            long[] ids = new long[count];
            for (int i = 0; i < count; i++) {
                ids[i] = start - count + i + 1;
            }
            return ids;
        }
        
        public void demonstrate() {
            System.out.println("\n【Redis生成ID】");
            System.out.println("  原理：Redis INCR命令原子递增");
            
            RedisIdGenerator generator = new RedisIdGenerator();
            for (int i = 0; i < 5; i++) {
                System.out.println("  生成ID: " + generator.generate());
            }
            
            System.out.println("  优点：性能高、有序、分布式友好");
            System.out.println("  缺点：依赖Redis、需要持久化");
            System.out.println("  适用：Redis可用的场景");
        }
    }

    // ============================================================
    // 6. Leaf - 美团开源
    // ============================================================
    
    /**
     * Leaf - 美团开源的分布式ID生成服务
     * 
     * 两种模式：
     * 1. Leaf-segment：号段模式
     * 2. Leaf-snowflake：雪花模式（改进版）
     * 
     * 特点：
     * - 双Buffer预加载
     * - 时钟回拨处理
     * - 号段动态调整
     * - 监控告警
     */
    static class LeafIdGenerator {
        
        public void demonstrate() {
            System.out.println("\n【Leaf - 美团开源】");
            System.out.println("  两种模式：");
            System.out.println("  1. Leaf-segment：号段模式，双Buffer预加载");
            System.out.println("  2. Leaf-snowflake：雪花模式，改进时钟回拨处理");
            System.out.println("  特点：高性能、高可用、监控完善");
            System.out.println("  适用：大规模分布式系统");
        }
    }

    // ============================================================
    // 方案对比和选择
    // ============================================================
    
    public static void compareSolutions() {
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║           分布式ID方案对比                       ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        System.out.println("\n  方案         | 唯一性 | 有序性 | 性能    | 依赖   | 适用场景");
        System.out.println("  -------------|--------|--------|---------|--------|----------");
        System.out.println("  UUID         | 完美   | 无序   | 极高    | 无     | 非主键标识");
        System.out.println("  数据库自增   | 单库   | 有序   | 中      | 数据库 | 单库低并发");
        System.out.println("  号段模式     | 集群   | 趋势   | 高      | 数据库 | 中高并发");
        System.out.println("  雪花算法     | 集群   | 趋势   | 极高    | 时钟   | 绝大多数");
        System.out.println("  Redis        | 集群   | 有序   | 高      | Redis  | Redis可用");
        System.out.println("  Leaf         | 集群   | 趋势   | 极高    | 可选   | 大规模");
        
        System.out.println("\n【选择建议】");
        System.out.println("  - 单库简单场景：数据库自增");
        System.out.println("  - 中高并发：号段模式（Leaf-segment）");
        System.out.println("  - 绝大多数场景：雪花算法（推荐）");
        System.out.println("  - 大规模系统：Leaf（美团）");
        System.out.println("  - 非主键标识：UUID");
    }

    // ============================================================
    // 演示和测试
    // ============================================================
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║      分布式ID生成面试题详解 - 高级工程师必备      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        // 各方案演示
        new UuidGenerator().demonstrate();
        new DatabaseAutoIncrement().demonstrate();
        new SegmentIdGenerator(100).demonstrate();
        new SnowflakeIdGenerator(1, 1).demonstrate();
        new RedisIdGenerator().demonstrate();
        new LeafIdGenerator().demonstrate();
        
        // 方案对比
        compareSolutions();
        
        // 雪花算法详解
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║           雪花算法面试详解                       ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        System.out.println("\n【Q1: 雪花算法的结构？】");
        System.out.println("  64位 = 1符号 + 41时间戳 + 5数据中心 + 5工作机器 + 12序列号");
        System.out.println("  - 41位时间戳：可用69年");
        System.out.println("  - 5位数据中心：32个数据中心");
        System.out.println("  - 5位工作机器：32个工作机器");
        System.out.println("  - 12位序列号：每毫秒4096个ID");
        
        System.out.println("\n【Q2: 如何处理时钟回拨？】");
        System.out.println("  1. 拒绝生成（简单粗暴）");
        System.out.println("  2. 等待时钟追上（小范围回拨）");
        System.out.println("  3. 使用扩展位（预留位）");
        System.out.println("  4. Leaf方案：记录上次时间戳，回拨时使用扩展位");
        
        System.out.println("\n【Q3: 如何保证全局唯一？】");
        System.out.println("  1. 时间戳 + 数据中心 + 工作机器 + 序列号 组合");
        System.out.println("  2. 同一毫秒内序列号递增");
        System.out.println("  3. 不同机器使用不同的数据中心和工作机器ID");
        
        System.out.println("\n【Q4: 如何部署多套环境？】");
        System.out.println("  方案1：不同环境使用不同数据中心ID");
        System.out.println("  方案2：不同环境使用不同工作机器ID段");
        System.out.println("  方案3：使用不同的时间戳起始点");
    }
}
