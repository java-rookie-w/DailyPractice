package org.wang.jvmlab.memory;

/**
 * 【考点 1】栈帧与 StackOverflowError
 *
 * 【运行】
 *   java org.wang.jvmlab.memory.StackOverflowDemo
 *   java -Xss256k org.wang.jvmlab.memory.StackOverflowDemo   （对比：栈变小，深度骤降）
 *
 * 【预期现象】
 *   打印递归深度，直到抛出 StackOverflowError。默认栈深约 1 万 ~ 2 万，
 *   -Xss256k 后深度会明显变小。
 *
 * 【面试要点】
 *   1. 栈是线程私有的，每次方法调用压入一个栈帧，递归太深 → 栈空间耗尽 → SOE。
 *   2. 栈帧里放的是：局部变量表、操作数栈、动态连接、方法返回地址。
 *      局部变量表的大小**编译期就确定**（class 文件 Code 属性的 max_locals），
 *      所以这里每帧消耗的栈空间是固定的。
 *   3. 区分两种错：StackOverflowError = 栈深度不够；OutOfMemoryError = 栈扩展时内存不够
 *      （线程特别多时才可能出现）。
 *   4. 修递归改迭代只是治标，真正的坑在"递归没有出口"和"隐式递归"（如 toString 互调）。
 */
public class StackOverflowDemo {

    private static int depth = 0;

    public static void main(String[] args) {
        try {
            recurse();
        } catch (StackOverflowError e) {
            System.out.println(">>> 抛出 StackOverflowError，本线程栈深度 = " + depth);
            System.out.println(">>> 含义：该线程总共压入了约 " + depth + " 个栈帧后栈空间耗尽");
        }
    }

    /** 每层放几个局部变量，让栈帧大小更接近真实业务方法 */
    static void recurse() {
        long a = depth;
        long b = depth + 1;
        long c = a + b;
        depth++;
        recurse();
        // 防止 JIT 把上面的局部变量优化掉（顺便演示：无用代码会被消除）
        if (c == Long.MIN_VALUE) {
            System.out.println(a + b);
        }
    }
}
