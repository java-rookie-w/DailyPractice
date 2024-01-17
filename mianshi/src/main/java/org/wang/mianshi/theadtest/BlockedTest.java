package org.wang.mianshi.theadtest;

public class BlockedTest {
    public static void main(String[] args) throws InterruptedException {
        // synchronized导致BLOCKED
        Object obj = new Object();
        new Thread(() -> {
            synchronized (obj) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
        Thread thread1 = new Thread(() -> {
            synchronized (obj) {
                try {
                    obj.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread1.start();
        while (true) {
            Thread.sleep(1000);
            System.out.println(thread1.getState());
        }
    }
}
