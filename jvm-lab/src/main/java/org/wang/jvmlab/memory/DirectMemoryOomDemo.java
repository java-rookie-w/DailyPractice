package org.wang.jvmlab.memory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 【考点 16 / P1-5】OOM 类型之一：Direct buffer memory（堆外内存）
 *
 * 【运行】
 *   java -XX:MaxDirectMemorySize=16m org.wang.jvmlab.memory.DirectMemoryOomDemo
 *
 * 【预期现象】
 *   分配若干次后抛 java.lang.OutOfMemoryError: Direct buffer memory。
 *   注意：JDK 内部在分配失败时会先触发一次 System.gc() 再重试，所以过程会比较慢。
 *
 * 【面试要点】
 *   1. 直接内存不受 -Xmx 限制，由 -XX:MaxDirectMemorySize 控制（默认约等于 Xmx）。
 *   2. 为什么用堆外：IO 时少一次堆内存到 native 内存的拷贝（Netty、NIO、零拷贝场景）。
 *   3. 它什么时候被回收？DirectByteBuffer 本身是个堆上的小对象，被 GC 后，
 *      由 Cleaner 触发 unsafe.freeMemory 释放堆外内存 —— 所以它**依赖 GC 才释放**。
 *   4. 经典坑：堆外内存满了但堆还很空 → 不触发 GC → 永远不释放 → 溢出。
 *      Netty 的解法是池化 + 显式释放（ReferenceCountUtil.release），不依赖 GC。
 *   5. 排查手段：-XX:NativeMemoryTracking=detail + jcmd <pid> VM.native_memory。
 */
public class DirectMemoryOomDemo {

    public static void main(String[] args) {
        System.out.println("开始分配堆外内存（每次 1MB），上限由 -XX:MaxDirectMemorySize 控制...");
        List<ByteBuffer> buffers = new ArrayList<>();
        int count = 0;
        try {
            while (true) {
                buffers.add(ByteBuffer.allocateDirect(1024 * 1024));
                if (++count % 4 == 0) {
                    System.out.println("  已分配 " + count + " MB 堆外内存");
                }
            }
        } catch (OutOfMemoryError e) {
            // 先释放一部分堆外内存，保证后续输出不会因为拿不到额度而再次失败
            while (buffers.size() > 1) {
                buffers.remove(buffers.size() - 1);
            }
            System.out.println();
            System.out.println(">>> 触发 OOM：" + e.getClass().getName() + "：" + e.getMessage());
            System.out.println(">>> 分配了 " + count + " MB 后堆外内存耗尽");
            System.out.println(">>> 关键：堆还很空的时候堆外也能溢出，因为两者是两套独立的额度");
            System.out.println(">>> 对比实验：去掉 -XX:MaxDirectMemorySize，默认额度约等于 -Xmx");
        }
    }
}
