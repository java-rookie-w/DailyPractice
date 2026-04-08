package org.wang.interview.distributelock.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 单机模式
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        // 自定义看门狗检查时间间隔为 10 秒
        config.setLockWatchdogTimeout(10000);
        return Redisson.create(config);
    }
}
