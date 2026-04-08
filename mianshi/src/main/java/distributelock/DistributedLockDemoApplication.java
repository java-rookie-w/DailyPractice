package org.wang.interview.distributelock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.wang.interview.distributelock.service.DistributedLockService;

@SpringBootApplication
public class DistributedLockDemoApplication implements CommandLineRunner {

    @Autowired
    private DistributedLockService distributedLockService;

    public static void main(String[] args) {
        SpringApplication.run(DistributedLockDemoApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        distributedLockService.doWithLock2();
    }
}
