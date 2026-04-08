package org.wang.interview.distributelock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.wang.interview.distributelock.config.RedisService;
import org.wang.interview.distributelock.service.DistributedLockService;
import org.wang.interview.distributelock.service.RedissonLockService;

@SpringBootApplication
public class RedissonApplication implements CommandLineRunner {
    @Autowired
    private RedissonLockService redissonLockService;

    public static void main(String[] args) {
        SpringApplication.run(DistributedLockDemoApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        redissonLockService.performTaskWithLock();
    }
}
