package org.wang.mianshi.theadtest;

public class WaitingTest {
    public static void main(String[] args) throws InterruptedException {
        // WAITING
        Object obj = new Object();
        Thread thread1 = new Thread(() -> {
            synchronized(obj) {
                try {
                    // Thread.join()也调用的是obj.wait()
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
