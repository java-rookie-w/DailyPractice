package org.wang.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Redis Configuration Class
 * Supports both Standalone and Cluster modes
 * 
 * Usage:
 * - For Standalone: Configure spring.redis.host, spring.redis.port in application.properties
 * - For Cluster: Configure spring.redis.cluster.nodes in application.properties
 */
@Configuration
@ConfigurationProperties(prefix = "spring.redis")
public class RedisConfig {

    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int database = 0;
    private long timeout = 5000;
    private List<String> clusterNodes;
    private int maxRedirects = 5;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        if (clusterNodes != null && !clusterNodes.isEmpty()) {
            // Cluster mode
            return createClusterConnectionFactory();
        } else {
            // Standalone mode
            return createStandaloneConnectionFactory();
        }
    }

    /**
     * Create standalone Redis connection factory
     */
    private RedisConnectionFactory createStandaloneConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(database);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }
        
        return new LettuceConnectionFactory(config);
    }

    /**
     * Create cluster Redis connection factory
     */
    private RedisConnectionFactory createClusterConnectionFactory() {
        RedisClusterConfiguration config = new RedisClusterConfiguration(clusterNodes);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }
        config.setMaxRedirects(maxRedirects);
        
        return new LettuceConnectionFactory(config);
    }

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

    // Getters and Setters
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public List<String> getClusterNodes() {
        return clusterNodes;
    }

    public void setClusterNodes(List<String> clusterNodes) {
        this.clusterNodes = clusterNodes;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }
}
