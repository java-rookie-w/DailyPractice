package org.wang.jvmlab.thread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 面试核心: synchronized、volatile、wait/notify、死锁
 * 每个场景一个 main, 独立运行, 注释即答案
 */
public class SyncAndVolatileDemo {

    // ==================== 1. synchronized 三种用法 ====================

    /** 对象锁: 同一实例共享一把锁 */
    static class SyncInstance {
        private int count;

        public synchronized void increment() {
            count++;
        }

        public void run() throws InterruptedException {
            SyncInstance obj = new SyncInstance();
            CountDownLatch latch = new CountDownLatch(2);
            for (int i = 0; i < 2; i++) {
                new Thread(() -> {
                    for (int j = 0; j < 10000; j++) obj.increment();
                    latch.countDown();
                }).start();
            }
            latch.await();
            System.out.println("synchronized实例方法结果: " + obj.count); // 20000, 线程安全
        }
    }

    /** 类锁: 所有实例共享一把锁 */
    static class SyncStatic {
        private static int count;

        public static synchronized void increment() {
            count++;
        }

        public void run() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            // 不同对象, 同一类锁
            SyncStatic a = new SyncStatic();
            SyncStatic b = new SyncStatic();
            new Thread(() -> {
                for (int i = 0; i < 10000; i++) a.increment();
                latch.countDown();
            }).start();
            new Thread(() -> {
                for (int i = 0; i < 10000; i++) b.increment();
                latch.countDown();
            }).start();
            latch.await();
            System.out.println("synchronized静态方法结果: " + SyncStatic.count); // 20000
        }
    }

    /** 代码块锁: 锁任意对象, 粒度更细 */
    static class SyncBlock {
        private final Object lock = new Object();
        private int count;

        public void increment() {
            synchronized (lock) {
                count++;
            }
        }

        public void run() throws InterruptedException {
            SyncBlock obj = new SyncBlock();
            CountDownLatch latch = new CountDownLatch(2);
            for (int i = 0; i < 2; i++) {
                new Thread(() -> {
                    for (int j = 0; j < 10000; j++) obj.increment();
                    latch.countDown();
                }).start();
            }
            latch.await();
            System.out.println("synchronized代码块结果: " + obj.count); // 20000
        }
    }

    // ==================== 2. volatile 可见性 ====================

    /**
     * 无 volatile: 线程B 可能永远看不到 线程A 对 flag 的修改 (JIT 将 flag 缓存在寄存器/CPU cache)
     * 有 volatile: 写立即刷新到主存, 读强制从主存读取 (happens-before)
     */
    static class VolatileVisibility {
        // 去掉 volatile 大概率死循环, 加上则 1 秒内退出
        private /*volatile*/ boolean flag = true;

        public void run() throws InterruptedException {
            new Thread(() -> {
                while (flag) {
                    // 注意: 如果这里有 System.out.println (含 synchronized), 会意外刷新缓存
                    // 导致即使没有 volatile 也可能退出, 这是面试常见追问
                }
                System.out.println("子线程: 检测到 flag=false, 退出循环");
            }).start();

            Thread.sleep(1000);
            flag = false;
            System.out.println("主线程: flag 已设为 false");
        }
    }

    // ==================== 3. volatile 不保证原子性 ====================

    /**
     * volatile 只保证可见性和禁止指令重排, 不保证原子性
     * count++ 是三步操作(读-改-写), 多线程会丢更新
     */
    static class VolatileNotAtomic {
        private volatile int count;

        public void run() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(10);
            for (int i = 0; i < 10; i++) {
                new Thread(() -> {
                    for (int j = 0; j < 1000; j++) count++;
                    latch.countDown();
                }).start();
            }
            latch.await();
            System.out.println("volatile count++ 结果: " + count + " (期望10000, 说明不保证原子性)");
        }
    }

    // ==================== 4. DCL 单例: 为什么必须 volatile ====================

    /**
     * instance = new DclSingleton() 分三步:
     *   1. 分配内存
     *   2. 初始化对象
     *   3. instance 指向内存地址
     * 2 和 3 可能被指令重排, 导致其他线程拿到未初始化完成的对象.
     * volatile 禁止该重排序.
     */
    static class DclSingleton {
        private static volatile DclSingleton instance;

        private DclSingleton() {}

        public static DclSingleton getInstance() {
            if (instance == null) {                          // 第一次检查, 避免不必要的加锁
                synchronized (DclSingleton.class) {
                    if (instance == null) {                  // 第二次检查, 保证只创建一次
                        instance = new DclSingleton();
                    }
                }
            }
            return instance;
        }

        public void run() {
            System.out.println("DCL 单例: " + getInstance().hashCode());
        }
    }

    // ==================== 5. wait/notify 生产者消费者 ====================

    static class WaitNotify {
        private final Object lock = new Object();
        private int product;

        public void produce() {
            synchronized (lock) {
                while (product >= 5) { // 用 while 不用 if, 防止虚假唤醒
                    try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                product++;
                System.out.println(Thread.currentThread().getName() + " 生产, 当前: " + product);
                lock.notifyAll();
            }
        }

        public void consume() {
            synchronized (lock) {
                while (product <= 0) {
                    try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                product--;
                System.out.println(Thread.currentThread().getName() + " 消费, 当前: " + product);
                lock.notifyAll();
            }
        }

        public void run() {
            WaitNotify demo = new WaitNotify();
            new Thread(() -> { for (int i = 0; i < 10; i++) demo.produce(); }, "生产者").start();
            new Thread(() -> { for (int i = 0; i < 10; i++) demo.consume(); }, "消费者").start();
        }
    }

    // ==================== 6. 死锁 ====================

    static class DeadLock {
        private final Object lockA = new Object();
        private final Object lockB = new Object();

        public void deadlock() {
            new Thread(() -> {
                synchronized (lockA) {
                    System.out.println("线程A 持有 lockA");
                    try { Thread.sleep(50); } catch (InterruptedException e) {}
                    synchronized (lockB) {
                        System.out.println("线程A 持有 lockB");
                    }
                }
            }, "线程A").start();

            new Thread(() -> {
                synchronized (lockB) {
                    System.out.println("线程B 持有 lockB");
                    try { Thread.sleep(50); } catch (InterruptedException e) {}
                    synchronized (lockA) {
                        System.out.println("线程B 持有 lockA");
                    }
                }
            }, "线程B").start();
        }

        public void run() {
            deadlock();
            System.out.println("死锁演示: 程序不会正常退出, 可用 jstack <pid> 查看");
        }
    }

    // ==================== 入口: 依次运行 ====================

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 1. synchronized 实例方法 ==========");
        new SyncInstance().run();

        System.out.println("\n========== 2. synchronized 静态方法 ==========");
        new SyncStatic().run();

        System.out.println("\n========== 3. synchronized 代码块 ==========");
        new SyncBlock().run();

        System.out.println("\n========== 4. volatile 可见性 (去注释 volatile 对比) ==========");
        new VolatileVisibility().run();
        Thread.sleep(3000); // 给可见性演示足够时间

        System.out.println("\n========== 5. volatile 不保证原子性 ==========");
        new VolatileNotAtomic().run();

        System.out.println("\n========== 6. DCL 单例 + volatile ==========");
        new DclSingleton().run();

        System.out.println("\n========== 7. wait/notify 生产者消费者 ==========");
        new WaitNotify().run();
        Thread.sleep(1000);

        System.out.println("\n========== 8. 死锁演示 ==========");
        new DeadLock().run();
        System.out.println("(主线程退出, 死锁线程仍在运行)");
    }

    // ==================== 附录: 面试速记 ====================
    /*
     * synchronized:
     *   - 底层: monitorenter/monitorexit 指令, 锁信息在对象头 Mark Word
     *   - 锁升级: 无锁 → 偏向锁 → 轻量级锁(CAS) → 重量级锁(os mutex), 不可逆降级
     *   - 保证: 原子性 + 可见性 + 有序性(但内部可指令重排)
     *
     * volatile:
     *   - 底层: lock 前缀指令, 写操作触发缓存行写回+失效(MESI), 相当于内存屏障
     *   - 保证: 可见性 + 有序性(禁止指令重排), 不保证原子性
     *   - 适用: 状态标志位, DCL 单例
     *
     * wait vs sleep:
     *   - wait: Object 方法, 释放锁, 在 synchronized 块中调用, 用 notify 唤醒
     *   - sleep: Thread 方法, 不释放锁, 任意位置调用, 时间到自动醒
     *
     * 死锁四条件: 互斥, 持有并等待, 不可剥夺, 循环等待 — 破任一即可
     */
}
