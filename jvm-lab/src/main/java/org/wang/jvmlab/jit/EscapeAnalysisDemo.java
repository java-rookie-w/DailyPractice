package org.wang.jvmlab.jit;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * 【P1-2】逃逸分析与标量替换的可观测验证
 *
 * 【运行（两种方式对比）】
 *   java org.wang.jvmlab.jit.EscapeAnalysisDemo
 *   java -XX:-DoEscapeAnalysis org.wang.jvmlab.jit.EscapeAnalysisDemo
 *   更直观：java -Xmx64m -Xlog:gc* org.wang.jvmlab.jit.EscapeAnalysisDemo
 *
 * 【预期现象】
 *   不逃逸的对象（方法内 new 完就丢）→ 开启逃逸分析时几乎不产生 GC、耗时更短；
 *   强制逃逸的对象（存进数组）→ GC 次数明显更多。
 *   加上 -XX:-DoEscapeAnalysis 后，两者的差距会缩小甚至消失。
 *
 * 【面试要点】
 *   1. 逃逸分析：JIT 判断对象是否会逃出方法/线程。不逃逸就能做三种优化：
 *      标量替换、栈上分配、同步消除（锁消除）。
 *   2. ⚠️ 高分细节：**HotSpot 实际只实现了标量替换，并没有真正的栈上分配**。
 *      所谓"栈上分配"在 HotSpot 里的表现形式就是标量替换 —— 对象直接被打散成
 *      若干个局部变量，连对象都不存在了。能说出这句，说明你读的是规范不是八股。
 *   3. 标量替换：把对象拆成若干基本类型的局部变量（Point.x / Point.y 变成两个 int），
 *      对象根本不会被创建 → 也就没有分配、没有 GC。
 *   4. 锁消除：对象不逃逸 → 不可能被别的线程访问 → 它上面的 synchronized 可以直接删掉。
 *      （验证参数：-XX:+PrintEliminateLocks，需 debug 版 JVM，了解即可）
 */
public class EscapeAnalysisDemo {

    static final int WARMUP = 5;
    static final int N = 50_000_000;

    public static void main(String[] args) {
        System.out.println("本轮参数：" + ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.println("（若看到 -XX:-DoEscapeAnalysis 说明逃逸分析已关闭）\n");

        // 预热，让 JIT 先把这两个方法编译掉，避免编译时间污染测量
        for (int i = 0; i < WARMUP; i++) {
            noEscape(1_000_000);
            escape(1_000_000);
        }

        System.gc();
        sleep(300);

        runPhase("不逃逸（方法内 new 完就丢）", EscapeAnalysisDemo::noEscape);
        runPhase("强制逃逸（存进数组）", EscapeAnalysisDemo::escape);

        System.out.println("\n结论：对比两行的 GC 次数与耗时。");
        System.out.println("  开启逃逸分析时，不逃逸版本几乎不产生 GC —— 对象被标量替换掉了。");
        System.out.println("  用 -XX:-DoEscapeAnalysis 重跑，差距会明显缩小。");
        System.out.println("注意：这是教学用的粗略对比，严谨基准请用 JMH。");
    }

    interface Allocator {
        long run(int n);
    }

    static void runPhase(String name, Allocator allocator) {
        long gcBefore = gcCount();
        long timeBefore = gcTime();
        long start = System.nanoTime();
        long sum = allocator.run(N);
        long elapsed = System.nanoTime() - start;
        long gcAfter = gcCount();
        long timeAfter = gcTime();

        System.out.printf("%-24s 耗时=%5d ms, GC 次数+%-3d, GC 耗时+%d ms (sum=%d)%n",
                name, elapsed / 1_000_000, gcAfter - gcBefore, timeAfter - timeBefore, sum);
    }

    /** 对象不逃逸：new 出来只在本方法内使用 */
    static long noEscape(int n) {
        long sum = 0;
        for (int i = 0; i < n; i++) {
            Point p = new Point(i, i + 1);
            sum += p.x + p.y;
        }
        return sum;
    }

    /** 对象逃逸：存进数组，逃出方法作用域 */
    static long escape(int n) {
        Point[] holder = new Point[1024];
        long sum = 0;
        for (int i = 0; i < n; i++) {
            Point p = new Point(i, i + 1);
            holder[i & 1023] = p;   // 让它逃出去
            sum += p.x + p.y;
        }
        return sum == 0 ? holder.length : sum;
    }

    static class Point {
        final int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    static long gcTime() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
