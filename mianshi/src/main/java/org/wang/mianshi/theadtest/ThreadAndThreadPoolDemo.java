package org.wang.mianshi.theadtest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 面试核心: Thread 生命周期、ThreadPoolExecutor 7 参数、4 种拒绝策略、常见线程池
 * 每个场景一个 main, 独立运行, 注释即答案
 */
public class ThreadAndThreadPoolDemo {

    // ==================== 1. 线程 6 种状态 ====================

    static class ThreadStates {
        public void run() throws InterruptedException {
            Object lock = new Object();

            Thread t = new Thread(() -> {
                // RUNNABLE
                System.out.println("  state after start: " + Thread.currentThread().getState());

                synchronized (lock) {
                    try {
                        lock.wait(); // WAITING
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // 被唤醒后回到 RUNNABLE, 执行完 → TERMINATED
            });

            System.out.println(" NEW: " + t.getState());                       // NEW
            t.start();
            Thread.sleep(100);  // 等线程跑起来
            System.out.println(" RUNNABLE: " + t.getState());                  // RUNNABLE

            // 让主线程持有锁再 notify, 让子线程进入 BLOCKED 状态
            synchronized (lock) {
                Thread.sleep(200); // 等 t 进入 wait()
                System.out.println(" WAITING: " + t.getState());              // WAITING

                // 再起一个线程争同一把锁, 观察 BLOCKED
                Thread waiter = new Thread(() -> {
                    synchronized (lock) { /* 获取锁后立即释放 */ }
                });
                waiter.start();
                Thread.sleep(50);
                // waiter 可能在 BLOCKED (如果 t 正持有锁), 但这里 t 在 wait 已释放锁
                // 所以 waiter 大概率马上获得锁, 这里用 timed_waiting 代替演示
                System.out.println(" waiter state: " + waiter.getState());

                lock.notify();  // 唤醒 t
            }

            t.join();
            System.out.println(" TERMINATED: " + t.getState());               // TERMINATED
        }
    }

    // ==================== 2. 创建线程的 3 种方式 ====================

    // 方式1: 继承 Thread
    static class MyThread extends Thread {
        @Override
        public void run() {
            System.out.println("  继承 Thread: " + Thread.currentThread().getName());
        }
    }

    // 方式2: 实现 Runnable
    static class MyRunnable implements Runnable {
        @Override
        public void run() {
            System.out.println("  实现 Runnable: " + Thread.currentThread().getName());
        }
    }

    // 方式3: 实现 Callable + FutureTask (有返回值)
    static void runCreateWays() throws ExecutionException, InterruptedException {
        new MyThread().start();

        new Thread(new MyRunnable()).start();

        // 等价于: new Thread(() -> ...).start()
        new Thread(() -> System.out.println("  Lambda: " + Thread.currentThread().getName())).start();

        // Callable 有返回值, 可抛异常
        FutureTask<String> future = new FutureTask<>(() -> "Callable 返回值");
        new Thread(future).start();
        System.out.println("  " + future.get());
    }

    // ==================== 3. ThreadPoolExecutor 7 参数 ====================

    /**
     * ThreadPoolExecutor(int corePoolSize,
     *                    int maximumPoolSize,
     *                    long keepAliveTime,
     *                    TimeUnit unit,
     *                    BlockingQueue<Runnable> workQueue,
     *                    ThreadFactory threadFactory,
     *                    RejectedExecutionHandler handler)
     *
     * 执行流程:
     *   提交任务 → 核心线程数未满? → 创建核心线程
     *          → 核心线程已满 → 队列未满? → 入队
     *                       → 队列已满 → 最大线程数未满? → 创建临时线程
     *                                  → 最大线程数已满 → 执行拒绝策略
     */
    static class PoolParams {
        public void run() {
            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    2,                          // corePoolSize: 常驻核心线程数
                    4,                          // maximumPoolSize: 最大线程数
                    30, TimeUnit.SECONDS,       // keepAliveTime: 临时线程空闲存活时间
                    new LinkedBlockingQueue<>(3), // workQueue: 阻塞队列
                    new ThreadFactory() {        // threadFactory: 线程工厂(自定义命名)
                        private final AtomicInteger idx = new AtomicInteger(1);
                        @Override
                        public Thread newThread(Runnable r) {
                            return new Thread(r, "pool-thread-" + idx.getAndIncrement());
                        }
                    },
                    new ThreadPoolExecutor.AbortPolicy() // handler: 拒绝策略
            );

            // 提交 8 个任务 (超过 4+3=7, 第8个触发拒绝)
            for (int i = 1; i <= 8; i++) {
                final int taskId = i;
                try {
                    pool.execute(() -> {
                        System.out.println(Thread.currentThread().getName() + " 执行任务" + taskId);
                        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    });
                    System.out.println("  任务" + taskId + " 提交成功");
                } catch (RejectedExecutionException e) {
                    System.out.println("! 任务" + taskId + " 被拒绝 (AbortPolicy)");
                }
            }
            pool.shutdown();
        }
    }

    // ==================== 4. 四种拒绝策略 ====================

    static class RejectPolicies {
        public void run() {
            // 小池: 核心1最大1, 队列1, 提交3个任务, 第3个必被拒
            testPolicy("AbortPolicy(抛异常, 默认)", new ThreadPoolExecutor.AbortPolicy());
            testPolicy("CallerRunsPolicy(由提交线程执行)", new ThreadPoolExecutor.CallerRunsPolicy());
            testPolicy("DiscardOldestPolicy(丢弃队首)", new ThreadPoolExecutor.DiscardOldestPolicy());
            testPolicy("DiscardPolicy(静默丢弃新任务)", new ThreadPoolExecutor.DiscardPolicy());
        }

        private void testPolicy(String name, RejectedExecutionHandler handler) {
            System.out.println("\n  --- " + name + " ---");
            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    1, 1, 0, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(1),
                    handler
            );
            for (int i = 1; i <= 3; i++) {
                final int id = i;
                try {
                    pool.execute(() -> {
                        System.out.println("    执行任务" + id);
                        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    });
                } catch (RejectedExecutionException e) {
                    System.out.println("    RejectedExecutionException: 任务" + id);
                }
            }
            pool.shutdown();
            try { pool.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // ==================== 5. Executors 工厂方法 ====================

    /**
     * 面试重点: 为什么阿里规约禁止 Executors 创建线程池?
     *   FixedThreadPool / SingleThreadPool: 无界队列, 可能 OOM
     *   CachedThreadPool / ScheduledThreadPool: 无限线程数, 可能 OOM
     */
    static class ExecutorFactories {
        public void run() {
            System.out.println("  推荐: 显式使用 ThreadPoolExecutor 构造, 明确各参数");

            // newFixedThreadPool(3) = core=max=3, 无界队列
            ExecutorService fixed = Executors.newFixedThreadPool(3);

            // newCachedThreadPool = core=0, max=Integer.MAX_VALUE, SynchronousQueue
            ExecutorService cached = Executors.newCachedThreadPool();

            // newSingleThreadExecutor = core=max=1, 无界队列, 保证任务顺序执行
            ExecutorService single = Executors.newSingleThreadExecutor();

            // newScheduledThreadPool = core指定, max=Integer.MAX_VALUE, DelayedWorkQueue
            ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(2);

            // submit 有返回值, execute 没有
            Future<Integer> future = fixed.submit(() -> {
                System.out.println("  submit 任务执行");
                return 42;
            });
            try {
                System.out.println("  submit 结果: " + future.get());
            } catch (Exception e) { e.printStackTrace(); }

            fixed.shutdown();
            cached.shutdown();
            single.shutdown();
            scheduled.shutdown();
        }
    }

    // ==================== 6. shutdown vs shutdownNow ====================

    static class ShutdownDemo {
        public void run() throws InterruptedException {
            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    2, 2, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>()
            );

            pool.execute(() -> {
                System.out.println("  任务1 开始");
                try { Thread.sleep(5000); } catch (InterruptedException e) {
                    System.out.println("  任务1 收到中断!");
                    Thread.currentThread().interrupt();
                }
            });

            // shutdown: 不再接收新任务, 已提交的任务继续执行完
            pool.shutdown();
            System.out.println("  shutdown 已调用, 但任务1仍在运行...");

            // shutdownNow: 中断所有正在执行的任务, 返回未执行的任务列表
            // pool.shutdownNow();

            boolean terminated = pool.awaitTermination(3, TimeUnit.SECONDS);
            System.out.println("  是否在3秒内终止: " + terminated);
        }
    }

    // ==================== 7. 线程池常见面试题: 核心线程会被回收吗? ====================
    /**
     * 默认不会. 但调用 allowCoreThreadTimeOut(true) 后, keepAliveTime 内空闲的核心线程也会被回收.
     * 面试追问: shutdown() 后任务队列里的任务不会执行, 直接返回未执行的任务列表.
     */

    // ==================== 入口 ====================

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. 线程 6 种状态 ==========");
        new ThreadStates().run();

        System.out.println("\n========== 2. 创建线程的 3 种方式 ==========");
        runCreateWays();

        System.out.println("\n========== 3. ThreadPoolExecutor 7 参数 & 执行流程 ==========");
        new PoolParams().run();
        Thread.sleep(4000); // 等线程池任务跑完

        System.out.println("\n========== 4. 四种拒绝策略 ==========");
        new RejectPolicies().run();

        System.out.println("\n========== 5. Executors 工厂方法 & 阿里规约 ==========");
        new ExecutorFactories().run();

        System.out.println("\n========== 6. shutdown vs shutdownNow ==========");
        // 这个会等 3 秒, 放在最后
    }

    // ==================== 附录: 面试速记 ====================
    /*
     * 线程状态 (6种):
     *   NEW → RUNNABLE(就绪+运行) → BLOCKED(争锁失败) / WAITING(wait/join/park) / TIMED_WAITING(sleep/wait(ms)/join(ms))
     *   → TERMINATED
     *   操作系统层面: BLOCKED/WAITING/TIMED_WAITING 都算阻塞, CPU 不分配时间片
     *
     * ThreadPoolExecutor:
     *   7 参数: corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler
     *   4 拒绝策略: Abort(抛异常), CallerRuns(调用者执行), DiscardOldest(丢队首), Discard(静默丢弃)
     *   队列: SynchronousQueue(无容量, 直接交接), LinkedBlockingQueue(无界/有界), ArrayBlockingQueue(有界), PriorityBlockingQueue(优先级)
     *
     * 如何合理配置线程数:
     *   CPU 密集型: Ncpu + 1
     *   IO 密集型: Ncpu * 2 (或 Ncpu * (1 + 平均等待时间/平均计算时间))
     *
     * submit vs execute:
     *   execute(Runnable): 无返回值, 异常直接抛出
     *   submit(Callable/Runnable): 返回 Future, 异常被封装在 Future.get() 中
     */
}
