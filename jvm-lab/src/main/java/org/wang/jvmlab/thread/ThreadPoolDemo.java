package org.wang.jvmlab.thread;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【考点：线程池】7 参数执行流程 + 4 种拒绝策略 + shutdown 语义
 *
 * 【运行】
 *   java org.wang.jvmlab.thread.ThreadPoolDemo
 *
 * 【面试要点】
 *   1. 七个参数：corePoolSize、maximumPoolSize、keepAliveTime、unit、
 *      workQueue、threadFactory、handler。
 *   2. 执行流程（背下来，必考题）：
 *      提交任务 → 核心线程没满？→ 创建核心线程
 *              → 核心满了 → 队列没满？→ 入队
 *                        → 队列满了 → 最大线程没满？→ 创建临时线程
 *                                  → 都满了 → 执行拒绝策略
 *   3. 四种拒绝策略：Abort（抛异常，默认）、CallerRuns（让提交线程自己跑，
 *      相当于反向限流）、DiscardOldest（丢队首）、Discard（静默丢弃）。
 *   4. 为什么阿里规约禁止 Executors 创建线程池？
 *      Fixed/Single 用无界队列 → 堆积 OOM；Cached/Scheduled 最大线程数是
 *      Integer.MAX_VALUE → 线程爆炸 OOM。所以要显式 new ThreadPoolExecutor。
 *   5. shutdown vs shutdownNow：前者不再收新任务、已提交的跑完；
 *      后者尝试中断正在执行的任务，并返回队列里未执行的任务列表。
 *   6. 核心线程默认不会被回收，allowCoreThreadTimeOut(true) 之后才会。
 */
public class ThreadPoolDemo {

    public static void main(String[] args) throws Exception {
        demoFlow();
        demoRejectionPolicies();
        demoShutdown();
    }

    /** 场景：core=2, max=4, queue=3 → 2 核心 + 3 队列 + 2 临时 = 7，第 8 个被拒 */
    static void demoFlow() throws Exception {
        System.out.println("========== 1. 执行流程（core=2, max=4, queue=3，提交 8 个任务）==========");
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(3),
                namedFactory("biz-pool"),
                new ThreadPoolExecutor.AbortPolicy());

        for (int i = 1; i <= 8; i++) {
            final int taskId = i;
            try {
                pool.execute(() -> {
                    System.out.println("  " + Thread.currentThread().getName() + " 执行任务" + taskId);
                    sleep(500);
                });
                System.out.printf("  任务%d 提交成功（当前线程数=%d, 队列=%d）%n",
                        taskId, pool.getPoolSize(), pool.getQueue().size());
            } catch (java.util.concurrent.RejectedExecutionException e) {
                System.out.printf("  任务%d 被拒绝：%s%n", taskId, e.getClass().getSimpleName());
            }
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  峰值线程数 = " + pool.getLargestPoolSize() + "（最大只能到 max=4）");
    }

    /** 四种拒绝策略对比：core=max=1, queue=1，提交 3 个任务 */
    static void demoRejectionPolicies() {
        System.out.println("\n========== 2. 四种拒绝策略（core=max=1, queue=1，提交 3 个）==========");
        testPolicy("AbortPolicy（抛异常，默认）", new ThreadPoolExecutor.AbortPolicy());
        testPolicy("CallerRunsPolicy（提交线程自己跑，会打印 main）",
                new ThreadPoolExecutor.CallerRunsPolicy());
        testPolicy("DiscardOldestPolicy（丢弃队首）", new ThreadPoolExecutor.DiscardOldestPolicy());
        testPolicy("DiscardPolicy（静默丢弃，什么都不打印）", new ThreadPoolExecutor.DiscardPolicy());
    }

    static void testPolicy(String name, RejectedExecutionHandler handler) {
        System.out.println("  --- " + name + " ---");
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), handler);
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            try {
                pool.execute(() -> {
                    System.out.println("    执行任务" + id + " @ " + Thread.currentThread().getName());
                    sleep(200);
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                System.out.println("    任务" + id + " → RejectedExecutionException");
            }
        }
        pool.shutdown();
        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** shutdown vs shutdownNow */
    static void demoShutdown() throws Exception {
        System.out.println("\n========== 3. shutdown 的语义 ==========");
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

        pool.execute(() -> {
            System.out.println("  长任务开始，预计跑 3 秒");
            sleep(3000);
            System.out.println("  长任务正常结束");
        });
        sleep(300);

        pool.shutdown();
        System.out.println("  调用 shutdown 后：isShutdown=" + pool.isShutdown()
                + "，已提交的任务仍会执行完");
        boolean done = pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  5 秒内是否终止：" + done + "（shutdown 不打断正在执行的任务）");

        System.out.println("\n  对比：shutdownNow 会 interrupt 正在执行的任务，");
        System.out.println("  并返回队列中尚未执行的任务列表 —— 代码里没演示是为了让进程干净退出。");
    }

    static java.util.concurrent.ThreadFactory namedFactory(String prefix) {
        AtomicInteger idx = new AtomicInteger(1);
        return r -> new Thread(r, prefix + "-" + idx.getAndIncrement());
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
