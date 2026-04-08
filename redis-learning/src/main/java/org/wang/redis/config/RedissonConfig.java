package org.wang.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson Configuration for advanced Redis features
 * Supports both Standalone and Cluster modes
 */
@Configuration
public class RedissonConfig {

    @Autowired
    private RedisConfig redisConfig;

    /**
     * Create RedissonClient bean
     * Automatically detects whether to use standalone or cluster mode
     */
    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        if (redisConfig.getClusterNodes() != null && !redisConfig.getClusterNodes().isEmpty()) {
            // Cluster mode
            String[] nodes = redisConfig.getClusterNodes().toArray(new String[0]);
            config.useClusterServers()
                .addNodeAddress(nodes)
                .setTimeout((int) redisConfig.getTimeout());
            
            if (redisConfig.getPassword() != null && !redisConfig.getPassword().isEmpty()) {
                config.useClusterServers().setPassword(redisConfig.getPassword());
            }
        } else {
            // Standalone mode
            String address = String.format("redis://%s:%d", redisConfig.getHost(), redisConfig.getPort());
            config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisConfig.getDatabase())
                .setTimeout((int) redisConfig.getTimeout());
            
            if (redisConfig.getPassword() != null && !redisConfig.getPassword().isEmpty()) {
                config.useSingleServer().setPassword(redisConfig.getPassword());
            }
        }
        
        // Custom watchdog timeout for distributed locks (in milliseconds)
        // Default is 30 seconds, can be overridden by setting system property
        String watchdogTimeout = System.getProperty("redisson.watchdogTimeout", "30000");
        config.setLockWatchdogTimeout(Long.parseLong(watchdogTimeout));
        
        return Redisson.create(config);
    }
}
