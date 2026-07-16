package org.wang.interviewnotes.distributed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 多级缓存设计面试题 - 九年经验高级工程师必备
 * 
 * 面试高频问题：
 * 1. 多级缓存的架构是怎样的？
 * 2. 如何保证缓存一致性？
 * 3. 如何处理缓存穿透、击穿、雪崩？
 * 4. 缓存预热、更新策略如何设计？
 */
public class MultiLevelCache {

    // ============================================================
    // 1. 本地缓存（L1 Cache）
    // ============================================================
    
    /**
     * LRU缓存实现（基于LinkedHashMap）
     * 
     * 面试考点：
     * - LRU、LFU、FIFO的区别？
     * - 如何实现线程安全的LRU？
     */
    static class LruCache<K, V> {
        private final int maxSize;
        private final long expireTimeMs;
        private final LinkedHashMap<K, CacheEntry<V>> cache;
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final AtomicInteger hitCount = new AtomicInteger(0);
        private final AtomicInteger missCount = new AtomicInteger(0);
        
        static class CacheEntry<V> {
            V value;
            long expireTime;
            
            CacheEntry(V value, long expireTimeMs) {
                this.value = value;
                this.expireTime = System.currentTimeMillis() + expireTimeMs;
            }
            
            boolean isExpired() {
                return System.currentTimeMillis() > expireTime;
            }
        }
        
        public LruCache(int maxSize, long expireTimeMs) {
            this.maxSize = maxSize;
            this.expireTimeMs = expireTimeMs;
            this.cache = new LinkedHashMap<K, CacheEntry<V>>(maxSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<K, CacheEntry<V>> eldest) {
                    return size() > LruCache.this.maxSize;
                }
            };
        }
        
        public V get(K key) {
            lock.readLock().lock();
            try {
                CacheEntry<V> entry = cache.get(key);
                if (entry != null && !entry.isExpired()) {
                    hitCount.incrementAndGet();
                    return entry.value;
                }
                missCount.incrementAndGet();
                return null;
            } finally {
                lock.readLock().unlock();
            }
        }
        
        public void put(K key, V value) {
            lock.writeLock().lock();
            try {
                cache.put(key, new CacheEntry<>(value, expireTimeMs));
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        public void remove(K key) {
            lock.writeLock().lock();
            try {
                cache.remove(key);
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        public double getHitRate() {
            int total = hitCount.get() + missCount.get();
            return total == 0 ? 0 : (double) hitCount.get() / total;
        }
        
        public int size() {
            return cache.size();
        }
    }

    // ============================================================
    // 2. Redis缓存（L2 Cache）
    // ============================================================
    
    /**
     * Redis缓存操作模拟
     * 
     * 面试考点：
     * - Redis数据类型选择？
     * - 如何设置过期时间？
     * - 如何处理大Key问题？
     */
    static class RedisCache {
        private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
        
        static class CacheEntry {
            Object value;
            long expireTime;
            long createTime;
            
            CacheEntry(Object value, long ttlSeconds) {
                this.value = value;
                this.createTime = System.currentTimeMillis();
                this.expireTime = this.createTime + ttlSeconds * 1000;
            }
            
            boolean isExpired() {
                return System.currentTimeMillis() > expireTime;
            }
            
            long getTtl() {
                long remaining = expireTime - System.currentTimeMillis();
                return remaining > 0 ? remaining / 1000 : 0;
            }
        }
        
        public Object get(String key) {
            CacheEntry entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.value;
            }
            if (entry != null) {
                cache.remove(key); // 惰性删除
            }
            return null;
        }
        
        public void set(String key, Object value, long ttlSeconds) {
            cache.put(key, new CacheEntry(value, ttlSeconds));
        }
        
        public boolean delete(String key) {
            return cache.remove(key) != null;
        }
        
        public boolean exists(String key) {
            CacheEntry entry = cache.get(key);
            return entry != null && !entry.isExpired();
        }
        
        public long getTtl(String key) {
            CacheEntry entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.getTtl();
            }
            return -1;
        }
        
        /**
         * 模拟Redis Lua脚本 - 原子操作
         */
        public boolean setIfAbsent(String key, Object value, long ttlSeconds) {
            return cache.putIfAbsent(key, new CacheEntry(value, ttlSeconds)) == null;
        }
        
        public int size() {
            return cache.size();
        }
    }

    // ============================================================
    // 3. 多级缓存管理器
    // ============================================================
    
    /**
     * 多级缓存架构
     * 
     * 架构：
     * 客户端 → 本地缓存(L1) → Redis缓存(L2) → 数据库(DB)
     * 
     * 面试考点：
     * - 缓存一致性如何保证？
     * - 缓存穿透、击穿、雪崩如何处理？
     * - 缓存预热策略？
     */
    static class MultiLevelCacheManager {
        private final LruCache<String, Object> localCache;
        private final RedisCache redisCache;
        private final ConcurrentHashMap<String, Boolean> bloomFilter; // 布隆过滤器模拟
        
        public MultiLevelCacheManager(int localCacheSize, long localExpireMs) {
            this.localCache = new LruCache<>(localCacheSize, localExpireMs);
            this.redisCache = new RedisCache();
            this.bloomFilter = new ConcurrentHashMap<>();
        }
        
        /**
         * 获取数据（多级缓存查询）
         */
        public Object get(String key, DataLoader loader) {
            // 1. 查本地缓存
            Object value = localCache.get(key);
            if (value != null) {
                System.out.println("[缓存] L1命中: " + key);
                return value;
            }
            
            // 2. 查Redis缓存
            value = redisCache.get(key);
            if (value != null) {
                System.out.println("[缓存] L2命中，回填L1: " + key);
                localCache.put(key, value);
                return value;
            }
            
            // 3. 查数据库（带缓存击穿保护）
            System.out.println("[缓存] 缓存未命中，查数据库: " + key);
            return loadFromDbWithLock(key, loader);
        }
        
        /**
         * 带分布式锁的数据库查询（防止缓存击穿）
         */
        private Object loadFromDbWithLock(String key, DataLoader loader) {
            // 使用setIfAbsent模拟分布式锁
            String lockKey = "lock:" + key;
            if (redisCache.setIfAbsent(lockKey, "1", 10)) {
                try {
                    // 查询数据库
                    Object value = loader.load(key);
                    
                    if (value != null) {
                        // 写入Redis（设置较短过期，防止数据不一致）
                        redisCache.set(key, value, 300);
                        // 写入本地缓存
                        localCache.put(key, value);
                        System.out.println("[缓存] 数据已缓存: " + key);
                    } else {
                        // 缓存空值，防止穿透
                        redisCache.set(key, "NULL", 60);
                        System.out.println("[缓存] 空值已缓存: " + key);
                    }
                    
                    return value;
                } finally {
                    redisCache.delete(lockKey);
                }
            } else {
                // 其他线程正在加载，等待后重试
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return get(key, loader); // 重试
            }
        }
        
        /**
         * 更新数据（缓存一致性策略）
         */
        public void update(String key, Object value) {
            // 1. 更新数据库（模拟）
            System.out.println("[缓存] 更新数据库: " + key);
            
            // 2. 删除Redis缓存（而非更新，避免并发问题）
            redisCache.delete(key);
            System.out.println("[缓存] 删除L2缓存: " + key);
            
            // 3. 删除本地缓存
            localCache.remove(key);
            System.out.println("[缓存] 删除L1缓存: " + key);
            
            // 4. 发布缓存失效消息（通知其他节点）
            publishCacheInvalidate(key);
        }
        
        /**
         * 缓存预热
         */
        public void warmUp(String key, DataLoader loader) {
            System.out.println("[缓存预热] 加载: " + key);
            Object value = loader.load(key);
            if (value != null) {
                redisCache.set(key, value, 3600);
                localCache.put(key, value);
            }
        }
        
        /**
         * 发布缓存失效消息（模拟Redis Pub/Sub）
         */
        private void publishCacheInvalidate(String key) {
            System.out.println("[缓存] 发布失效消息: " + key);
            // 实际使用: redisTemplate.convertAndSend("cache:invalidate", key);
        }
        
        /**
         * 布隆过滤器检查（防止缓存穿透）
         */
        public boolean mightExist(String key) {
            return bloomFilter.containsKey(key);
        }
        
        public void addToBloomFilter(String key) {
            bloomFilter.put(key, Boolean.TRUE);
        }
        
        public int getLocalCacheSize() {
            return localCache.size();
        }
        
        public int getRedisCacheSize() {
            return redisCache.size();
        }
        
        public double getLocalHitRate() {
            return localCache.getHitRate();
        }
    }
    
    /**
     * 数据加载器接口
     */
    interface DataLoader {
        Object load(String key);
    }

    // ============================================================
    // 4. 缓存穿透、击穿、雪崩解决方案
    // ============================================================
    
    /**
     * 缓存问题解决方案
     */
    static class CacheProblemSolver {
        private final MultiLevelCacheManager cacheManager;
        
        public CacheProblemSolver(MultiLevelCacheManager cacheManager) {
            this.cacheManager = cacheManager;
        }
        
        /**
         * 缓存穿透解决方案
         * 
         * 问题：查询不存在的数据，每次都打到数据库
         * 
         * 解决方案：
         * 1. 布隆过滤器：快速判断key是否存在
         * 2. 缓存空值：将null结果也缓存，设置较短过期时间
         * 3. 参数校验：拦截非法请求
         */
        public Object solvePenetration(String key, DataLoader loader) {
            // 1. 布隆过滤器检查
            if (!cacheManager.mightExist(key)) {
                System.out.println("[穿透防护] 布隆过滤器拦截: " + key);
                return null;
            }
            
            // 2. 查询缓存（已处理空值缓存）
            return cacheManager.get(key, loader);
        }
        
        /**
         * 缓存击穿解决方案
         * 
         * 问题：热点key过期，大量并发请求打到数据库
         * 
         * 解决方案：
         * 1. 互斥锁：只允许一个线程重建缓存
         * 2. 逻辑过期：不设置TTL，使用逻辑过期时间
         * 3. 永不过期：热点key不设过期
         */
        public Object solveBreakdown(String key, DataLoader loader) {
            // 使用分布式锁（已在MultiLevelCacheManager.get中实现）
            return cacheManager.get(key, loader);
        }
        
        /**
         * 缓存雪崩解决方案
         * 
         * 问题：大量key同时过期，或Redis宕机，请求全部打到数据库
         * 
         * 解决方案：
         * 1. 过期时间加随机值：避免同时过期
         * 2. 多级缓存：本地缓存兜底
         * 3. 限流降级：保护数据库
         * 4. Redis集群：高可用
         */
        public void solveAvalanche(String key, Object value) {
            // 1. 过期时间加随机值
            long baseTtl = 300; // 5分钟
            long randomTtl = baseTtl + ThreadLocalRandom.current().nextInt(60); // +0~60秒
            System.out.println("[雪崩防护] 随机过期时间: " + randomTtl + "秒");
            
            // 2. 多级缓存存储（已在update中实现）
            cacheManager.update(key, value);
        }
        
        /**
         * 热点数据永不过期
         */
        public void setHotDataNeverExpire(String key, Object value) {
            // 实际实现：不设置TTL，通过后台任务定期刷新
            System.out.println("[热点数据] 设置永不过期: " + key);
            // redis.opsForValue().set(key, value); // 不设置过期时间
        }
        
        /**
         * 逻辑过期时间（不使用TTL）
         */
        public void setWithLogicalExpire(String key, Object value, long expireSeconds) {
            // 实际实现：将过期时间作为value的一部分存储
            // Map<String, Object> data = new HashMap<>();
            // data.put("value", value);
            // data.put("expireTime", System.currentTimeMillis() + expireSeconds * 1000);
            // redis.opsForValue().set(key, data);
            System.out.println("[逻辑过期] 设置: " + key + ", 过期时间: " + expireSeconds + "秒");
        }
    }

    // ============================================================
    // 5. 缓存一致性方案
    // ============================================================
    
    /**
     * 缓存一致性解决方案
     * 
     * 面试考点：
     * - Cache Aside Pattern详解
     * - 如何保证最终一致性？
     * - 延迟双删的原理？
     */
    static class CacheConsistency {
        
        /**
         * Cache Aside Pattern（旁路缓存）
         * 
         * 读流程：
         * 1. 读缓存，命中则返回
         * 2. 未命中，查数据库
         * 3. 写入缓存
         * 
         * 写流程：
         * 1. 更新数据库
         * 2. 删除缓存（而非更新缓存）
         * 
         * 为什么删除而不是更新缓存？
         * - 避免并发写导致的数据不一致
         * - 减少缓存计算开销
         */
        public void cacheAsidePattern(String key, Object newValue) {
            System.out.println("[Cache Aside] 更新流程:");
            System.out.println("  1. 更新数据库");
            // updateDb(key, newValue);
            
            System.out.println("  2. 删除缓存");
            // redis.delete(key);
            
            System.out.println("  为什么删除而不是更新？");
            System.out.println("  - 避免并发写导致数据不一致");
            System.out.println("  - 减少无意义的缓存计算");
        }
        
        /**
         * 延迟双删策略
         * 
         * 流程：
         * 1. 先删除缓存
         * 2. 更新数据库
         * 3. 延迟一段时间后，再次删除缓存
         * 
         * 为什么需要延迟双删？
         * - 解决主从延迟导致的不一致
         * - 第一次删除：清理旧缓存
         * - 第二次删除：清理主从同步期间写入的脏缓存
         */
        public void delayedDoubleDelete(String key, Object newValue) {
            System.out.println("[延迟双删] 流程:");
            
            System.out.println("  1. 删除缓存");
            // redis.delete(key);
            
            System.out.println("  2. 更新数据库");
            // updateDb(key, newValue);
            
            System.out.println("  3. 延迟删除（等待主从同步）");
            // scheduledExecutor.schedule(() -> redis.delete(key), 500, MILLISECONDS);
            
            System.out.println("  延迟时间：通常500ms，根据主从延迟调整");
        }
        
        /**
         * 订阅Binlog方案（Canal + MQ）
         * 
         * 流程：
         * 1. 更新数据库
         * 2. Canal监听Binlog变更
         * 3. 发送MQ消息
         * 4. 消费者更新/删除缓存
         * 
         * 优点：
         * - 与业务代码解耦
         * - 可靠性高
         * - 支持复杂场景
         */
        public void binlogSubscription(String key) {
            System.out.println("[Binlog订阅] 流程:");
            System.out.println("  1. 更新数据库（产生Binlog）");
            System.out.println("  2. Canal监听Binlog变更");
            System.out.println("  3. 发送MQ消息");
            System.out.println("  4. 消费者处理缓存更新");
            System.out.println("  优点：与业务解耦、可靠性高");
        }
        
        /**
         * 最终一致性保证
         */
        public void eventualConsistency(String key, Object value) {
            System.out.println("[最终一致性] 策略:");
            System.out.println("  1. 设置较短的缓存过期时间（如30秒）");
            System.out.println("  2. 使用消息队列异步更新缓存");
            System.out.println("  3. 定时任务校验一致性");
            System.out.println("  4. 监控告警，人工补偿");
        }
    }

    // ============================================================
    // 演示和测试
    // ============================================================
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║      多级缓存设计面试题详解 - 高级工程师必备      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        // 创建多级缓存管理器
        MultiLevelCacheManager cacheManager = new MultiLevelCacheManager(100, 60000);
        CacheProblemSolver problemSolver = new CacheProblemSolver(cacheManager);
        CacheConsistency consistency = new CacheConsistency();
        
        // 1. 多级缓存演示
        System.out.println("\n【1. 多级缓存查询演示】");
        DataLoader loader = key -> {
            System.out.println("  [数据库] 查询: " + key);
            return "value_" + key;
        };
        
        // 第一次查询（未命中）
        Object value = cacheManager.get("user:1001", loader);
        System.out.println("  结果: " + value);
        
        // 第二次查询（L1命中）
        value = cacheManager.get("user:1001", loader);
        System.out.println("  结果: " + value);
        
        System.out.println("  L1缓存大小: " + cacheManager.getLocalCacheSize());
        System.out.println("  L2缓存大小: " + cacheManager.getRedisCacheSize());
        System.out.println("  L1命中率: " + String.format("%.2f%%", cacheManager.getLocalHitRate() * 100));
        
        // 2. 缓存穿透演示
        System.out.println("\n【2. 缓存穿透解决方案】");
        // 模拟布隆过滤器
        for (int i = 1; i <= 1000; i++) {
            cacheManager.addToBloomFilter("user:" + i);
        }
        System.out.println("  布隆过滤器已添加1000个用户");
        
        // 查询不存在的用户
        value = problemSolver.solvePenetration("user:9999", loader);
        System.out.println("  查询不存在的用户: " + value);
        
        // 3. 缓存击穿演示
        System.out.println("\n【3. 缓存击穿解决方案】");
        System.out.println("  解决方案：互斥锁 + 逻辑过期");
        
        // 4. 缓存雪崩演示
        System.out.println("\n【4. 缓存雪崩解决方案】");
        problemSolver.solveAvalanche("user:1001", "value_1001");
        
        // 5. 缓存一致性演示
        System.out.println("\n【5. 缓存一致性方案】");
        consistency.cacheAsidePattern("user:1001", "new_value");
        consistency.delayedDoubleDelete("user:1001", "new_value");
        consistency.binlogSubscription("user:1001");
        consistency.eventualConsistency("user:1001", "new_value");
        
        // 面试总结
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║           多级缓存设计面试总结                   ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        System.out.println("\n【缓存架构】");
        System.out.println("  客户端 → 本地缓存(L1, Caffeine/Guava) → Redis(L2) → 数据库");
        System.out.println("  L1：容量小、速度快、JVM内");
        System.out.println("  L2：容量大、支持分布式、持久化");
        
        System.out.println("\n【缓存问题对比】");
        System.out.println("  问题     | 原因                | 解决方案");
        System.out.println("  ---------|---------------------|------------------");
        System.out.println("  穿透     | 查询不存在的数据    | 布隆过滤器+缓存空值");
        System.out.println("  击穿     | 热点key过期         | 互斥锁+逻辑过期");
        System.out.println("  雪崩     | 大量key同时过期     | 随机过期+多级缓存");
        
        System.out.println("\n【缓存一致性方案选择】");
        System.out.println("  方案              | 一致性 | 复杂度 | 适用场景");
        System.out.println("  ------------------|--------|--------|----------");
        System.out.println("  Cache Aside       | 最终   | 低     | 大多数场景");
        System.out.println("  延迟双删          | 最终   | 中     | 主从延迟场景");
        System.out.println("  Binlog订阅        | 强     | 高     | 核心业务");
        
        System.out.println("\n【面试高频问题】");
        System.out.println("  Q1: 为什么删除缓存而不是更新缓存？");
        System.out.println("      A: 避免并发写导致的数据不一致，减少无意义计算");
        System.out.println("  Q2: 如何保证缓存与数据库的强一致性？");
        System.out.println("      A: 使用Binlog订阅(Canal) + 消息队列");
        System.out.println("  Q3: 热点key如何处理？");
        System.out.println("      A: 本地缓存 + 永不过期 + 异步刷新");
    }
}
