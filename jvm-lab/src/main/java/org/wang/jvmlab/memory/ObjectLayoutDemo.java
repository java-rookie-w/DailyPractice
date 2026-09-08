package org.wang.jvmlab.memory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * 【考点 4】对象内存布局与浅堆大小估算
 *
 * 【运行】
 *   java org.wang.jvmlab.memory.ObjectLayoutDemo
 *   java -XX:-UseCompressedOops org.wang.jvmlab.memory.ObjectLayoutDemo   （对比：关掉压缩指针）
 *
 * 【预期现象】
 *   打印各类对象的估算浅堆大小，通常是 16 / 24 / 32 这样 8 的倍数 —— 这就是对齐填充。
 *   关掉压缩指针后，同样的对象会**变大**（对象头从 12 字节变成 16 字节）。
 *
 * 【面试要点】
 *   1. 布局 = 对象头 + 实例数据 + 对齐填充。
 *      对象头 = Mark Word（8 字节，存 hash / GC 年龄 / 锁标志）+ 类型指针
 *      （开启压缩指针时 4 字节）；数组对象还多 4 字节记录长度。
 *   2. 对象头里的 GC 年龄只有 4 bit，所以 -XX:MaxTenuringThreshold 最大只能是 15。
 *   3. 压缩指针 -XX:+UseCompressedOops 默认开启，堆超过 32GB 时自动失效 ——
 *      这就是"堆配到 31GB 性价比最高，要么干脆上 48GB+"的原因。
 *   4. 严谨的对象布局请用 JOL（org.openjdk.jol:jol-core），本实验用批量分配的
 *      堆占用差值来估算。实测绝对值含约 4 字节的系统偏差（GC 分配粒度所致），
 *      **请以差值为准**（关压缩指针的差值、加字段的差值都是准的）。
 *      建议加 -XX:+UseSerialGC 让数值更稳定。
 */
public class ObjectLayoutDemo {

    static final int N = 2_000_000;

    public static void main(String[] args) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        System.out.println("是否开启压缩指针：" + compressedOopsHint());
        System.out.println("每次用 " + N + " 个对象取平均，结果四舍五入到字节\n");

        measure("Object（无字段）", Object[]::new, i -> new Object(), memory);
        measure("Empty（空类）", Empty[]::new, i -> new Empty(), memory);
        measure("OneInt（1 个 int）", OneInt[]::new, i -> new OneInt(), memory);
        measure("LongAndInt（long + int）", LongAndInt[]::new, i -> new LongAndInt(), memory);
        measure("WithRef（1 个引用）", WithRef[]::new, i -> new WithRef(), memory);

        System.out.println("\n========== 怎么看这份数据 ==========");
        System.out.println("  绝对值含约 4 字节的测量偏差（GC 分配粒度导致），**请以差值为准**：");
        System.out.println("  1) 关闭压缩指针后，同样的对象会变大 —— 差值就是对象头里类型指针 4B → 8B；");
        System.out.println("  2) LongAndInt 比 Empty 大约多 8 字节 —— 正好是一个 long 字段；");
        System.out.println("  3) 所有大小都是 8 的倍数 —— 对齐填充；字段顺序会被 JVM 重排以减少填充。");
        System.out.println("  再跑一次对比：java -XX:+UseSerialGC -XX:-UseCompressedOops ... （SerialGC 下数值更稳定）");
    }

    interface Factory<T> {
        T make(int i);
    }

    static <T> void measure(String name, java.util.function.IntFunction<T[]> arrayFactory,
                            Factory<T> factory, MemoryMXBean memory) {
        // 关键：对比两个"GC 之后的稳态"，而不是"GC 前 vs GC 后"。
        // 后者会被尚未回收的垃圾污染（实测能算出负数）。
        T[] array = arrayFactory.apply(N);
        for (int i = 0; i < N; i++) {
            array[i] = factory.make(i);
        }
        long withObjects = usedAfterGc(memory);   // 稳态 A：数组 + N 个对象

        for (int i = 0; i < N; i++) {
            array[i] = null;                      // 只清引用，数组本身还在
        }
        long arrayOnly = usedAfterGc(memory);     // 稳态 B：只有数组

        long per = Math.round((withObjects - arrayOnly) / (double) N);
        System.out.printf("  %-24s 单个约 %d 字节%n", name, per);
    }

    /** 连续 GC 两次再取堆占用，得到一个相对干净的稳态值 */
    static long usedAfterGc(MemoryMXBean memory) {
        for (int i = 0; i < 2; i++) {
            System.gc();
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return memory.getHeapMemoryUsage().getUsed();
    }

    private static String compressedOopsHint() {
        long max = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
        long gb = max / (1024 * 1024 * 1024);
        return "当前堆上限约 " + gb + " GB（>32GB 时压缩指针自动失效）";
    }

    static class Empty {
    }

    static class OneInt {
        int a;
    }

    static class LongAndInt {
        long l;
        int i;
    }

    static class WithRef {
        Object o;
    }
}
