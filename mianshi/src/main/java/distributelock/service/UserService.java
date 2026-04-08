package org.wang.interview.distributelock.service;

import javax.annotation.PostConstruct;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wang.interview.distributelock.repository.UserDatabaseRepository;

import java.util.concurrent.TimeUnit;

/**
 * 事务管理：使用@Transactional注解确保数据库更新和锁操作在同一个事务中。
 * 锁获取超时：使用tryLock方法设置锁获取超时时间，避免死锁。
 * 缓存失效策略：在更新数据库后，先删除缓存，然后在读取操作中重新加载数据到缓存。
 * 异步更新缓存：使用@Async注解将缓存更新操作异步化，减少对主业务流程的影响。
 * 日志记录：可以增加日志记录，便于排查问题和监控系统状态。
 * 缓存过期时间：为缓存设置过期时间，避免缓存数据长期不更新。
 */
@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 假设这是一个数据库操作的类
    @Autowired
    private UserDatabaseRepository userDatabaseRepository;

    @Transactional
    public void updateUser(String userId, String userInfo) {
        // 1. 更新数据库
        userDatabaseRepository.updateUser(userId, userInfo);

        // 2. 获取分布式锁，并设置锁获取超时时间
        String lockKey = "user:lock:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean isLocked = lock.tryLock(10, 10, TimeUnit.SECONDS);
            if (isLocked) {
                try {
                    // 3. 删除缓存
                    stringRedisTemplate.delete("user:" + userId);
                } finally {
                    lock.unlock();
                }
            } else {
                throw new RuntimeException("Failed to acquire lock");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while acquiring lock", e);
        }

        // 4. 异步更新缓存
        asyncUpdateCache(userId, userInfo);
    }

    @Async
    public void asyncUpdateCache(String userId, String userInfo) {
        // 5. 更新缓存并设置过期时间
        stringRedisTemplate.opsForValue().set("user:" + userId, userInfo, 60, TimeUnit.SECONDS);
    }

    /**
     * 缓存预热：可以在系统启动时预热缓存，减少冷启动时的数据库访问压力。
     * 缓存过期时间：为缓存设置合理的过期时间（例如60秒），避免缓存数据长期不更新。
     * 日志记录：增加日志记录，便于排查问题和监控系统状态。
     * 缓存更新策略：在读取操作中，如果缓存数据过期或不存在，从数据库获取并更新缓存。
     * @param userId
     * @return
     */
    public String getUser(String userId) {
        // 1. 尝试从缓存中获取用户信息
        String userInfo = stringRedisTemplate.opsForValue().get("user:" + userId);
        if (userInfo != null) {
            logger.info("User data retrieved from cache for userId: {}", userId);
            return userInfo;
        }

        // 2. 如果缓存中没有，从数据库获取
        userInfo = userDatabaseRepository.getUser(userId);
        if (userInfo != null) {
            // 3. 将数据库中的数据写入缓存并设置过期时间
            stringRedisTemplate.opsForValue().set("user:" + userId, userInfo, 60, TimeUnit.SECONDS);
            logger.info("User data retrieved from database and cached for userId: {}", userId);
        } else {
            logger.warn("User data not found for userId: {}", userId);
        }

        return userInfo;
    }


    @PostConstruct
    public void preheatCache() {
        // 5. 预热缓存
        logger.info("Preheating cache...");
        // 假设有一个方法可以获取所有需要预热的用户ID
        for (String userId : userDatabaseRepository.getAllUserIds()) {
            String userInfo = userDatabaseRepository.getUser(userId);
            if (userInfo != null) {
                stringRedisTemplate.opsForValue().set("user:" + userId, userInfo, 60, TimeUnit.SECONDS);
            }
        }
        logger.info("Cache preheating completed.");
    }

}
