package org.wang.advanced.concurrent.seckill;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 秒杀系统设计 - 九年经验高级工程师面试必考
 * 
 * 面试高频问题：
 * 1. 秒杀系统的核心挑战是什么？
 * 2. 如何防止超卖？
 * 3. 如何保证高并发下的性能？
 * 4. 如何设计秒杀系统的架构？
 */
public class SeckillDesign {

    // ============================================================
    // 1. 库存扣减核心逻辑 - 多种方案对比
    // ============================================================
    
    /**
     * 方案1：数据库乐观锁（适合中低并发）
     * 
     * SQL: UPDATE goods SET stock = stock - 1 WHERE id = ? AND stock > 0
     * 
     * 优点：实现简单，数据一致性好
     * 缺点：数据库压力大，高并发下性能差
     * 适用：QPS < 1000
     */
    static class DatabaseOptimisticLock {
        
        /**
         * 扣减库存（模拟数据库操作）
         * @return true: 成功, false: 库存不足
         */
        public boolean deductStock(Long goodsId, int quantity) {
            // 实际SQL: UPDATE goods SET stock = stock - #{quantity} 
            //         WHERE id = #{goodsId} AND stock >= #{quantity}
            
            System.out.println("[数据库乐观锁] 扣减库存: goodsId=" + goodsId + ", quantity=" + quantity);
            return true; // 模拟成功
        }
    }

    /**
     * 方案2：Redis预扣减（适合高并发）
     * 
     * 命令: DECR stock:1001 或 Lua脚本
     * 
     * 优点：性能极高，单机10万+QPS
     * 缺点：需要与数据库同步，可能超卖
     * 适用：QPS > 10000
     */
    static class RedisDeductStock {
        
        /**
         * Redis Lua脚本 - 原子扣减库存
         */
        private static final String LUA_DEDUCT_SCRIPT =
            "local stock = redis.call('get', KEYS[1])\n" +
            "if stock and tonumber(stock) >= tonumber(ARGV[1]) then\n" +
            "    redis.call('decrby', KEYS[1], ARGV[1])\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end";
        
        private final AtomicLong redisStock = new AtomicLong(100); // 模拟Redis库存
        
        /**
         * Lua脚本扣减库存
         */
        public boolean deductStock(Long goodsId, int quantity) {
            // 实际使用RedisTemplate执行Lua脚本
            // Long result = redisTemplate.execute(
            //     new DefaultRedisScript<>(LUA_DEDUCT_SCRIPT, Long.class),
            //     Collections.singletonList("stock:" + goodsId),
            //     String.valueOf(quantity)
            // );
            
            // 模拟Redis操作
            long currentStock = redisStock.get();
            if (currentStock >= quantity) {
                return redisStock.compareAndSet(currentStock, currentStock - quantity);
            }
            return false;
        }
        
        public long getStock() {
            return redisStock.get();
        }
    }

    /**
     * 方案3：Redis + Lua + 分段库存（适合超高并发）
     * 
     * 将库存分成多个段，分散到不同Redis节点
     * 
     * 优点：并发能力极强，可线性扩展
     * 缺点：实现复杂，库存分配不均
     * 适用：QPS > 100000（如双11）
     */
    static class RedisSegmentedStock {
        private static final int SEGMENT_COUNT = 4; // 库存分段数
        private final AtomicLong[] segmentStocks = new AtomicLong[SEGMENT_COUNT];
        
        public RedisSegmentedStock(int totalStock) {
            int perSegment = totalStock / SEGMENT_COUNT;
            for (int i = 0; i < SEGMENT_COUNT; i++) {
                segmentStocks[i] = new AtomicLong(perSegment);
            }
        }
        
        /**
         * 分段扣减库存
         */
        public boolean deductStock(Long goodsId, int quantity) {
            // 随机选择一个段
            int segment = ThreadLocalRandom.current().nextInt(SEGMENT_COUNT);
            
            // 尝试从当前段扣减
            for (int i = 0; i < SEGMENT_COUNT; i++) {
                int idx = (segment + i) % SEGMENT_COUNT;
                long current = segmentStocks[idx].get();
                if (current >= quantity) {
                    if (segmentStocks[idx].compareAndSet(current, current - quantity)) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        public long getTotalStock() {
            long total = 0;
            for (AtomicLong stock : segmentStocks) {
                total += stock.get();
            }
            return total;
        }
    }

    // ============================================================
    // 2. 请求过滤 - 多层防护
    // ============================================================
    
    /**
     * 前端限流 + 请求过滤
     * 
     * 面试考点：
     * - 如何防止恶意刷单？
     * - 如何减少无效请求到达后端？
     */
    static class RequestFilter {
        private final ConcurrentHashMap<String, Long> userRequestMap = new ConcurrentHashMap<>();
        private final int maxRequestPerUser = 1; // 每用户限购1次
        private final long requestInterval = 1000; // 1秒内不能重复请求
        
        /**
         * 用户请求去重
         * @return true: 允许请求, false: 重复请求
         */
        public boolean filterDuplicateRequest(String userId) {
            Long lastRequestTime = userRequestMap.get(userId);
            long now = System.currentTimeMillis();
            
            if (lastRequestTime != null && now - lastRequestTime < requestInterval) {
                System.out.println("[请求过滤] 用户 " + userId + " 请求过于频繁");
                return false;
            }
            
            userRequestMap.put(userId, now);
            return true;
        }
        
        /**
         * 购买次数限制
         * @return true: 允许购买, false: 已达限购
         */
        public boolean filterPurchaseLimit(String userId, ConcurrentHashMap<String, AtomicInteger> purchaseCount) {
            AtomicInteger count = purchaseCount.computeIfAbsent(userId, k -> new AtomicInteger(0));
            if (count.get() >= maxRequestPerUser) {
                System.out.println("[限购检查] 用户 " + userId + " 已达限购数量");
                return false;
            }
            count.incrementAndGet();
            return true;
        }
    }

    // ============================================================
    // 3. 消息队列异步处理
    // ============================================================
    
    /**
     * 异步下单 - 使用消息队列削峰
     * 
     * 面试考点：
     * - 为什么需要消息队列？
     * - 如何保证消息不丢失？
     * - 如何处理消息堆积？
     */
    static class AsyncOrderService {
        private final BlockingQueue<String> orderQueue = new LinkedBlockingQueue<>(1000);
        private final AtomicInteger orderCount = new AtomicInteger(0);
        
        /**
         * 异步提交订单
         */
        public boolean submitOrder(String userId, Long goodsId) {
            String order = userId + ":" + goodsId + ":" + System.currentTimeMillis();
            try {
                boolean success = orderQueue.offer(order, 100, TimeUnit.MILLISECONDS);
                if (success) {
                    System.out.println("[异步下单] 订单入队: " + order);
                } else {
                    System.out.println("[异步下单] 订单队列已满，拒绝请求");
                }
                return success;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        /**
         * 消费订单（模拟消息消费者）
         */
        public void consumeOrders() {
            CompletableFuture.runAsync(() -> {
                while (true) {
                    try {
                        String order = orderQueue.take();
                        processOrder(order);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
        
        private void processOrder(String order) {
            // 实际处理：创建订单、扣减库存、发送通知等
            int count = orderCount.incrementAndGet();
            System.out.println("[订单处理] " + order + ", 处理总数: " + count);
        }
        
        public int getPendingCount() {
            return orderQueue.size();
        }
    }

    // ============================================================
    // 4. 分布式锁 - 防止并发问题
    // ============================================================
    
    /**
     * 分布式锁实现（模拟Redisson）
     * 
     * 面试考点：
     * - 分布式锁的续期问题？
     * - 锁的可重入性如何实现？
     * - 如何避免死锁？
     */
    static class DistributedLock {
        private final ReentrantLock lock = new ReentrantLock();
        private final ConcurrentHashMap<String, Thread> lockOwnerMap = new ConcurrentHashMap<>();
        
        /**
         * 获取锁
         */
        public boolean tryLock(String key, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            
            while (System.currentTimeMillis() < deadline) {
                Thread currentThread = Thread.currentThread();
                Thread owner = lockOwnerMap.putIfAbsent(key, currentThread);
                
                if (owner == null) {
                    // 获取锁成功
                    lock.lock();
                    return true;
                } else if (owner == currentThread) {
                    // 可重入
                    lock.lock();
                    return true;
                }
                
                // 等待后重试
                Thread.sleep(10);
            }
            
            return false;
        }
        
        /**
         * 释放锁
         */
        public void unlock(String key) {
            Thread currentThread = Thread.currentThread();
            Thread owner = lockOwnerMap.get(key);
            
            if (owner == currentThread) {
                lockOwnerMap.remove(key);
                lock.unlock();
            }
        }
        
        /**
         * 模拟Redisson的看门狗机制
         * 自动续期，防止锁过期
         */
        public void startWatchdog(String key, long leaseTime) {
            CompletableFuture.runAsync(() -> {
                while (lock.isLocked()) {
                    try {
                        Thread.sleep(leaseTime / 3); // 续期时间为1/3
                        // 实际实现：redis.pExpire(key, leaseTime)
                        System.out.println("[看门狗] 续期锁: " + key);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }

    // ============================================================
    // 5. 完整秒杀流程
    // ============================================================
    
    /**
     * 秒杀系统完整流程
     * 
     * 架构层次：
     * 1. 前端层：静态化、CDN、按钮防抖
     * 2. 网关层：限流、过滤、负载均衡
     * 3. 服务层：库存校验、订单创建
     * 4. 数据层：Redis预扣减、数据库落库
     */
    static class SeckillService {
        private final RedisDeductStock redisStock = new RedisDeductStock();
        private final RequestFilter requestFilter = new RequestFilter();
        private final AsyncOrderService orderService = new AsyncOrderService();
        private final ConcurrentHashMap<String, AtomicInteger> purchaseCount = new ConcurrentHashMap<>();
        
        /**
         * 秒杀主流程
         * @return 秒杀结果
         */
        public String seckill(String userId, Long goodsId) {
            // 1. 参数校验
            if (userId == null || goodsId == null) {
                return "参数错误";
            }
            
            // 2. 请求去重
            if (!requestFilter.filterDuplicateRequest(userId)) {
                return "请求过于频繁，请稍后再试";
            }
            
            // 3. 限购检查
            if (!requestFilter.filterPurchaseLimit(userId, purchaseCount)) {
                return "已达到限购数量";
            }
            
            // 4. Redis预扣减库存
            if (!redisStock.deductStock(goodsId, 1)) {
                return "库存不足";
            }
            
            // 5. 异步下单
            boolean submitted = orderService.submitOrder(userId, goodsId);
            if (!submitted) {
                // 下单失败，回滚库存
                // 实际实现：redis.incr("stock:" + goodsId)
                return "系统繁忙，请稍后再试";
            }
            
            return "秒杀成功，订单已提交";
        }
        
        public long getStock() {
            return redisStock.getStock();
        }
        
        public int getPendingOrders() {
            return orderService.getPendingCount();
        }
    }

    // ============================================================
    // 演示和测试
    // ============================================================
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║      秒杀系统设计面试题详解 - 高级工程师必备      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        SeckillService seckillService = new SeckillService();
        
        // 模拟100个用户并发秒杀
        System.out.println("\n【模拟100个用户并发秒杀】");
        System.out.println("初始库存: " + seckillService.getStock());
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        for (int i = 0; i < 100; i++) {
            final String userId = "user" + i;
            executor.submit(() -> {
                String result = seckillService.seckill(userId, 1001L);
                if (result.contains("成功")) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("秒杀完成:");
        System.out.println("  成功: " + successCount.get());
        System.out.println("  失败: " + failCount.get());
        System.out.println("  剩余库存: " + seckillService.getStock());
        System.out.println("  待处理订单: " + seckillService.getPendingOrders());
        
        // 面试总结
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║           秒杀系统设计面试总结                   ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        System.out.println("\n【核心挑战】");
        System.out.println("  1. 高并发：瞬间大量请求涌入");
        System.out.println("  2. 超卖：库存扣减的原子性");
        System.out.println("  3. 幂等：防止重复下单");
        System.out.println("  4. 性能：响应时间要求高");
        
        System.out.println("\n【架构设计】");
        System.out.println("  前端层：静态化 + CDN + 按钮防抖 + 倒计时");
        System.out.println("  网关层：限流 + 过滤 + 负载均衡");
        System.out.println("  服务层：库存校验 + 订单创建 + 消息队列");
        System.out.println("  数据层：Redis预扣减 + 数据库落库");
        
        System.out.println("\n【库存扣减方案对比】");
        System.out.println("  方案            | 并发能力 | 实现复杂度 | 适用场景");
        System.out.println("  ----------------|----------|------------|----------");
        System.out.println("  数据库乐观锁    | 低       | 简单       | QPS<1000");
        System.out.println("  Redis原子操作   | 高       | 中等       | QPS>10000");
        System.out.println("  Redis分段库存   | 极高     | 复杂       | QPS>100000");
        
        System.out.println("\n【面试高频问题】");
        System.out.println("  Q1: 如何防止超卖？");
        System.out.println("      A: Redis Lua脚本原子扣减 + 数据库最终校验");
        System.out.println("  Q2: 如何保证高可用？");
        System.out.println("      A: Redis集群 + 消息队列削削 + 服务降级");
        System.out.println("  Q3: 秒杀结束后如何处理？");
        System.out.println("      A: 消息队列异步处理订单 + 定时任务补偿");
    }
}
