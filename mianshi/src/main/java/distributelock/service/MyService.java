package org.wang.interview.distributelock.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.wang.interview.distributelock.config.RedisDistributedLock;

@Service
public class MyService {

    @Autowired
    private RedisDistributedLock redisDistributedLock;

    public void doSomething() {
        String key = "my_lock_key";
        long expireTime = 5000; // 锁的过期时间，单位为毫秒
        String lockValue = redisDistributedLock.acquireLock(key, expireTime);
        if (lockValue != null) {
            try {
                // 获得锁，执行业务逻辑
                System.out.println("获得锁，开始执行任务");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                // 释放锁
                redisDistributedLock.releaseLock(key, lockValue);
                System.out.println("释放锁");
            }
        } else {
            System.out.println("未获得锁");
        }
    }
}
