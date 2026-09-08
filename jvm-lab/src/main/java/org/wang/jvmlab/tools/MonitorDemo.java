package org.wang.jvmlab.tools;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * 【考点 18 / 22】用 MXBean 采集 JVM 运行时指标
 *
 * 【运行】
 *   java org.wang.jvmlab.tools.MonitorDemo
 *
 * 【预期现象】
 *   打印堆/非堆内存、各内存池、GC 统计、线程数，以及当前 JVM 的启动参数。
 *
 * 【面试要点】
 *   1. 这套 API 就是 Arthas、Spring Boot Actuator、各类 APM 的数据来源（JMX）。
 *   2. 监控要采集的四类指标：内存占用、GC 次数与耗时、线程数、类加载数。
 *   3. 判断健康的关键信号：
 *      - Full GC 次数应接近 0；
 *      - 老年代回收后能降下来（锯齿形），底部不断抬高 = 泄漏；
 *      - 线程数持续增长不回落 = 线程池配置或线程泄漏问题。
 *   4. 别只看平均值：GC 停顿要看 P99（G1 的 MaxGCPauseMillis 也是尽力而为，不是硬保证）。
 */
public class MonitorDemo {

    public static void main(String[] args) throws Exception {
        printRuntimeInfo();
        printHeapInfo();
        printMemoryPools();
        printGcInfo();
        printThreadInfo();

        System.out.println("\n========== 泄漏判据：GC 后能否降下来 ==========");
        long before = usedMb();
        byte[][] data = new byte[200][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new byte[1024 * 1024];
        }
        long after = usedMb();
        System.out.printf("  分配 200MB 后        ：堆已用 %d MB → %d MB%n", before, after);

        // 情形一：引用还在，GC 无能为力
        System.gc();
        sleep(300);
        long stillReferenced = usedMb();
        System.out.printf("  GC 后（引用还在）    ：%d MB —— 降不下来，因为对象仍然可达%n",
                stillReferenced);

        // 情形二：去掉引用，GC 立刻回收
        data = null;
        System.gc();
        sleep(300);
        long released = usedMb();
        System.out.printf("  GC 后（引用已去掉）  ：%d MB —— 被回收 %d MB%n",
                released, stillReferenced - released);
        System.out.println("  >>> 线上判据：每次 GC 后老年代底部不断抬高 = 对象一直可达 = 泄漏");
    }

    static void printRuntimeInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        System.out.println("========== 运行时信息 ==========");
        System.out.println("  JVM      ：" + runtime.getVmName() + " " + runtime.getVmVersion());
        System.out.println("  启动参数：" + runtime.getInputArguments());
    }

    static void printHeapInfo() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        System.out.println("\n========== 堆 / 非堆 ==========");
        printUsage("  Heap    ", heap);
        printUsage("  NonHeap ", nonHeap);
    }

    static void printMemoryPools() {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        System.out.println("\n========== 内存池明细 ==========");
        for (MemoryPoolMXBean pool : pools) {
            System.out.printf("  %-32s %-12s ", pool.getName(), pool.getType());
            printUsage("", pool.getUsage());
        }
    }

    static void printGcInfo() {
        System.out.println("\n========== GC 统计 ==========");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.printf("  %-26s 次数=%-5d 累计耗时=%d ms%n",
                    gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        }
    }

    static void printThreadInfo() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        System.out.println("\n========== 线程 ==========");
        System.out.println("  当前线程数：" + threads.getThreadCount());
        System.out.println("  峰值线程数：" + threads.getPeakThreadCount());
        System.out.println("  守护线程数：" + threads.getDaemonThreadCount());
        System.out.println("  累计启动过：" + threads.getTotalStartedThreadCount());
    }

    static long usedMb() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1024 / 1024;
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void printUsage(String prefix, MemoryUsage usage) {
        System.out.printf("%s used=%-8d MB committed=%-8d MB max=%s%n",
                prefix,
                usage.getUsed() / 1024 / 1024,
                usage.getCommitted() / 1024 / 1024,
                usage.getMax() < 0 ? "无限制" : usage.getMax() / 1024 / 1024 + " MB");
    }
}
