package org.wang.mianshi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis Service
 * Provides common Redis operations for the interview module
 */
@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Set a value with expiration time
     * @param key the key
     * @param value the value
     * @param timeout timeout value
     * @param unit time unit
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * Set a value without expiration
     * @param key the key
     * @param value the value
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * Get a value by key
     * @param key the key
     * @return the value
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Delete a key
     * @param key the key to delete
     * @return true if deleted, false otherwise
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * Check if key exists
     * @param key the key
     * @return true if exists, false otherwise
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Set expiration time for a key
     * @param key the key
     * @param timeout timeout value
     * @param unit time unit
     * @return true if successful, false otherwise
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    /**
     * Get remaining TTL (Time To Live) for a key
     * @param key the key
     * @return TTL in seconds, -1 if no expiration, -2 if key doesn't exist
     */
    public long getTTL(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * Increment value by delta (for counters)
     * @param key the key
     * @param delta the increment value
     * @return the value after increment
     */
    public long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * Decrement value by delta
     * @param key the key
     * @param delta the decrement value
     * @return the value after decrement
     */
    public long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    // Hash Operations
    
    /**
     * Set hash field
     * @param key the key
     * @param field the field
     * @param value the value
     */
    public void hashSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * Get hash field
     * @param key the key
     * @param field the field
     * @return the value
     */
    public Object hashGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * Delete hash field
     * @param key the key
     * @param fields the fields to delete
     * @return number of fields deleted
     */
    public long hashDelete(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    // List Operations
    
    /**
     * Push element to left of list
     * @param key the key
     * @param value the value
     */
    public void leftPush(String key, Object value) {
        redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * Push element to right of list
     * @param key the key
     * @param value the value
     */
    public void rightPush(String key, Object value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * Get list range
     * @param key the key
     * @param start start index
     * @param end end index
     * @return list of values
     */
    public java.util.List<Object> listRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }
}
