package org.wang.mianshi.theadtest;

public class StateTest {
    public static void main(String[] args) throws InterruptedException {
        // NEW
        Thread thread = new Thread(() -> System.out.println("线程执行"));
        System.out.println(thread.getState());

        // RUNNABLE
        thread.start();
        System.out.println(thread.getState());
        // 线程结束TERMINATED
        Thread.sleep(1000);
        System.out.println(thread.getState());
    }
}
