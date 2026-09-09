package org.wang.jvmlab.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * 【考点 9 / 18】观察 Young GC 与 Full GC，配合 GC 日志阅读
 *
 * 【运行（务必带 -Xlog，否则看不到日志）】
 *   java -Xmx64m -Xmn16m -Xlog:gc* org.wang.jvmlab.gc.GcLogDemo
 *   JDK 8 用：java -Xmx64m -Xmn16m -Xloggc:gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps ...
 *
 * 【预期现象】
 *   阶段一不停产生朝生夕灭的垃圾 → 频繁 Young GC（G1 日志里是 "Pause Young"）；
 *   阶段二把对象长期持有 → 对象晋升到老年代 → 触发 Full GC 或 Mixed GC。
 *   程序最后打印各收集器的次数与累计耗时。
 *
 * 【面试要点】
 *   1. 看 GC 日志只盯四个指标：停顿时间、GC 频率、回收前后容量、吞吐量。
 *   2. 老年代占用应该呈"锯齿"（回收后能降下来）；如果每次回收后底部不断抬高，
 *      就是内存泄漏的信号。
 *   3. GC 频繁不等于要调参 —— 先问两个问题：是分配速率太高，还是对象本该早点死？
 *      能改代码解决的，别动参数。
 *   4. 吞吐量的定义：1 - GC 时间 / 总运行时间。
 */
public class GcLogDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("阶段一：持续制造短命对象，观察 Young GC");
        long youngGcBefore = youngGcCount();
        for (int i = 0; i < 200; i++) {
            byte[] garbage = new byte[1024 * 512]; // 0.5MB，用完即弃
            if (garbage[0] == 1) {
                System.out.println("不会执行");
            }
            Thread.sleep(5);
        }
        System.out.println("  本阶段 Young GC 次数增量 = " + (youngGcCount() - youngGcBefore));

        System.out.println("\n阶段二：持有对象不放，逼出晋升与 Full GC");
        long fullGcBefore = fullGcCount();
        List<byte[]> longLived = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            longLived.add(new byte[1024 * 512]);
            Thread.sleep(5);
            if (longLived.size() > 60) {
                longLived.remove(0); // 留住一部分，制造"存活对象"以触发晋升
            }
        }
        System.out.println("  本阶段 Full/Old GC 次数增量 = " + (fullGcCount() - fullGcBefore));

        System.out.println("\n阶段三：显式 System.gc()（生产环境应加 -XX:+DisableExplicitGC）");
        System.gc();

        System.out.println("\n========== 收集器统计 ==========");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.printf("  %-24s 次数=%-4d 累计耗时=%d ms%n",
                    gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        }
        System.out.println("\n提示：把 -Xlog:gc* 的输出对着日志逐行看，重点关注");
        System.out.println("  GC 原因(如 G1 Evacuation Pause)、回收前后容量、停顿毫秒数。");
    }

    static long youngGcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .filter(b -> b.getName().toLowerCase().contains("young"))
                .mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    static long fullGcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .filter(b -> b.getName().toLowerCase().contains("old")
                        || b.getName().toLowerCase().contains("full"))
                .mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }
}
