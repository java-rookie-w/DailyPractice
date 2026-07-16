package org.wang.redis.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight Distributed Lock Implementation using Redis Lua scripts
 * Does not require Redisson, uses only Spring Data Redis
 * Suitable for simple locking scenarios
 */
@Service
public class RedisLockService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_KEY_PREFIX = "lock:";
    private static final Long RELEASE_SUCCESS = 1L;

    /**
     * Acquire a lock with default expiration time (30 seconds)
     * @param key the lock key
     * @return the lock value if acquired, null otherwise
     */
    public String acquireLock(String key) {
        return acquireLock(key, 30000);
    }

    /**
     * Acquire a lock with specified expiration time
     * @param key the lock key
     * @param expireTime the expiration time in milliseconds
     * @return the lock value if acquired, null otherwise
     */
    public String acquireLock(String key, long expireTime) {
        String lockValue = UUID.randomUUID().toString();
        String lockKey = LOCK_KEY_PREFIX + key;
        
        stringRedisTemplate.opsForValue().set(
            lockKey, 
            lockValue, 
            expireTime, 
            TimeUnit.MILLISECONDS
        );
        
        return lockValue;
    }

    /**
     * Try to acquire a lock without blocking
     * @param key the lock key
     * @return the lock value if acquired, null otherwise
     */
    public String tryLock(String key) {
        return tryLock(key, 30000);
    }

    /**
     * Try to acquire a lock with specified expiration time
     * @param key the lock key
     * @param expireTime the expiration time in milliseconds
     * @return the lock value if acquired, null otherwise
     */
    public String tryLock(String key, long expireTime) {
        return acquireLock(key, expireTime);
    }

    /**
     * Release a lock
     * @param key the lock key
     * @param lockValue the lock value (must match the one returned by acquireLock)
     * @return true if the lock was released successfully, false otherwise
     */
    public boolean releaseLock(String key, String lockValue) {
        String lockKey = LOCK_KEY_PREFIX + key;
        
        // Lua script to atomically check and delete the lock
        // This ensures that we only delete our own lock
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                       "return redis.call('del', KEYS[1]) " +
                       "else " +
                       "return 0 " +
                       "end";
        
        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = stringRedisTemplate.execute(
            redisScript, 
            Collections.singletonList(lockKey), 
            lockValue
        );
        
        return RELEASE_SUCCESS.equals(result);
    }

    /**
     * Check if a lock is held
     * @param key the lock key
     * @return true if the lock is held, false otherwise
     */
    public boolean isLocked(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
    }

    /**
     * Get the current lock value
     * @param key the lock key
     * @return the lock value, or null if not locked
     */
    public String getLockValue(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        return stringRedisTemplate.opsForValue().get(lockKey);
    }

    /**
     * Extend the expiration time of a lock
     * @param key the lock key
     * @param lockValue the lock value (must match the current lock holder)
     * @param expireTime the new expiration time in milliseconds
     * @return true if the expiration was extended, false otherwise
     */
    public boolean extendLock(String key, String lockValue, long expireTime) {
        String lockKey = LOCK_KEY_PREFIX + key;
        
        // Only extend if we hold the lock
        String currentValue = stringRedisTemplate.opsForValue().get(lockKey);
        if (lockValue.equals(currentValue)) {
            return Boolean.TRUE.equals(
                stringRedisTemplate.expire(lockKey, expireTime, TimeUnit.MILLISECONDS)
            );
        }
        
        return false;
    }

    /**
     * Execute a task with a lock
     * @param key the lock key
     * @param expireTime the lock expiration time in milliseconds
     * @param task the task to execute
     * @return true if the task was executed successfully, false if lock was not acquired
     */
    public boolean executeWithLock(String key, long expireTime, Runnable task) {
        String lockValue = acquireLock(key, expireTime);
        if (lockValue != null) {
            try {
                task.run();
                return true;
            } finally {
                releaseLock(key, lockValue);
            }
        }
        return false;
    }

    /**
     * Execute a task with a lock and default expiration time
     * @param key the lock key
     * @param task the task to execute
     * @return true if the task was executed successfully, false if lock was not acquired
     */
    public boolean executeWithLock(String key, Runnable task) {
        return executeWithLock(key, 30000, task);
    }
}
