package org.wang.redis.service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed Lock Service using Redisson
 * Provides various locking mechanisms for distributed systems
 */
@Service
public class DistributedLockService {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * Acquire a lock with default settings
     * @param lockName the name of the lock
     * @return the RLock object
     */
    public RLock lock(String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        lock.lock();
        return lock;
    }

    /**
     * Acquire a lock with specified lease time
     * @param lockName the name of the lock
     * @param leaseTime the lease time
     * @param unit the time unit
     * @return the RLock object
     */
    public RLock lock(String lockName, long leaseTime, TimeUnit unit) {
        RLock lock = redissonClient.getLock(lockName);
        lock.lock(leaseTime, unit);
        return lock;
    }

    /**
     * Try to acquire a lock without blocking
     * @param lockName the name of the lock
     * @return true if the lock was acquired, false otherwise
     */
    public boolean tryLock(String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        return lock.tryLock();
    }

    /**
     * Try to acquire a lock with timeout
     * @param lockName the name of the lock
     * @param waitTime the maximum time to wait for the lock
     * @param unit the time unit
     * @return true if the lock was acquired, false otherwise
     */
    public boolean tryLock(String lockName, long waitTime, TimeUnit unit) throws InterruptedException {
        RLock lock = redissonClient.getLock(lockName);
        return lock.tryLock(waitTime, unit);
    }

    /**
     * Try to acquire a lock with wait time and lease time
     * @param lockName the name of the lock
     * @param waitTime the maximum time to wait for the lock
     * @param leaseTime the lease time
     * @param unit the time unit
     * @return true if the lock was acquired, false otherwise
     */
    public boolean tryLock(String lockName, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        RLock lock = redissonClient.getLock(lockName);
        return lock.tryLock(waitTime, leaseTime, unit);
    }

    /**
     * Release a lock
     * @param lock the lock to release
     */
    public void unlock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Execute a task with a lock
     * @param lockName the name of the lock
     * @param task the task to execute
     */
    public void executeWithLock(String lockName, Runnable task) {
        RLock lock = null;
        try {
            lock = lock(lockName);
            task.run();
        } finally {
            unlock(lock);
        }
    }

    /**
     * Execute a task with a lock and return a result
     * @param lockName the name of the lock
     * @param supplier the supplier to execute
     * @param <T> the type of result
     * @return the result from the supplier
     */
    public <T> T executeWithLock(String lockName, Supplier<T> supplier) {
        RLock lock = null;
        try {
            lock = lock(lockName);
            return supplier.get();
        } finally {
            unlock(lock);
        }
    }

    /**
     * Execute a task with a lock and specified lease time
     * @param lockName the name of the lock
     * @param leaseTime the lease time
     * @param unit the time unit
     * @param task the task to execute
     */
    public void executeWithLock(String lockName, long leaseTime, TimeUnit unit, Runnable task) {
        RLock lock = null;
        try {
            lock = lock(lockName, leaseTime, unit);
            task.run();
        } finally {
            unlock(lock);
        }
    }

    /**
     * Execute a task with tryLock and timeout
     * @param lockName the name of the lock
     * @param waitTime the maximum time to wait for the lock
     * @param task the task to execute
     * @return true if the task was executed, false if lock was not acquired
     */
    public boolean executeWithTryLock(String lockName, long waitTime, TimeUnit unit, Runnable task) {
        RLock lock = null;
        try {
            if (tryLock(lockName, waitTime, unit)) {
                lock = redissonClient.getLock(lockName);
                task.run();
                return true;
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            unlock(lock);
        }
    }

    /**
     * Check if a lock is held
     * @param lockName the name of the lock
     * @return true if the lock is held by any thread
     */
    public boolean isLocked(String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        return lock.isLocked();
    }

    /**
     * Check if a lock is held by the current thread
     * @param lockName the name of the lock
     * @return true if the lock is held by the current thread
     */
    public boolean isHeldByCurrentThread(String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        return lock.isHeldByCurrentThread();
    }

    /**
     * Force unlock a lock (use with caution)
     * @param lockName the name of the lock
     */
    public void forceUnlock(String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        if (lock.isLocked()) {
            lock.forceUnlock();
        }
    }
}
