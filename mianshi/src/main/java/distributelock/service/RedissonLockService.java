package org.wang.interview.distributelock.service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedissonLockService {
    @Autowired
    private RedissonClient redissonClient;

    public void performTaskWithLock() {
        String lockName = "myDistributedLock";
        RLock lock = redissonClient.getLock(lockName);

        try {
            // 使用默认的看门狗机制获取锁
            lock.lock();

            System.out.println("成功获取锁，开始执行任务");
            // 模拟一个耗时较长的任务
            TimeUnit.SECONDS.sleep(30);
            System.out.println("任务执行完成");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                System.out.println("锁已释放");
            }
        }
    }
}
