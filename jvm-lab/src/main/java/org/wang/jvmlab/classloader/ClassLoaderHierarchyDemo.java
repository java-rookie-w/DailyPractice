package org.wang.jvmlab.classloader;

/**
 * 【考点 19 / 20】三层类加载器与双亲委派
 *
 * 【运行】
 *   java org.wang.jvmlab.classloader.ClassLoaderHierarchyDemo
 *
 * 【预期现象】
 *   String 的加载器 = null（Bootstrap，C++ 实现，Java 里看不到）
 *   javax.sql.DataSource 之类 JDK 模块的类 = PlatformClassLoader
 *   我们自己的类 = AppClassLoader
 *   打印出的委派链：App → Platform → Bootstrap
 *
 * 【面试要点】
 *   1. 三层：Bootstrap（JAVA_HOME/lib，C++ 实现，Java 里为 null）、
 *      Platform（JDK 9 起由 Extension 改名，加载部分 JDK 模块）、Application（classpath）。
 *   2. 双亲委派不是"继承"（不是 extends），是**组合**：每个加载器持有 parent 引用，
 *      loadClass 先问 parent，parent 做不到才自己 findClass。
 *   3. 两个好处：避免类被重复加载（类的唯一性 = 加载器 + 全限定名）、
 *      保护核心 API 不被篡改（你自己写个 java.lang.String 也加载不进来）。
 *   4. TCCL（线程上下文类加载器）是打破委派的关键工具，见 JDBC/SPI 场景。
 */
public class ClassLoaderHierarchyDemo {

    public static void main(String[] args) {
        System.out.println("========== 1. 各类由谁加载 ==========");
        printLoader("java.lang.String（核心类）", String.class);
        printLoader("javax.sql.DataSource（JDK 模块）", javax.sql.DataSource.class);
        printLoader("本类（classpath 上）", ClassLoaderHierarchyDemo.class);

        System.out.println("\n========== 2. 委派链 ==========");
        ClassLoader cl = ClassLoaderHierarchyDemo.class.getClassLoader();
        while (cl != null) {
            System.out.println("  ↑ " + cl.getName() + " (" + cl.getClass().getName() + ")");
            cl = cl.getParent();
        }
        System.out.println("  ↑ Bootstrap（Java 里是 null）");
        System.out.println("  委派方向是自下而上询问，加载失败再自上而下回落");

        System.out.println("\n========== 3. 线程上下文类加载器 TCCL ==========");
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        System.out.println("  当前 TCCL = " + tccl);
        System.out.println("  作用：父加载器（如 Bootstrap 里的 DriverManager）");
        System.out.println("        可以通过 TCCL 反向请求子加载器去加载实现类 —— 这就是 SPI 打破委派的方式");

        System.out.println("\n========== 4. 为什么 java.* 改不了 ==========");
        try {
            Class<?> fake = Class.forName("java.lang.String");
            System.out.println("  java.lang.String 的加载器 = " + fake.getClassLoader()
                    + "（null = Bootstrap，只有它能加载 java.*）");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    static void printLoader(String desc, Class<?> clazz) {
        ClassLoader loader = clazz.getClassLoader();
        System.out.printf("  %-34s → %s%n", desc, loader == null ? "Bootstrap（null）" : loader.toString());
    }
}
