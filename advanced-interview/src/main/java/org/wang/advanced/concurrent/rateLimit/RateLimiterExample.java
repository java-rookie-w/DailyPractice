package org.wang.advanced.concurrent.rateLimit;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流算法面试题 - 九年经验高级工程师必备
 * 
 * 面试高频问题：
 * 1. 令牌桶和漏桶算法的区别？
 * 2. 滑动窗口如何实现？
 * 3. 如何实现分布式限流？
 * 4. 限流算法的选择策略？
 */
public class RateLimiterExample {

    // ============================================================
    // 1. 令牌桶算法 (Token Bucket)
    // ============================================================
    
    /**
     * 令牌桶算法：以固定速率向桶中添加令牌，请求需要获取令牌才能通过
     * 
     * 特点：
     * - 允许突发流量（桶中有令牌时可以快速消费）
     * - 平滑限流（令牌以固定速率添加）
     * - 广泛应用于API网关、微服务限流
     * 
     * 面试考点：
     * - 如何处理突发流量？
     * - 令牌桶和漏桶的本质区别是什么？
     */
    static class TokenBucket {
        private final int capacity;           // 桶容量
        private final int rate;               // 令牌生成速率（个/秒）
        private final AtomicInteger tokens;   // 当前令牌数
        private final AtomicLong lastRefillTime; // 上次填充时间
        
        public TokenBucket(int capacity, int rate) {
            this.capacity = capacity;
            this.rate = rate;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        }
        
        /**
         * 尝试获取令牌
         * @return true: 获取成功, false: 获取失败
         */
        public synchronized boolean tryAcquire() {
            refill();
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }
        
        /**
         * 填充令牌
         */
        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime.get();
            int newTokens = (int) (elapsed * rate / 1000);
            
            if (newTokens > 0) {
                tokens.set(Math.min(capacity, tokens.get() + newTokens));
                lastRefillTime.set(now);
            }
        }
        
        public int getAvailableTokens() {
            return tokens.get();
        }
    }

    // ============================================================
    // 2. 滑动窗口算法 (Sliding Window)
    // ============================================================
    
    /**
     * 滑动窗口算法：将时间划分为窗口，统计窗口内的请求数
     * 
     * 特点：
     * - 比固定窗口更精确，避免临界突发
     * - 内存占用与窗口大小相关
     * - 常用于API限流、网络流量控制
     * 
     * 面试考点：
     * - 滑动窗口 vs 固定窗口的区别？
     * - 如何优化滑动窗口的内存占用？
     */
    static class SlidingWindow {
        private final int windowSize;      // 窗口大小（毫秒）
        private final int maxRequests;     // 窗口内最大请求数
        private final ConcurrentLinkedQueue<Long> timestamps; // 请求时间戳队列
        
        public SlidingWindow(int windowSize, int maxRequests) {
            this.windowSize = windowSize;
            this.maxRequests = maxRequests;
            this.timestamps = new ConcurrentLinkedQueue<>();
        }
        
        /**
         * 尝试通过限流
         * @return true: 通过, false: 被限流
         */
        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long windowStart = now - windowSize;
            
            // 移除窗口外的时间戳
            while (!timestamps.isEmpty() && timestamps.peek() < windowStart) {
                timestamps.poll();
            }
            
            // 判断是否超过限制
            if (timestamps.size() < maxRequests) {
                timestamps.offer(now);
                return true;
            }
            
            return false;
        }
        
        public int getCurrentCount() {
            return timestamps.size();
        }
    }

    // ============================================================
    // 3. 漏桶算法 (Leaky Bucket)
    // ============================================================
    
    /**
     * 漏桶算法：请求以任意速率进入桶，以固定速率流出
     * 
     * 特点：
     * - 严格平滑流量，输出速率恒定
     * - 无法处理突发流量
     * - 适用于对输出速率有严格要求的场景
     * 
     * 面试考点：
     * - 漏桶和令牌桶的本质区别？
     * - 什么时候选择漏桶而不是令牌桶？
     */
    static class LeakyBucket {
        private final int capacity;      // 桶容量
        private final int leakRate;      // 漏出速率（个/秒）
        private int water;               // 当前水量
        private long lastLeakTime;       // 上次漏水时间
        
        public LeakyBucket(int capacity, int leakRate) {
            this.capacity = capacity;
            this.leakRate = leakRate;
            this.water = 0;
            this.lastLeakTime = System.currentTimeMillis();
        }
        
        /**
         * 尝试加水
         * @return true: 成功, false: 桶已满
         */
        public synchronized boolean tryAcquire() {
            leak();
            if (water < capacity) {
                water++;
                return true;
            }
            return false;
        }
        
        /**
         * 漏水
         */
        private void leak() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastLeakTime;
            int leaked = (int) (elapsed * leakRate / 1000);
            
            if (leaked > 0) {
                water = Math.max(0, water - leaked);
                lastLeakTime = now;
            }
        }
    }

    // ============================================================
    // 4. 计数器限流 (Counter-based)
    // ============================================================
    
    /**
     * 固定窗口计数器：最简单的限流方式
     * 
     * 特点：
     * - 实现简单，内存占用小
     * - 存在临界问题（窗口边界可能突发2倍流量）
     * - 适用于对精度要求不高的场景
     */
    static class FixedWindowCounter {
        private final int maxRequests;
        private final long windowSize;
        private final AtomicInteger count;
        private final AtomicLong windowStart;
        
        public FixedWindowCounter(int maxRequests, long windowSize) {
            this.maxRequests = maxRequests;
            this.windowSize = windowSize;
            this.count = new AtomicInteger(0);
            this.windowStart = new AtomicLong(System.currentTimeMillis());
        }
        
        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart.get() > windowSize) {
                // 重置窗口
                count.set(0);
                windowStart.set(now);
            }
            
            if (count.get() < maxRequests) {
                count.incrementAndGet();
                return true;
            }
            return false;
        }
    }

    // ============================================================
    // 5. 分布式限流 - Redis实现
    // ============================================================
    
    /**
     * 分布式滑动窗口限流（基于Redis Lua脚本）
     * 
     * 面试考点：
     * - 如何保证分布式环境下的原子性？
     * - Redis实现限流的优缺点？
     * - 如何处理Redis集群下的限流？
     */
    static class RedisRateLimiter {
        
        /**
         * Redis Lua脚本 - 滑动窗口限流
         * 
         * KEYS[1]: 限流key
         * ARGV[1]: 窗口大小（毫秒）
         * ARGV[2]: 最大请求数
         * ARGV[3]: 当前时间戳
         */
        private static final String LUA_SCRIPT = 
            "local key = KEYS[1]\n" +
            "local window = tonumber(ARGV[1])\n" +
            "local limit = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "\n" +
            "-- 移除窗口外的请求\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\n" +
            "\n" +
            "-- 获取当前窗口内的请求数\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "\n" +
            "if count < limit then\n" +
            "    -- 未超过限制，添加当前请求\n" +
            "    redis.call('ZADD', key, now, now .. '-' .. math.random())\n" +
            "    redis.call('EXPIRE', key, window / 1000)\n" +
            "    return 1\n" +
            "else\n" +
            "    -- 超过限制\n" +
            "    return 0\n" +
            "end";
        
        // 实际使用时需要注入RedisTemplate
        // @Autowired
        // private StringRedisTemplate redisTemplate;
        
        /**
         * 尝试获取许可（模拟）
         */
        public boolean tryAcquire(String key, int window, int limit) {
            // 实际实现：
            // Long result = redisTemplate.execute(
            //     new DefaultRedisScript<>(LUA_SCRIPT, Long.class),
            //     Collections.singletonList(key),
            //     String.valueOf(window),
            //     String.valueOf(limit),
            //     String.valueOf(System.currentTimeMillis())
            // );
            // return result != null && result == 1L;
            
            System.out.println("[Redis限流] key=" + key + ", window=" + window + "ms, limit=" + limit);
            return true; // 模拟
        }
    }

    // ============================================================
    // 演示和测试
    // ============================================================
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║      限流算法面试题详解 - 高级工程师必备          ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        // 1. 令牌桶演示
        System.out.println("\n【1. 令牌桶算法】");
        System.out.println("  原理：以固定速率向桶中添加令牌，请求需要获取令牌");
        System.out.println("  特点：允许突发流量，平滑限流");
        System.out.println("  应用：API网关、微服务限流");
        
        TokenBucket tokenBucket = new TokenBucket(10, 5); // 容量10，每秒5个令牌
        int successCount = 0;
        for (int i = 0; i < 15; i++) {
            if (tokenBucket.tryAcquire()) {
                successCount++;
            }
        }
        System.out.println("  测试结果：15个请求，通过 " + successCount + " 个");
        
        // 2. 滑动窗口演示
        System.out.println("\n【2. 滑动窗口算法】");
        System.out.println("  原理：统计时间窗口内的请求数");
        System.out.println("  特点：比固定窗口更精确，避免临界突发");
        System.out.println("  应用：API限流、网络流量控制");
        
        SlidingWindow slidingWindow = new SlidingWindow(1000, 5); // 1秒窗口，最多5个请求
        successCount = 0;
        for (int i = 0; i < 10; i++) {
            if (slidingWindow.tryAcquire()) {
                successCount++;
            }
        }
        System.out.println("  测试结果：10个请求，通过 " + successCount + " 个");
        
        // 3. 漏桶演示
        System.out.println("\n【3. 漏桶算法】");
        System.out.println("  原理：请求以任意速率进入，以固定速率流出");
        System.out.println("  特点：严格平滑流量，无法处理突发");
        System.out.println("  应用：网络流量整形、视频流控制");
        
        LeakyBucket leakyBucket = new LeakyBucket(5, 2); // 容量5，每秒漏出2个
        successCount = 0;
        for (int i = 0; i < 10; i++) {
            if (leakyBucket.tryAcquire()) {
                successCount++;
            }
        }
        System.out.println("  测试结果：10个请求，通过 " + successCount + " 个");
        
        // 4. 固定窗口演示
        System.out.println("\n【4. 固定窗口计数器】");
        System.out.println("  原理：在固定时间窗口内计数");
        System.out.println("  特点：实现简单，存在临界问题");
        System.out.println("  应用：对精度要求不高的简单限流");
        
        FixedWindowCounter fixedWindow = new FixedWindowCounter(5, 1000); // 1秒窗口，最多5个
        successCount = 0;
        for (int i = 0; i < 10; i++) {
            if (fixedWindow.tryAcquire()) {
                successCount++;
            }
        }
        System.out.println("  测试结果：10个请求，通过 " + successCount + " 个");
        
        // 5. 分布式限流
        System.out.println("\n【5. 分布式限流（Redis实现）】");
        System.out.println("  原理：使用Redis + Lua脚本保证原子性");
        System.out.println("  特点：支持分布式环境，性能高");
        System.out.println("  应用：分布式系统、微服务架构");
        
        RedisRateLimiter redisLimiter = new RedisRateLimiter();
        redisLimiter.tryAcquire("api:user:list", 1000, 100);
        
        // 面试总结
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║              面试高频问题总结                    ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        System.out.println("\n【Q1: 令牌桶和漏桶的区别？】");
        System.out.println("  令牌桶：以固定速率添加令牌，允许突发（桶中有令牌时可快速消费）");
        System.out.println("  漏桶：以固定速率漏出，严格平滑（无法处理突发）");
        System.out.println("  选择：需要突发用令牌桶，需要平滑用漏桶");
        
        System.out.println("\n【Q2: 滑动窗口如何优化？】");
        System.out.println("  1. 使用Redis ZSet存储时间戳");
        System.out.println("  2. 位图（Bitmap）优化内存");
        System.out.println("  3. 分片窗口减少单点压力");
        
        System.out.println("\n【Q3: 分布式限流方案？】");
        System.out.println("  1. Redis + Lua（推荐，原子性好）");
        System.out.println("  2. Redis + Lua + 令牌桶");
        System.out.println("  3. 网关层限流（Sentinel、Kong）");
        System.out.println("  4. 客户端限流（本地+全局协调）");
        
        System.out.println("\n【Q4: 限流算法选择？】");
        System.out.println("  - API网关：令牌桶（允许突发）");
        System.out.println("  - 消息推送：漏桶（平滑发送）");
        System.out.println("  - 简单场景：固定窗口（实现简单）");
        System.out.println("  - 高精度：滑动窗口（避免临界）");
    }
}
