package org.wang.mianshi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Arrays;
import java.util.List;

/**
 * Redis Configuration Class
 * Supports both Standalone and Cluster modes
 * 
 * For Standalone Mode:
 * - Configure in application.properties with spring.redis.host and spring.redis.port
 * 
 * For Cluster Mode:
 * - Configure in application.properties with spring.redis.cluster.nodes
 * - Or use application-cluster.properties profile
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // Set key serializer
        template.setKeySerializer(new StringRedisSerializer());
        // Set value serializer
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        // Set hash key serializer
        template.setHashKeySerializer(new StringRedisSerializer());
        // Set hash value serializer
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Optional: Custom factory for cluster mode
     * This is automatically configured by Spring Boot if cluster properties are set
     * Uncomment if you need custom cluster configuration
     */
    /*
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // Check if cluster configuration exists
        try {
            String clusterNodes = System.getProperty("spring.redis.cluster.nodes");
            if (clusterNodes != null || hasClusterProperties()) {
                return createClusterConnectionFactory();
            }
        } catch (Exception e) {
            // Fall back to standalone
        }
        return new LettuceConnectionFactory("localhost", 6379);
    }

    private LettuceConnectionFactory createClusterConnectionFactory() {
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration();
        
        // Add cluster nodes
        List<String> nodes = Arrays.asList(
            "127.0.0.1:7000",
            "127.0.0.1:7001",
            "127.0.0.1:7002",
            "127.0.0.1:7003",
            "127.0.0.1:7004",
            "127.0.0.1:7005"
        );
        clusterConfig.setClusterNodes(nodes);
        
        // Set password if needed
        // clusterConfig.setPassword("your_password");
        
        // Set max redirects
        clusterConfig.setMaxRedirects(5);
        
        return new LettuceConnectionFactory(clusterConfig);
    }

    private boolean hasClusterProperties() {
        // Check if cluster properties are configured
        return false; // Spring Boot auto-detects cluster config
    }
    */
}
