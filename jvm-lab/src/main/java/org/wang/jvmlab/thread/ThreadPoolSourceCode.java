package org.wang.jvmlab.thread;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 线程池源码分析面试题 - 九年经验高级工程师必备
 * 
 * 面试高频问题：
 * 1. 线程池的核心参数有哪些？
 * 2. 线程池的工作流程是怎样的？
 * 3. 四种拒绝策略的区别？
 * 4. 如何合理配置线程池参数？
 */
public class ThreadPoolSourceCode {

    // ============================================================
    // 1. 线程池核心参数详解
    // ============================================================
    
    /**
     * ThreadPoolExecutor 核心参数
     * 
     * 面试必背：
     * - corePoolSize：核心线程数
     * - maximumPoolSize：最大线程数
     * - keepAliveTime：空闲线程存活时间
     * - unit：时间单位
     * - workQueue：任务队列
     * - threadFactory：线程工厂
     * - handler：拒绝策略
     */
    static class ThreadPoolParams {
        
        public void demonstrate() {
            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.println("║        线程池核心参数详解                        ║");
            System.out.println("╚══════════════════════════════════════════════════╝");
            
            System.out.println("\n【1. corePoolSize - 核心线程数】");
            System.out.println("  定义：线程池中保持的最小线程数（即使空闲）");
            System.out.println("  特点：核心线程默认不会被回收（除非设置allowCoreThreadTimeOut）");
            System.out.println("  建议：CPU密集型 = CPU核数+1，IO密集型 = CPU核数*2");
            
            System.out.println("\n【2. maximumPoolSize - 最大线程数】");
            System.out.println("  定义：线程池允许创建的最大线程数");
            System.out.println("  特点：当队列满时，会创建新线程直到达到最大线程数");
            System.out.println("  建议：根据系统资源和业务需求设置");
            
            System.out.println("\n【3. keepAliveTime - 空闲线程存活时间】");
            System.out.println("  定义：非核心线程空闲时的存活时间");
            System.out.println("  特点：超过存活时间，线程会被回收");
            System.out.println("  建议：通常设置60秒");
            
            System.out.println("\n【4. workQueue - 任务队列】");
            System.out.println("  类型：");
            System.out.println("    - ArrayBlockingQueue：有界数组队列（推荐）");
            System.out.println("    - LinkedBlockingQueue：无界链表队列（危险）");
            System.out.println("    - SynchronousQueue：不存储元素的队列");
            System.out.println("    - PriorityBlockingQueue：优先级队列");
            System.out.println("  建议：使用有界队列，避免OOM");
            
            System.out.println("\n【5. threadFactory - 线程工厂】");
            System.out.println("  作用：创建线程时设置线程名称、优先级、是否守护线程");
            System.out.println("  建议：自定义线程工厂，方便问题排查");
            
            System.out.println("\n【6. handler - 拒绝策略】");
            System.out.println("  触发条件：线程池和队列都满了");
            System.out.println("  策略：见下文详解");
        }
    }

    // ============================================================
    // 2. 线程池工作流程
    // ============================================================
    
    /**
     * 线程池工作流程
     * 
     * 面试必背流程图：
     * 1. 提交任务 → 核心线程是否已满？
     *    - 否：创建核心线程执行
     *    - 是：→ 2
     * 2. 队列是否已满？
     *    - 否：任务入队等待
     *    - 是：→ 3
     * 3. 线程数是否达到最大？
     *    - 否：创建非核心线程执行
     *    - 是：→ 4
     * 4. 执行拒绝策略
     */
    static class ThreadPoolWorkflow {
        
        public void demonstrate() {
            System.out.println("\n╔══════════════════════════════════════════════════╗");
            System.out.println("║        线程池工作流程                            ║");
            System.out.println("╚══════════════════════════════════════════════════╝");
            
            System.out.println("\n【流程图】");
            System.out.println("  ┌─────────────┐");
            System.out.println("  │  提交任务    │");
            System.out.println("  └──────┬──────┘");
            System.out.println("         ▼");
            System.out.println("  ┌─────────────┐     否    ┌─────────────┐");
            System.out.println("  │ 核心线程满？ │─────────▶│ 创建核心线程 │");
            System.out.println("  └──────┬──────┘          └─────────────┘");
            System.out.println("         │ 是");
            System.out.println("         ▼");
            System.out.println("  ┌─────────────┐     否    ┌─────────────┐");
            System.out.println("  │  队列满？    │─────────▶│  任务入队    │");
            System.out.println("  └──────┬──────┘          └─────────────┘");
            System.out.println("         │ 是");
            System.out.println("         ▼");
            System.out.println("  ┌─────────────┐     否    ┌─────────────┐");
            System.out.println("  │ 线程数满？   │─────────▶│ 创建非核心线程│");
            System.out.println("  └──────┬──────┘          └─────────────┘");
            System.out.println("         │ 是");
            System.out.println("         ▼");
            System.out.println("  ┌─────────────┐");
            System.out.println("  │ 执行拒绝策略 │");
            System.out.println("  └─────────────┘");
            
            System.out.println("\n【源码解析】");
            System.out.println("  public void execute(Runnable command) {");
            System.out.println("      int c = ctl.get();");
            System.out.println("      // 1. 核心线程是否已满");
            System.out.println("      if (workerCountOf(c) < corePoolSize) {");
            System.out.println("          if (addWorker(command, true))");
            System.out.println("              return;");
            System.out.println("      }");
            System.out.println("      // 2. 队列是否已满");
            System.out.println("      if (isRunning(c) && workQueue.offer(command)) {");
            System.out.println("          // 入队成功");
            System.out.println("      }");
            System.out.println("      // 3. 线程数是否达到最大");
            System.out.println("      else if (!addWorker(command, false))");
            System.out.println("          reject(command); // 4. 执行拒绝策略");
            System.out.println("  }");
        }
    }

    // ============================================================
    // 3. 四种拒绝策略
    // ============================================================
    
    /**
     * 拒绝策略详解
     * 
     * 面试必背：
     * - AbortPolicy：抛出异常（默认）
     * - CallerRunsPolicy：调用者执行
     * - DiscardPolicy：静默丢弃
     * - DiscardOldestPolicy：丢弃最旧任务
     */
    static class RejectionPolicies {
        
        public void demonstrate() {
            System.out.println("\n╔══════════════════════════════════════════════════╗");
            System.out.println("║        四种拒绝策略详解                          ║");
            System.out.println("╚══════════════════════════════════════════════════╝");
            
            System.out.println("\n【1. AbortPolicy - 抛出异常（默认）】");
            System.out.println("  行为：直接抛出 RejectedExecutionException");
            System.out.println("  特点：任务被拒绝，调用者感知到异常");
            System.out.println("  适用：需要感知任务拒绝的场景");
            System.out.println("  源码：");
            System.out.println("    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {");
            System.out.println("        throw new RejectedExecutionException(...);");
            System.out.println("    }");
            
            System.out.println("\n【2. CallerRunsPolicy - 调用者执行】");
            System.out.println("  行为：由提交任务的线程直接执行");
            System.out.println("  特点：不丢弃任务，但会阻塞调用者线程");
            System.out.println("  适用：不允许丢弃任务的场景");
            System.out.println("  源码：");
            System.out.println("    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {");
            System.out.println("        if (!e.isShutdown()) {");
            System.out.println("            r.run(); // 调用者线程执行");
            System.out.println("        }");
            System.out.println("    }");
            
            System.out.println("\n【3. DiscardPolicy - 静默丢弃】");
            System.out.println("  行为：直接丢弃任务，不抛出异常");
            System.out.println("  特点：任务被丢弃，调用者无感知");
            System.out.println("  适用：允许丢弃任务的场景（如日志记录）");
            System.out.println("  源码：");
            System.out.println("    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {");
            System.out.println("        // 什么也不做");
            System.out.println("    }");
            
            System.out.println("\n【4. DiscardOldestPolicy - 丢弃最旧任务】");
            System.out.println("  行为：丢弃队列中最旧的任务，然后重新提交");
            System.out.println("  特点：丢弃最旧的，保留最新的");
            System.out.println("  适用：实时性要求高的场景");
            System.out.println("  源码：");
            System.out.println("    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {");
            System.out.println("        if (!e.isShutdown()) {");
            System.out.println("            e.poll(); // 丢弃队列头");
            System.out.println("            e.execute(r); // 重新提交");
            System.out.println("        }");
            System.out.println("    }");
            
            System.out.println("\n【自定义拒绝策略】");
            System.out.println("  实现 RejectedExecutionHandler 接口");
            System.out.println("  常见：记录日志 + 告警 + 降级处理");
        }
    }

    // ============================================================
    // 4. 线程池参数配置指南
    // ============================================================
    
    /**
     * 线程池参数配置指南
     * 
     * 面试高频问题：
     * - 如何合理配置线程池参数？
     * - CPU密集型和IO密集型的区别？
     */
    static class ThreadPoolConfigGuide {
        
        public void demonstrate() {
            System.out.println("\n╔══════════════════════════════════════════════════╗");
            System.out.println("║        线程池参数配置指南                        ║");
            System.out.println("╚══════════════════════════════════════════════════╝");
            
            System.out.println("\n【1. CPU密集型任务】");
            System.out.println("  特点：大量计算，CPU使用率高，很少IO等待");
            System.out.println("  示例：加密解密、图像处理、数据分析");
            System.out.println("  配置：核心线程数 = CPU核数 + 1");
            System.out.println("  原因：避免线程切换开销，充分利用CPU");
            
            System.out.println("\n【2. IO密集型任务】");
            System.out.println("  特点：大量IO操作，CPU使用率低，经常等待");
            System.out.println("  示例：数据库操作、文件读写、网络请求");
            System.out.println("  配置：核心线程数 = CPU核数 * 2 或 更高");
            System.out.println("  原因：IO等待时CPU可以处理其他线程");
            
            System.out.println("\n【3. 混合型任务】");
            System.out.println("  特点：既有CPU计算，又有IO操作");
            System.out.println("  配置：拆分为CPU密集和IO密集两个线程池");
            System.out.println("  原因：不同类型任务使用不同配置");
            
            System.out.println("\n【4. 经验公式】");
            System.out.println("  CPU密集型：N_cpu + 1");
            System.out.println("  IO密集型：N_cpu * (1 + W/C)");
            System.out.println("    - N_cpu：CPU核数");
            System.out.println("    - W：等待时间");
            System.out.println("    - C：计算时间");
            
            System.out.println("\n【5. 队列选择】");
            System.out.println("  - ArrayBlockingQueue：有界队列，推荐");
            System.out.println("  - LinkedBlockingQueue：无界队列，慎用");
            System.out.println("  - SynchronousQueue：不存储，直接提交");
            System.out.println("  - PriorityBlockingQueue：优先级队列");
            
            System.out.println("\n【6. 实际配置示例】");
            System.out.println("  // CPU密集型");
            System.out.println("  new ThreadPoolExecutor(");
            System.out.println("      Runtime.getRuntime().availableProcessors() + 1,");
            System.out.println("      Runtime.getRuntime().availableProcessors() + 1,");
            System.out.println("      60L, TimeUnit.SECONDS,");
            System.out.println("      new ArrayBlockingQueue<>(1000)");
            System.out.println("  );");
            System.out.println("");
            System.out.println("  // IO密集型");
            System.out.println("  new ThreadPoolExecutor(");
            System.out.println("      Runtime.getRuntime().availableProcessors() * 2,");
            System.out.println("      Runtime.getRuntime().availableProcessors() * 4,");
            System.out.println("      60L, TimeUnit.SECONDS,");
            System.out.println("      new ArrayBlockingQueue<>(1000)");
            System.out.println("  );");
        }
    }

    // ============================================================
    // 5. 线程池监控和调优
    // ============================================================
    
    /**
     * 线程池监控
     * 
     * 面试考点：
     * - 如何监控线程池状态？
     * - 如何动态调整线程池参数？
     */
    static class ThreadPoolMonitor {
        
        public void demonstrate() {
            System.out.println("\n╔══════════════════════════════════════════════════╗");
            System.out.println("║        线程池监控和调优                          ║");
            System.out.println("╚══════════════════════════════════════════════════╝");
            
            System.out.println("\n【监控指标】");
            System.out.println("  getPoolSize()：当前线程数");
            System.out.println("  getActiveCount()：活跃线程数");
            System.out.println("  getCompletedTaskCount()：已完成任务数");
            System.out.println("  getTaskCount()：总任务数");
            System.out.println("  getQueue().size()：队列长度");
            
            System.out.println("\n【动态调整】");
            System.out.println("  setCorePoolSize()：动态调整核心线程数");
            System.out.println("  setMaximumPoolSize()：动态调整最大线程数");
            System.out.println("  allowCoreThreadTimeOut()：允许核心线程超时");
            
            System.out.println("\n【监控代码示例】");
            System.out.println("  ThreadPoolExecutor executor = ...;");
            System.out.println("  ");
            System.out.println("  ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);");
            System.out.println("  monitor.scheduleAtFixedRate(() -> {");
            System.out.println("      System.out.println(\"Pool Size: \" + executor.getPoolSize());");
            System.out.println("      System.out.println(\"Active Count: \" + executor.getActiveCount());");
            System.out.println("      System.out.println(\"Completed: \" + executor.getCompletedTaskCount());");
            System.out.println("      System.out.println(\"Queue Size: \" + executor.getQueue().size());");
            System.out.println("  }, 0, 1, TimeUnit.SECONDS);");
            
            System.out.println("\n【常见问题排查】");
            System.out.println("  1. 线程数持续增长 → 检查是否有任务堆积");
            System.out.println("  2. 队列持续增长 → 增加线程数或优化任务");
            System.out.println("  3. 频繁拒绝 → 调整拒绝策略或增加资源");
            System.out.println("  4. 线程频繁创建销毁 → 检查keepAliveTime设置");
        }
    }

    // ============================================================
    // 6. 自定义线程池示例
    // ============================================================
    
    /**
     * 自定义线程池（生产环境推荐）
     */
    static class CustomThreadPool {
        
        /**
         * 创建自定义线程池
         */
        public static ThreadPoolExecutor createCustomPool(
                String poolName,
                int coreSize,
                int maxSize,
                long keepAliveTime,
                int queueCapacity) {
            
            return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new CustomThreadFactory(poolName),
                new CustomRejectedHandler(poolName)
            );
        }
        
        /**
         * 自定义线程工厂
         */
        static class CustomThreadFactory implements ThreadFactory {
            private final String poolName;
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            CustomThreadFactory(String poolName) {
                this.poolName = poolName;
            }
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, poolName + "-thread-" + threadNumber.getAndIncrement());
                t.setDaemon(false);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        }
        
        /**
         * 自定义拒绝策略
         */
        static class CustomRejectedHandler implements RejectedExecutionHandler {
            private final String poolName;
            
            CustomRejectedHandler(String poolName) {
                this.poolName = poolName;
            }
            
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                // 1. 记录日志
                System.out.println("[" + poolName + "] 任务被拒绝: " + r.toString());
                
                // 2. 触发告警
                // alertService.send("线程池 " + poolName + " 已满");
                
                // 3. 降级处理
                // fallbackService.handle(r);
                
                // 4. 或者抛出异常
                throw new RejectedExecutionException("线程池 " + poolName + " 已满");
            }
        }
        
        public void demonstrate() {
            System.out.println("\n╔══════════════════════════════════════════════════╗");
            System.out.println("║        自定义线程池示例                          ║");
            System.out.println("╚══════════════════════════════════════════════════╝");
            
            ThreadPoolExecutor executor = createCustomPool(
                "OrderService", 10, 20, 60L, 1000
            );
            
            // 提交任务
            for (int i = 0; i < 10; i++) {
                final int taskId = i;
                executor.execute(() -> {
                    System.out.println("执行任务 " + taskId + ", 线程: " + Thread.currentThread().getName());
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            
            // 监控
            System.out.println("\n线程池状态:");
            System.out.println("  Pool Size: " + executor.getPoolSize());
            System.out.println("  Active Count: " + executor.getActiveCount());
            System.out.println("  Completed: " + executor.getCompletedTaskCount());
            System.out.println("  Queue Size: " + executor.getQueue().size());
            
            executor.shutdown();
        }
    }

    // ============================================================
    // 演示和测试
    // ============================================================
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║      线程池源码分析面试题详解 - 高级工程师必备    ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        // 1. 核心参数详解
        new ThreadPoolParams().demonstrate();
        
        // 2. 工作流程
        new ThreadPoolWorkflow().demonstrate();
        
        // 3. 拒绝策略
        new RejectionPolicies().demonstrate();
        
        // 4. 参数配置指南
        new ThreadPoolConfigGuide().demonstrate();
        
        // 5. 监控和调优
        new ThreadPoolMonitor().demonstrate();
        
        // 6. 自定义线程池示例
        new CustomThreadPool().demonstrate();
        
        // 面试总结
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║           线程池面试总结                         ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        
        System.out.println("\n【面试高频问题】");
        System.out.println("  Q1: 线程池的7个核心参数？");
        System.out.println("      A: corePoolSize, maximumPoolSize, keepAliveTime,");
        System.out.println("         unit, workQueue, threadFactory, handler");
        
        System.out.println("\n  Q2: 线程池的工作流程？");
        System.out.println("      A: 核心线程 → 队列 → 非核心线程 → 拒绝策略");
        
        System.out.println("\n  Q3: 如何配置线程池参数？");
        System.out.println("      A: CPU密集=N+1, IO密集=N*2, 混合型拆分");
        
        System.out.println("\n  Q4: 为什么不允许使用Executors创建线程池？");
        System.out.println("      A: FixedThreadPool/ScheduledThreadPool使用无界队列，");
        System.out.println("         可能导致OOM；CachedThreadPool最大线程数=Integer.MAX_VALUE，");
        System.out.println("         可能创建大量线程导致OOM");
        
        System.out.println("\n  Q5: 如何优雅关闭线程池？");
        System.out.println("      A: shutdown() → 等待任务完成 → shutdownNow()");
        
        System.out.println("\n【最佳实践】");
        System.out.println("  1. 使用有界队列，避免OOM");
        System.out.println("  2. 自定义线程工厂，方便排查");
        System.out.println("  3. 自定义拒绝策略，记录日志+告警");
        System.out.println("  4. 监控线程池状态，及时发现问题");
        System.out.println("  5. 根据业务类型合理配置参数");
    }
}
