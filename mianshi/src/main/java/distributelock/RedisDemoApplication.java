package org.wang.interview.distributelock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.wang.interview.distributelock.config.RedisService;

@SpringBootApplication
public class RedisDemoApplication implements CommandLineRunner {

    @Autowired
    private RedisService redisService;

    public static void main(String[] args) {
        SpringApplication.run(RedisDemoApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 设置键值对
        redisService.setValue("myKey", "Hello, Redis!");
        // 获取键对应的值
        String value = redisService.getValue("myKey");
        System.out.println("Value from Redis: " + value);
    }
}
