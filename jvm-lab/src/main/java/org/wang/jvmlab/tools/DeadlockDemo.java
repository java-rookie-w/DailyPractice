package org.wang.jvmlab.tools;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * 【考点 16 / P1-8】死锁的复现与定位
 *
 * 【运行】
 *   java org.wang.jvmlab.tools.DeadlockDemo
 *   另开一个终端执行：jstack <pid>     （程序会打印 pid 和完整命令）
 *
 * 【预期现象】
 *   程序自己用 ThreadMXBean 检测到死锁并打印两个线程的持锁/等锁关系；
 *   jstack 输出末尾会出现 "Found one Java-level deadlock" 段落，内容一致。
 *
 * 【面试要点】
 *   1. 死锁四条件：互斥、占有且等待、不可抢占、循环等待。破坏任意一个即可避免。
 *   2. 工程上的解法：**按固定顺序获取锁**（破坏循环等待）或 **tryLock + 超时**（破坏不可抢占）。
 *   3. 定位手段：jstack / jcmd Thread.print / Arthas thread -b（后者能直接指出阻塞链路）。
 *   4. 数据库死锁靠超时回滚，JVM 死锁只能靠重启或提前预防 —— 所以重点在编码规范。
 */
public class DeadlockDemo {

    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();

    public static void main(String[] args) throws Exception {
        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                System.out.println("  T1 已持有 LOCK_A，等待 LOCK_B...");
                sleep(200);
                synchronized (LOCK_B) {
                    System.out.println("  T1 拿到两把锁（不会发生）");
                }
            }
        }, "deadlock-thread-1");

        Thread t2 = new Thread(() -> {
            synchronized (LOCK_B) {
                System.out.println("  T2 已持有 LOCK_B，等待 LOCK_A...");
                sleep(200);
                synchronized (LOCK_A) {
                    System.out.println("  T2 拿到两把锁（不会发生）");
                }
            }
        }, "deadlock-thread-2");

        t1.start();
        t2.start();

        long pid = ProcessHandle.current().pid();
        System.out.println("\n制造死锁中...");
        System.out.println(">>> 现在另开终端执行：jstack " + pid);
        System.out.println(">>> 或：jcmd " + pid + " Thread.print | findstr /C:\"deadlock\"");
        System.out.println(">>> 期待看到：Found one Java-level deadlock\n");

        sleep(2000);
        detectByMxBean();

        System.out.println("\n程序保持运行，方便你执行 jstack；按 Ctrl+C 退出。");
        sleep(60_000);
    }

    /** 用 JMX 自己检测死锁（Arthas / APM 工具的原理就是它） */
    static void detectByMxBean() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] deadlocked = bean.findDeadlockedThreads();
        if (deadlocked == null || deadlocked.length == 0) {
            System.out.println("未检测到死锁");
            return;
        }
        System.out.println("========== 检测到死锁线程 " + deadlocked.length + " 个 ==========");
        for (long id : deadlocked) {
            ThreadInfo info = bean.getThreadInfo(id, 5);
            System.out.println("线程：" + info.getThreadName());
            System.out.println("  状态   ：" + info.getThreadState());
            System.out.println("  等待锁 ：" + info.getLockName());
            System.out.println("  被谁持有：" + info.getLockOwnerName());
            System.out.println("  栈     ：");
            for (StackTraceElement e : info.getStackTrace()) {
                System.out.println("      " + e);
            }
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
