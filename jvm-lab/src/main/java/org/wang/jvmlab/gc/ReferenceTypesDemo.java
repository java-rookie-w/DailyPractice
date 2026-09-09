package org.wang.jvmlab.gc;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 【考点 6】四种引用：强、软、弱、虚
 *
 * 【运行】
 *   java -Xmx64m org.wang.jvmlab.gc.ReferenceTypesDemo
 *
 * 【预期现象】
 *   强引用：一直活着
 *   软引用：内存充足时存活，堆被压到快满时被回收
 *   弱引用：一次 System.gc() 后即被回收
 *   虚引用：get() 永远返回 null，对象被回收后进入 ReferenceQueue
 *
 * 【面试要点】
 *   1. 软引用的回收时机是"内存不足时"，不是"下次 GC"—— 本实验靠持续申请内存来制造压力，
 *      这样才看得到它被回收。只调一次 System.gc() 软引用通常还在。
 *   2. 软引用适合做缓存，弱引用适合做"不影响生命周期的附属信息"
 *      （典型：WeakHashMap、ThreadLocalMap 的 key）。
 *   3. ThreadLocal 泄漏就出在这里：key 是弱引用会被回收，但 value 是强引用还在，
 *      且线程池里的线程长期存活 → value 永远可达 → 泄漏。解法：用完 remove()。
 *   4. 虚引用拿不到对象（get() 恒为 null），唯一作用是"对象被回收时收到通知"，
 *      用来释放堆外资源；JDK 9 起更推荐直接用 Cleaner（见 CleanerDemo）。
 */
public class ReferenceTypesDemo {

    public static void main(String[] args) {
        strongRef();
        weakRef();
        phantomRef();
        softRef();
    }

    /** 强引用：只要引用还在，永不回收 */
    static void strongRef() {
        System.out.println("\n【强引用】");
        Object obj = new Object();
        System.gc();
        System.out.println("  GC 后对象仍在：" + (obj != null));
    }

    /** 弱引用：下一次 GC 必定回收 */
    static void weakRef() {
        System.out.println("\n【弱引用】");
        Object target = new Object();
        WeakReference<Object> weak = new WeakReference<>(target);
        System.out.println("  GC 前：" + (weak.get() != null ? "存活" : "已回收"));
        target = null;                 // 只剩弱引用
        System.gc();
        System.out.println("  GC 后：" + (weak.get() != null ? "存活（少见）" : "已回收 ✓"));
    }

    /** 虚引用：get() 恒为 null，被回收后进入队列 */
    static void phantomRef() {
        System.out.println("\n【虚引用】");
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object target = new Object();
        PhantomReference<Object> phantom = new PhantomReference<>(target, queue);
        System.out.println("  phantom.get() = " + phantom.get() + "（恒为 null，拿不到对象）");
        target = null;
        System.gc();
        Reference<?> polled = queue.poll();
        System.out.println("  对象被回收后是否入队：" + (polled != null ? "是 ✓" : "尚未（可多试几次）"));
    }

    /** 软引用：内存不足时才回收 */
    static void softRef() {
        System.out.println("\n【软引用】");
        SoftReference<byte[]> soft = new SoftReference<>(new byte[8 * 1024 * 1024]);
        System.gc();
        System.out.println("  GC 后（内存还够）：" + (soft.get() != null ? "存活 ✓" : "已回收"));

        List<byte[]> pressure = new ArrayList<>();
        System.out.println("  开始申请内存施压...");
        int mb = 0;
        try {
            while (soft.get() != null) {
                pressure.add(new byte[1024 * 1024]);
                mb++;
            }
            System.out.println("  申请 " + mb + " MB 后，软引用被回收 ✓");
        } catch (OutOfMemoryError e) {
            System.out.println("  堆先撑不住了（" + mb + " MB），软引用状态："
                    + (soft.get() != null ? "仍存活" : "已回收"));
        }
    }
}
