package org.wang.interview.distributelock.service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class DistributedLockService {
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 当 tryLock() 传入过期时间参数时，不会启用看门狗功能，锁会在指定的过期时间后自动释放。
     */
    public void doWithLock() {
        // 定义锁的名称
        String lockName = "myDistributedLock";
        // 获取锁对象
        RLock lock = redissonClient.getLock(lockName);

        try {
            // 尝试获取锁，最多等待 10 秒，锁的持有时间为 30 秒
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (isLocked) {
                try {
                    // 获得锁，执行业务逻辑
                    System.out.println("获得锁，开始执行任务");
                    // 模拟业务操作
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("未获得锁");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                System.out.println("释放锁");
            }
        }
    }

    /**
     * 当 tryLock() 不传入过期时间参数时，会启用看门狗功能，锁会自动续期。
     */
    public void doWithLock2() {
        // 定义锁的名称
        String lockName = "myDistributedLock";
        // 获取锁对象
        RLock lock = redissonClient.getLock(lockName);

        try {
            // 当trylock不传过期参数时，开启看门狗，传递参数则不开启
            boolean isLocked = lock.tryLock();
            if (isLocked) {
                try {
                    // 获得锁，执行业务逻辑
                    System.out.println("获得锁，开始执行任务");
                    // 模拟业务操作
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("未获得锁");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                System.out.println("释放锁");
            }
        }
    }
}
