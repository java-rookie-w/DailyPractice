package org.wang.mianshi.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.wang.mianshi.service.RedisService;

import java.util.concurrent.TimeUnit;

/**
 * Demo Controller for Redis Operations
 * Shows how to use Redis in the interview module
 */
@RestController
@RequestMapping("/api/redis")
public class RedisDemoController {

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Basic String Operations
     */
    @PostMapping("/set")
    public String set(@RequestParam String key, @RequestParam String value) {
        redisService.set(key, value);
        return "Value set successfully!";
    }

    @GetMapping("/get")
    public Object get(@RequestParam String key) {
        return redisService.get(key);
    }

    @PostMapping("/setWithExpiry")
    public String setWithExpiry(@RequestParam String key, @RequestParam String value, 
                                 @RequestParam(defaultValue = "60") long timeout) {
        redisService.set(key, value, timeout, TimeUnit.SECONDS);
        return "Value set with expiry: " + timeout + " seconds";
    }

    @DeleteMapping("/delete")
    public boolean delete(@RequestParam String key) {
        return redisService.delete(key);
    }

    @GetMapping("/exists")
    public boolean exists(@RequestParam String key) {
        return redisService.hasKey(key);
    }

    @GetMapping("/ttl")
    public long getTTL(@RequestParam String key) {
        return redisService.getTTL(key);
    }

    /**
     * Counter Operations
     */
    @PostMapping("/increment")
    public long increment(@RequestParam String key, @RequestParam(defaultValue = "1") long delta) {
        return redisService.increment(key, delta);
    }

    @PostMapping("/decrement")
    public long decrement(@RequestParam String key, @RequestParam(defaultValue = "1") long delta) {
        return redisService.decrement(key, delta);
    }

    /**
     * Hash Operations
     */
    @PostMapping("/hash/set")
    public String hashSet(@RequestParam String key, @RequestParam String field, @RequestParam String value) {
        redisService.hashSet(key, field, value);
        return "Hash field set successfully!";
    }

    @GetMapping("/hash/get")
    public Object hashGet(@RequestParam String key, @RequestParam String field) {
        return redisService.hashGet(key, field);
    }

    /**
     * List Operations
     */
    @PostMapping("/list/push")
    public String listPush(@RequestParam String key, @RequestParam String value) {
        redisService.rightPush(key, value);
        return "Value pushed to list!";
    }

    @GetMapping("/list/range")
    public java.util.List<Object> listRange(@RequestParam String key, 
                                             @RequestParam(defaultValue = "0") long start,
                                             @RequestParam(defaultValue = "-1") long end) {
        return redisService.listRange(key, start, end);
    }

    /**
     * Cache Example - Cache user data
     */
    @GetMapping("/cache/user/{userId}")
    public Object getUserFromCache(@PathVariable String userId) {
        String cacheKey = "user:" + userId;
        
        // Try to get from cache first
        Object cachedUser = redisService.get(cacheKey);
        if (cachedUser != null) {
            System.out.println("Cache hit for user: " + userId);
            return cachedUser;
        }
        
        // Simulate fetching from database
        System.out.println("Cache miss for user: " + userId + ", fetching from DB...");
        String userData = "{\"id\":\"" + userId + "\",\"name\":\"User " + userId + "\",\"email\":\"user" + userId + "@example.com\"}";
        
        // Cache it for 5 minutes
        redisService.set(cacheKey, userData, 5, TimeUnit.MINUTES);
        
        return userData;
    }
}
