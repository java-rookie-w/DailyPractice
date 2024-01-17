package org.wang.mianshi.theadtest;

public class JoinTest {
    public static void main(String[] args) throws InterruptedException {
        Thread thread = new Thread(() -> {
            System.out.println("thread begin");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("thread end");
        });
        thread.start();
        System.out.println("main begin");
        thread.join();
        System.out.println("main end");
    }
}
