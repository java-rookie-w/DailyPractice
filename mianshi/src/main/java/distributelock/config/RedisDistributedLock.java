package org.wang.interview.distributelock.config;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RedisDistributedLock {

    private static final String LOCK_KEY_PREFIX = "distributed_lock:";
    private static final Long RELEASE_SUCCESS = 1L;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public String acquireLock(String key, long expireTime) {
        String lockValue = UUID.randomUUID().toString();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + key, lockValue, expireTime, TimeUnit.MILLISECONDS);
        return result != null && result ? lockValue : null;
    }

    public boolean releaseLock(String key, String lockValue) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(LOCK_KEY_PREFIX + key), lockValue);
        return RELEASE_SUCCESS.equals(result);
    }
}