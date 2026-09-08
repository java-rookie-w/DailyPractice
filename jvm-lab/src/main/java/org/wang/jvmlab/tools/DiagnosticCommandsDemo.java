package org.wang.jvmlab.tools;

/**
 * 【考点 18 / 22】诊断命令与 JVM 参数速查（纯打印，随时可跑）
 *
 * 【运行】
 *   java org.wang.jvmlab.tools.DiagnosticCommandsDemo
 *
 * 【说明】
 *   这是一份"能跑起来的速查表"：不制造任何现象，只把面试和排障时
 *   最常用的命令与参数打印出来，方便临考前扫一遍。
 */
public class DiagnosticCommandsDemo {

    public static void main(String[] args) {
        System.out.println("========== 一、进程与线程 ==========");
        line("jps -l", "列出本机所有 Java 进程及主类（排障第一步）");
        line("jps -lv", "额外显示 JVM 启动参数，确认线上到底配了什么");
        line("jstack <pid>", "打印线程栈：查死锁、查线程卡在哪、定位 CPU 高");
        line("jstack <pid> > s.txt", "抓 3 次间隔 5 秒，对比看线程是否一直卡在同一处");
        line("jcmd <pid> Thread.print", "jstack 的推荐替代（jcmd 是官方新一代工具）");

        System.out.println("\n========== 二、内存与 GC ==========");
        line("jstat -gcutil <pid> 1000", "每秒打印各区占用百分比 + GC 次数/耗时（最常用）");
        line("jstat -gc <pid> 1000", "打印各区容量实际字节数，看分配速率");
        line("jmap -heap <pid>", "堆配置与当前使用概览");
        line("jmap -histo <pid> | head -30", "不看 dump 也能快速找出「哪个类对象最多」");
        line("jmap -dump:format=b,file=heap.hprof <pid>", "导出堆转储（会 STW，生产慎用）");
        line("jcmd <pid> VM.native_memory", "看堆外内存（需先加 -XX:NativeMemoryTracking=detail）");

        System.out.println("\n========== 三、GC 日志参数 ==========");
        line("JDK 8", "-Xloggc:/path/gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps");
        line("JDK 9+", "-Xlog:gc*:file=/path/gc.log:time,uptime:filecount=5,filesize=20m");
        line("注意", "-XX:+PrintGCDetails 在 JDK 9+ 已废弃，会被自动改写成 -Xlog:gc*");

        System.out.println("\n========== 四、生产基线参数 ==========");
        line("-Xms=-Xmx", "堆上下限相等，避免运行中扩容/收缩带来的抖动");
        line("-XX:MaxRAMPercentage=70", "容器里用它代替写死 -Xmx，让 JVM 感知 cgroup 限制");
        line("-XX:MaxMetaspaceSize=256m", "给元空间设上限，否则能吃光物理内存");
        line("-XX:+HeapDumpOnOutOfMemoryError", "OOM 时自动 dump，留证据（强烈建议常开）");
        line("-XX:HeapDumpPath=/path", "dump 文件路径，注意磁盘要够大");
        line("-Xlog:gc*:file=...", "GC 日志落盘，排障时唯一的「黑匣子」");
        line("-XX:+DisableExplicitGC", "禁止代码里的 System.gc()（用了堆外的不要加）");

        System.out.println("\n========== 五、常用调优参数 ==========");
        line("-Xmn / -XX:NewRatio", "新生代大小 / 新老比例（默认 NewRatio=2）");
        line("-XX:SurvivorRatio=8", "Eden:S0:S1 = 8:1:1");
        line("-XX:MaxTenuringThreshold=15", "晋升年龄，最大只能 15（对象头只有 4 bit）");
        line("-XX:MaxGCPauseMillis=200", "G1 的目标停顿（尽力而为，不是硬保证）");
        line("-XX:+UseG1GC / +UseZGC", "切换收集器（JDK 9+ 默认 G1）");
        line("-XX:MaxDirectMemorySize", "堆外内存上限，Netty 场景必配");

        System.out.println("\n========== 六、GC 日志看什么 ==========");
        line("停顿时间", "单次 STW 是否超过 SLA");
        line("GC 频率", "Young GC 是否过于频繁（分配速率问题）");
        line("回收前后容量", "老年代回收后能否降下来 → 判断泄漏");
        line("吞吐量", "1 - GC 时间/总时间，一般目标 99%+");
        line("GC 原因", "G1 Evacuation Pause / Humongous Allocation / Metadata GC Threshold 等");
    }

    static void line(String key, String desc) {
        System.out.printf("  %-42s %s%n", key, desc);
    }
}
