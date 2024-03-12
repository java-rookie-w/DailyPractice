package org.wang.mianshi.theadtest;

import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池测试类
 */
public class ThreadPoolExecutorTest {

    private static final int corePoolSize = 2;

    private static int maximumPoolSize = 4;

    private static long keepAliveTime = 1000;

    private static TimeUnit unit = TimeUnit.SECONDS;

    private static int queue_cap = 2;

    public static void main(String[] args) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, new ArrayBlockingQueue<>(queue_cap));
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            executor.execute(()->{
                System.out.println("当前线程：" + finalI);
                try {
                    Thread.sleep(100000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
