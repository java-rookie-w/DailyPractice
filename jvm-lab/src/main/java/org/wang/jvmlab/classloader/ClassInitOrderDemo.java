package org.wang.jvmlab.classloader;

/**
 * 【考点 19】类初始化顺序与"被动引用"
 *
 * 【运行】
 *   java org.wang.jvmlab.classloader.ClassInitOrderDemo
 *
 * 【预期现象】
 *   第一部分：new 一个子类 → 父静态 → 子静态 → 父实例块 → 父构造 → 子实例块 → 子构造
 *   第二部分：三种被动引用，全部**不触发**子类/目标类初始化
 *
 * 【面试要点】
 *   1. 完整流程：加载 → 验证 → 准备 → 解析 → 初始化 → 使用 → 卸载。
 *   2. **准备**阶段只给静态变量分配内存并设零值（static int a = 1 此时 a = 0），
 *      **初始化**阶段才执行 <clinit>（静态赋值 + 静态块合并成的方法）。
 *   3. <clinit> 是线程安全的：JVM 会加锁保证一个类只被初始化一次，
 *      这也是"静态内部类单例模式"能天然线程安全的原因。
 *   4. 初始化时机（主动引用）：new、读写静态字段（非常量）、调用静态方法、
 *      反射调用、子类初始化触发父类、启动类。
 *   5. 被动引用（不触发初始化）：子类引用父类静态字段、数组定义引用类、
 *      使用编译期常量（常量已进入调用方的常量池）。
 */
public class ClassInitOrderDemo {

    public static void main(String[] args) {
        System.out.println("========== 1. 初始化顺序（new 一个子类）==========");
        new ChildA();

        System.out.println("\n========== 2. 被动引用：通过子类访问父类静态字段 ==========");
        System.out.println("  读取 ChildB.parentValue = " + ChildB.parentValue);
        System.out.println("  ↑ 只看到 ParentB 初始化，ChildB 没初始化 ✓");

        System.out.println("\n========== 3. 被动引用：定义数组 ==========");
        ParentB[] array = new ParentB[3];
        System.out.println("  创建了 " + array.length + " 长度的数组，没有触发任何类初始化 ✓");

        System.out.println("\n========== 4. 被动引用：编译期常量 ==========");
        System.out.println("  读取 ConstHolder.CONST = " + ConstHolder.CONST);
        System.out.println("  ↑ 常量在编译期就进了调用方常量池，ConstHolder 没初始化 ✓");
    }

    // ---------- 第一部分：顺序演示 ----------

    static class ParentA {
        static {
            System.out.println("  1. 父类静态块");
        }
        {
            System.out.println("  3. 父类实例块");
        }

        ParentA() {
            System.out.println("  4. 父类构造");
        }
    }

    static class ChildA extends ParentA {
        static {
            System.out.println("  2. 子类静态块");
        }
        {
            System.out.println("  5. 子类实例块");
        }

        ChildA() {
            System.out.println("  6. 子类构造");
        }
    }

    // ---------- 第二部分：被动引用演示 ----------

    static class ParentB {
        static int parentValue = 10;

        static {
            System.out.println("  [ParentB 已初始化]");
        }
    }

    static class ChildB extends ParentB {
        static {
            System.out.println("  [ChildB 已初始化]  ← 如果出现这行，说明你的理解有误");
        }
    }

    static class ConstHolder {
        static final int CONST = 99;

        static {
            System.out.println("  [ConstHolder 已初始化]  ← 如果出现这行，说明常量没被内联");
        }
    }
}
