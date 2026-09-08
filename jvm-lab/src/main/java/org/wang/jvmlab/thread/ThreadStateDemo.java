package org.wang.jvmlab.thread;

import java.util.concurrent.CountDownLatch;

/**
 * 【考点：线程状态】六种线程状态的确定性观察
 *
 * 【运行】
 *   java org.wang.jvmlab.thread.ThreadStateDemo
 *
 * 【设计说明】
 *   老版本靠 sleep 猜状态（注释里自己都写着"大概率"），跑出来标签和值对不上。
 *   这里改成**轮询到目标状态再打印**，结果稳定可复现。
 *
 * 【面试要点】
 *   1. Java 定义了 6 种状态：NEW、RUNNABLE、BLOCKED、WAITING、TIMED_WAITING、TERMINATED。
 *   2. RUNNABLE 在 JVM 里包含操作系统的"就绪"和"运行中"，所以看到 RUNNABLE
 *      不代表它此刻真的在占用 CPU。
 *   3. 三种阻塞的区别（高频追问）：
 *      BLOCKED       → 争抢 synchronized **监视器锁**失败（只有这个和 synchronized 有关）
 *      WAITING       → 主动 wait()/join()/park()，需要别人唤醒
 *      TIMED_WAITING → 带超时参数的 sleep()/wait(ms)/join(ms)/parkNanos()
 *      LockSupport.park() 和 ReentrantLock 的等待是 WAITING，不是 BLOCKED ——
 *      这是区分"synchronized 派"和"JUC 派"的关键点。
 *   4. 操作系统层面，三种阻塞都不分配 CPU 时间片。
 */
public class ThreadStateDemo {

    static final Object LOCK = new Object();
    static volatile boolean spinning = true;

    public static void main(String[] args) throws Exception {
        printNew();
        printBlocked();
        printWaiting();
        printTimedWaiting();
        printRunnable();
        printTerminated();
    }

    /** NEW：创建了但没 start */
    static void printNew() throws Exception {
        Thread t = new Thread(() -> {
        }, "demo-new");
        System.out.printf("%-16s %s%n", "NEW", t.getState());
    }

    /** BLOCKED：主线程先占住锁，另一个线程进不来 */
    static void printBlocked() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            started.countDown();
            synchronized (LOCK) {
                System.out.println("  （blocked 线程拿到锁了）");
            }
        }, "demo-blocked");
        synchronized (LOCK) {          // 主线程持有锁
            t.start();
            started.await();
            awaitState(t, Thread.State.BLOCKED);
            System.out.printf("%-16s %s%n", "BLOCKED", t.getState());
        }
        t.join();
    }

    /** WAITING：wait() 后需要被唤醒 */
    static void printWaiting() throws Exception {
        Thread t = new Thread(() -> {
            synchronized (LOCK) {
                try {
                    LOCK.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "demo-waiting");
        t.start();
        awaitState(t, Thread.State.WAITING);
        System.out.printf("%-16s %s%n", "WAITING", t.getState());
        synchronized (LOCK) {
            LOCK.notifyAll();          // 唤醒它，让它能正常结束
        }
        t.join();
    }

    /** TIMED_WAITING：sleep(ms) */
    static void printTimedWaiting() throws Exception {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "demo-timed-waiting");
        t.start();
        awaitState(t, Thread.State.TIMED_WAITING);
        System.out.printf("%-16s %s%n", "TIMED_WAITING", t.getState());
        t.join();
    }

    /** RUNNABLE：一直在跑（空转循环） */
    static void printRunnable() throws Exception {
        Thread t = new Thread(() -> {
            long n = 0;
            while (spinning) {
                n++;
            }
            if (n < 0) {
                System.out.println(n);
            }
        }, "demo-runnable");
        t.start();
        awaitState(t, Thread.State.RUNNABLE);
        System.out.printf("%-16s %s%n", "RUNNABLE", t.getState());
        spinning = false;
        t.join();
    }

    /** TERMINATED：执行完 join 之后 */
    static void printTerminated() throws Exception {
        Thread t = new Thread(() -> System.out.println("  （terminated 线程执行完毕）"), "demo-terminated");
        t.start();
        t.join();
        System.out.printf("%-16s %s%n", "TERMINATED", t.getState());
    }

    /** 轮询直到目标状态，避免靠 sleep 猜 */
    static void awaitState(Thread t, Thread.State expected) {
        for (int i = 0; i < 200; i++) {
            if (t.getState() == expected) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println("  （等待超时，实际状态 = " + t.getState() + "）");
    }
}
