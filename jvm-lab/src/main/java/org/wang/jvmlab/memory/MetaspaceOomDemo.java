package org.wang.jvmlab.memory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 【考点 16】OOM 类型之一：Metaspace
 *
 * 【运行】
 *   java -XX:MaxMetaspaceSize=32m org.wang.jvmlab.memory.MetaspaceOomDemo
 *
 * 【预期现象】
 *   反复用**不同的类加载器**加载同一个类，类加载元数据占满元空间后抛
 *   java.lang.OutOfMemoryError: Metaspace
 *
 * 【面试要点】
 *   1. 元空间存的是类的元数据（类型信息、字段、方法、常量池），JDK 8 起用本地内存，
 *      默认只受物理内存限制 —— 所以不设 MaxMetaspaceSize 时能把机器内存吃干。
 *   2. 类卸载的条件很苛刻：该类的 Class 对象不可达 + 加载它的 ClassLoader 不可达
 *      + 该类所有实例不可达。本实验用 List 强引用 Class，阻止卸载，才能稳定撑爆元空间。
 *   3. 线上元空间溢出的真凶几乎都是"动态生成类"：CGLIB/ByteBuddy 代理、
 *      Groovy/JSP 热加载、反射膨胀（inflation）、大量 ClassLoader 泄漏。
 *   4. 反过来讲：正是因为元空间在本地内存，JDK 8 才解决了 PermGen OOM
 *      （java.lang.OutOfMemoryError: PermGen space）。
 */
public class MetaspaceOomDemo {

    public static void main(String[] args) throws Exception {
        byte[] classBytes = readSelfClassBytes();
        System.out.println("已读取 Sample 类的字节码：" + classBytes.length + " 字节");
        System.out.println("开始用不同的 ClassLoader 反复定义同一个类...");

        // 强引用 Class → 间接强引用其 ClassLoader → 阻止类卸载 → 元空间只增不减
        List<Class<?>> keepAlive = new ArrayList<>();
        int count = 0;
        try {
            while (true) {
                ClassLoader loader = new ClassLoader(null) { // parent = null（Bootstrap）
                    @Override
                    protected Class<?> findClass(String name) {
                        return defineClass(name, classBytes, 0, classBytes.length);
                    }
                };
                keepAlive.add(loader.loadClass(Sample.class.getName()));
                count++;
                if (count % 200 == 0) {
                    // 注意：这里刻意不用字符串拼接（JDK 9+ 的 + 走 invokedynamic，
                    // 元空间满时连拼字符串都会失败），所以拆成多条 print
                    System.out.print("  已加载 ");
                    System.out.print(count);
                    System.out.println(" 份副本");
                }
            }
        } catch (OutOfMemoryError e) {
            // 先把强引用放开，让后续输出有足够的空间
            keepAlive.clear();
            System.out.println();
            System.out.print(">>> 触发 OOM：");
            System.out.println(e.getClass().getName());
            System.out.print(">>> 消息：");
            System.out.println(e.getMessage());
            System.out.println(">>> 元空间被动态生成的类撑爆");
            System.out.println(">>> 线上对应场景：动态代理 / 热部署 / Groovy 脚本，每次都生成新类");
            System.out.print(">>> 触发前已加载副本数：");
            System.out.println(count);
        }
    }

    private static byte[] readSelfClassBytes() throws Exception {
        String simple = "MetaspaceOomDemo$Sample.class";
        try (InputStream in = MetaspaceOomDemo.class.getResourceAsStream(simple)) {
            if (in == null) {
                throw new IllegalStateException("找不到 " + simple + "，请先编译本模块");
            }
            return in.readAllBytes();
        }
    }

    /** 被反复加载的"靶子类"，本身什么都不做 */
    public static class Sample {
        public void hello() {
            System.out.println("  Sample 被加载并实例化");
        }
    }
}
