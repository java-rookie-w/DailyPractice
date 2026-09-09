package org.wang.jvmlab.gc;

import java.lang.ref.Cleaner;

/**
 * 【考点 7】finalize() 为什么被废弃，以及官方替代方案 Cleaner
 *
 * 【运行】
 *   java org.wang.jvmlab.gc.CleanerDemo
 *
 * 【预期现象】
 *   对象失去引用后，执行一次 System.gc()，Cleaner 注册的清理动作被触发，
 *   打印"资源已释放"。整个过程不需要重写 finalize()。
 *
 * 【面试要点】
 *   1. finalize() 的问题：调用时机不确定、可能严重拖慢 GC、异常会被吞掉、
 *      对象还能在 finalize 里"复活"、JDK 9 标记废弃、JDK 18 标记 for-removal。
 *   2. 正解是 try-with-resources（AutoCloseable），那是"确定性释放"；
 *      Cleaner 只是**兜底**，不能替代显式关闭。
 *   3. Cleaner 的底层就是 PhantomReference + ReferenceQueue，
 *      清理动作跑在专门的 Cleaner 线程上，不阻塞业务线程。
 *   4. DirectByteBuffer 和 Netty 的堆外内存释放用的就是这套机制（JDK 内部叫 Deallocator）。
 */
public class CleanerDemo {

    public static void main(String[] args) throws Exception {
        Cleaner cleaner = Cleaner.create();

        System.out.println("创建一个持有「假资源」的对象，并注册清理动作");
        Resource resource = new Resource(cleaner, "连接-001");
        resource.use();

        System.out.println("置空引用并触发 GC...");
        resource = null;
        System.gc();
        Thread.sleep(500); // 等 Cleaner 线程跑完

        System.out.println(">>> 观察上面是否打印了「资源已释放」，那就是 Cleaner 兜底生效");
        System.out.println(">>> 对比 finalize：Cleaner 的清理逻辑由我们控制，且不会让对象复活");

        cleaner = null;
        System.gc();
        System.out.println("\n演示结束（Cleaner 线程会在无任务后退出，JVM 正常退出）");
    }

    /** 模拟一个持有外部资源（如文件句柄、堆外内存）的对象 */
    static class Resource implements AutoCloseable {

        private final String name;
        private final Cleaner.Cleanable cleanable;

        Resource(Cleaner cleaner, String name) {
            this.name = name;
            // 注册清理动作；注意 State 不能持有 Resource 引用，否则永远不可达
            this.cleanable = cleaner.register(this, new State(name));
        }

        void use() {
            System.out.println("  使用资源：" + name);
        }

        @Override
        public void close() {
            cleanable.clean(); // 显式关闭：确定性释放
        }

        /** 清理状态类必须是 static 的，且不能引用被清理对象 */
        static class State implements Runnable {
            private final String name;

            State(String name) {
                this.name = name;
            }

            @Override
            public void run() {
                System.out.println("  [Cleaner] 资源已释放：" + name);
            }
        }
    }
}
