package org.wang.jvmlab.tools;

import java.util.concurrent.TimeUnit;

/**
 * 【P1-8】CPU 100% 的复现与定位演练
 *
 * 【运行】
 *   java org.wang.jvmlab.tools.CpuHighDemo
 *
 * 【定位步骤（Linux）】
 *   1. top                          → 找到 CPU 高的 Java 进程 PID
 *   2. top -Hp <pid>                → 找到 CPU 高的线程 TID（十进制）
 *   3. printf "%x\n" <tid>          → 转成 16 进制（jstack 里的 nid 是 16 进制）
 *   4. jstack <pid> | grep -A 20 <nid16>   → 看这个线程在干什么
 *   5. 如果是 GC 线程占 CPU → 配合 jstat -gcutil 判断是不是频繁 GC
 *
 * 【Windows】
 *   任务管理器看不到 Java 线程 ID，用 jconsole / VisualVM / Arthas 更省事：
 *   jcmd <pid> Thread.print > thread.txt，再找 nid。
 *
 * 【面试要点】
 *   1. 三步法：找进程 → 找线程 → 看堆栈。答出"转 16 进制"这个细节很加分。
 *   2. CPU 高的三类常见原因：死循环（如本实验）、频繁 GC、大量线程上下文切换。
 *   3. 如果 CPU 高伴随频繁 Full GC，那瓶颈不是线程代码，而是**内存**：
 *      先看 jstat -gcutil 的 FGC/FGCT，再看老年代是不是下不来（泄漏）。
 *   4. 反过来：如果 GC 正常但 CPU 高，就看是不是死循环、正则回溯、序列化热点。
 */
public class CpuHighDemo {

    public static void main(String[] args) throws Exception {
        long pid = ProcessHandle.current().pid();
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

        System.out.println("当前进程 pid = " + pid);
        System.out.println("启动 " + threads + " 个忙循环线程制造 CPU 压力");
        System.out.println("另开终端执行：top -Hp " + pid + "  （Windows 用 jcmd 或 VisualVM）\n");

        for (int i = 0; i < threads; i++) {
            final int id = i;
            Thread t = new Thread(() -> {
                long n = 0;
                while (true) {
                    n += (n % 3 == 0) ? 1 : 2;   // 纯计算，没有 IO、没有 sleep
                    if (n < 0) {
                        System.out.println("unreachable " + n);
                    }
                }
            }, "busy-loop-" + id);
            t.setDaemon(true);
            t.start();
        }

        // 再来一个"正常"线程做对照：它大部分时间在 sleep，几乎不占 CPU
        Thread normal = new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "sleeping-thread");
        normal.setDaemon(true);
        normal.start();

        System.out.println("忙循环已启动，持续 120 秒。观察要点：");
        System.out.println("  - busy-loop-* 线程 CPU 接近 100%");
        System.out.println("  - sleeping-thread 接近 0%");
        System.out.println("  - 用 jstack 抓到的 busy-loop 线程栈，栈顶就在本类的 lambda 里");
        Thread.sleep(120_000);
    }
}
