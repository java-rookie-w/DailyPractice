package org.wang.jvmlab.jit;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * 【P1-1】JIT 分层编译的观察（非严谨基准，重点看趋势）
 *
 * 【运行】
 *   java -XX:+PrintCompilation org.wang.jvmlab.jit.JitCompilationDemo
 *   （编译日志很吵，用 grep 过滤：... | grep -E "hotMethod|\[round\]"）
 *
 * 【预期现象】
 *   -XX:+PrintCompilation 的输出里会看到同一个方法出现多次，注释列依次是
 *   3（C1 完全优化）、4（C2）、% 表示 OSR（栈上替换）；
 *   本程序打印的每轮耗时会随着调用次数增加而下降，最后趋于稳定。
 *
 * 【面试要点】
 *   1. 分层编译（Tiered，JDK 8 起默认）：
 *      解释执行 → C1 编译（快，优化少，带 profiling）→ C2 编译（慢，优化狠）。
 *      之所以分两层，是为了兼顾启动速度和峰值性能。
 *   2. 热点探测两种方式：方法调用计数器 + 回边计数器（循环体）。
 *      循环体触发的编译叫 **OSR（On-Stack Replacement）**，即边跑边替换栈帧里的代码。
 *   3. 编译产物放 Code Cache，默认 240MB；满了会打印
 *      "CodeCache is full. Compiler disabled"，然后性能断崖式下跌。
 *      用 -XX:ReservedCodeCacheSize 调大，或 -XX:+UseCodeCacheFlushing 让冷代码被冲刷。
 *   4. ⚠️ 别把这种手工计时当成性能基准：没有预热轮次、没有多轮取中位数、
 *      没有屏蔽死代码消除，结果只能看趋势。真正做基准请用 JMH。
 */
public class JitCompilationDemo {

    public static void main(String[] args) {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        System.out.println("JVM 参数：" + runtime.getInputArguments());
        System.out.println("若未加 -XX:+PrintCompilation，重跑时加上可以看到编译过程\n");

        System.out.println("开始分轮调用同一个方法，观察耗时变化：");
        System.out.println("轮次\t调用次数\t累计\t耗时(ms)");

        int loops = 20;
        int perLoop = 1_000_000;
        long total = 0;
        for (int i = 1; i <= loops; i++) {
            long start = System.nanoTime();
            total += hotMethod(perLoop);
            long elapsed = System.nanoTime() - start;
            // 输出刻意带 [round] 前缀：-XX:+PrintCompilation 的日志很吵，
            // 可以用 grep round / grep hotMethod 过滤出自己关心的部分
            System.out.printf("[round] %2d\t每次=%d\t累计=%d\t耗时=%d ms%n",
                    i, perLoop, (long) i * perLoop, elapsed / 1_000_000);
        }

        System.out.println("\n观察：前几轮较慢（解释执行 / C1），后续明显变快并稳定（C2）。");
        System.out.println("（result=" + total + "，这行是为了防止 JIT 把计算整体消除）");
    }

    /** 一个足够"热"的方法：循环 + 分支 + 算术，给 C2 提供优化素材 */
    static long hotMethod(int n) {
        long result = 0;
        for (int i = 0; i < n; i++) {
            if ((i & 1) == 0) {
                result += i * 3 + 1;
            } else {
                result -= i / 2 + 1;
            }
        }
        return result;
    }
}
