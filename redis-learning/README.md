# Redis Module

A reusable Redis integration module built with Spring Boot and Redisson, providing comprehensive Redis operations and distributed lock support.

## Features

- **Dual Mode Support**: Works with both Redis Standalone and Redis Cluster configurations
- **RedisTemplate Integration**: Pre-configured RedisTemplate with JSON serialization
- **Redisson Support**: Advanced Redis features including distributed locks with automatic watchdog
- **Lightweight Lock Option**: Simple Redis-based lock implementation using Lua scripts (no Redisson required)
- **Comprehensive Operations**: Support for String, Hash, List, Set, and ZSet operations
- **Auto-Configuration**: Automatic mode detection based on configuration

## Quick Start

### 1. Add Dependency

Add the redis dependency to your project's pom.xml:

```xml
<dependency>
    <groupId>org.wang</groupId>
    <artifactId>redis</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Redis

**For Standalone Redis:**

```properties
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=
spring.redis.database=0
spring.redis.timeout=5000ms

# Connection Pool Settings
spring.redis.lettuce.pool.max-active=8
spring.redis.lettuce.pool.max-idle=8
spring.redis.lettuce.pool.min-idle=0
```

**For Redis Cluster:**

```properties
spring.redis.cluster.nodes[0]=127.0.0.1:7000
spring.redis.cluster.nodes[1]=127.0.0.1:7001
spring.redis.cluster.nodes[2]=127.0.0.1:7002
spring.redis.cluster.password=your_password
spring.redis.cluster.timeout=5000ms
spring.redis.cluster.max-redirects=5

# Cluster Pool Settings (recommended higher values)
spring.redis.lettuce.pool.max-active=16
spring.redis.lettuce.pool.max-idle=16
spring.redis.lettuce.pool.min-idle=4
```

### 3. Use in Your Code

#### Basic Redis Operations

```java
@Service
public class MyService {
    
    @Autowired
    private RedisService redisService;
    
    public void doSomething() {
        // Set value with expiration
        redisService.set("user:1", userData, 5, TimeUnit.MINUTES);
        
        // Get value
        Object user = redisService.get("user:1");
        
        // Check if key exists
        boolean exists = redisService.hasKey("user:1");
        
        // Increment counter
        long count = redisService.increment("page:views", 1);
        
        // Hash operations
        redisService.hashSet("user:1:profile", "name", "John");
        Object name = redisService.hashGet("user:1:profile", "name");
        
        // List operations
        redisService.rightPush("mylist", "item1");
        List<Object> items = redisService.listRange("mylist", 0, -1);
    }
}
```

#### Distributed Lock with Redisson (Recommended)

```java
@Service
public class OrderService {
    
    @Autowired
    private DistributedLockService lockService;
    
    public void processOrder(String orderId) {
        // Simple lock usage
        lockService.executeWithLock("order:" + orderId, () -> {
            // Critical section - only one thread can execute this at a time
            System.out.println("Processing order: " + orderId);
            // Your business logic here
        });
        
        // Or use tryLock with timeout
        boolean acquired = lockService.executeWithTryLock(
            "order:" + orderId, 
            5, 
            TimeUnit.SECONDS,
            () -> {
                // Execute if lock acquired
            }
        );
        
        if (!acquired) {
            System.out.println("Could not acquire lock, skipping...");
        }
    }
}
```

#### Lightweight Lock (Without Redisson)

```java
@Service
public class InventoryService {
    
    @Autowired
    private RedisLockService redisLockService;
    
    public void updateInventory(String productId) {
        boolean success = redisLockService.executeWithLock(
            "inventory:" + productId, 
            10000, // 10 seconds timeout
            () -> {
                // Critical section
                System.out.println("Updating inventory for: " + productId);
            }
        );
        
        if (!success) {
            System.out.println("Failed to acquire lock");
        }
    }
}
```

## Components

### 1. RedisConfig

Auto-configuration class that:
- Detects standalone vs cluster mode automatically
- Creates RedisConnectionFactory
- Configures RedisTemplate with proper serializers

### 2. RedissonConfig

Configuration for RedissonClient:
- Supports both standalone and cluster modes
- Configurable watchdog timeout
- Auto-detects mode based on RedisConfig

### 3. RedisService

Convenient service providing:
- Common operations: delete, hasKey, expire, ttl
- String operations: set, get, increment, decrement
- Hash operations: hashSet, hashGet, hashDelete
- List operations: push, pop, range
- Set operations: add, remove, members

### 4. DistributedLockService (Redisson-based)

Advanced distributed locking:
- Blocking lock: `lock()`
- Non-blocking tryLock: `tryLock()`
- Lock with timeout
- Automatic lock renewal (watchdog)
- Convenient execution methods

### 5. RedisLockService (Lightweight)

Simple lock implementation:
- Uses Redis SETNX with Lua scripts
- No Redisson dependency
- Atomic lock release
- Suitable for simple scenarios

## Usage Examples

### Caching Example

```java
@Service
public class UserService {
    
    @Autowired
    private RedisService redisService;
    
    public User getUserById(String userId) {
        String cacheKey = "user:" + userId;
        
        // Try to get from cache
        User cachedUser = redisService.get(cacheKey, User.class);
        if (cachedUser != null) {
            return cachedUser;
        }
        
        // Cache miss, load from database
        User user = userRepository.findById(userId);
        
        // Cache the result with 10 minute expiration
        redisService.set(cacheKey, user, 10, TimeUnit.MINUTES);
        
        return user;
    }
    
    public void updateUser(User user) {
        String cacheKey = "user:" + user.getId();
        
        // Update database
        userRepository.save(user);
        
        // Invalidate cache
        redisService.delete(cacheKey);
    }
}
```

### Rate Limiting Example

```java
@Service
public class RateLimitService {
    
    @Autowired
    private RedisService redisService;
    
    private static final int MAX_REQUESTS = 100;
    private static final long TIME_WINDOW = 60; // 1 minute
    
    public boolean allowRequest(String userId) {
        String key = "rate_limit:" + userId;
        
        Long count = redisService.increment(key, 1);
        
        if (count == 1) {
            // First request, set expiration
            redisService.expire(key, TIME_WINDOW, TimeUnit.SECONDS);
        }
        
        return count <= MAX_REQUESTS;
    }
}
```

### Distributed Lock Best Practices

```java
@Service
public class PaymentService {
    
    @Autowired
    private DistributedLockService lockService;
    
    public void processPayment(String userId, BigDecimal amount) {
        // Use tryLock to avoid waiting
        boolean acquired = false;
        try {
            acquired = lockService.tryLock("payment:" + userId, 3, TimeUnit.SECONDS);
            
            if (acquired) {
                // Check balance and process payment
                System.out.println("Processing payment for user: " + userId);
            } else {
                System.out.println("Payment processing is busy, please try again later");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock interrupted", e);
        } finally {
            // Note: When using tryLock, you need to manually unlock
            RLock lock = lockService.getLock("payment:" + userId);
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

## Configuration Options

### Standalone Mode Properties

| Property | Default | Description |
|----------|---------|-------------|
| spring.redis.host | localhost | Redis server host |
| spring.redis.port | 6379 | Redis server port |
| spring.redis.password | none | Redis password |
| spring.redis.database | 0 | Database index (0-15) |
| spring.redis.timeout | 5000ms | Connection timeout |
| spring.redis.lettuce.pool.max-active | 8 | Max total connections |
| spring.redis.lettuce.pool.max-idle | 8 | Max idle connections |
| spring.redis.lettuce.pool.min-idle | 0 | Min idle connections |

### Cluster Mode Properties

| Property | Default | Description |
|----------|---------|-------------|
| spring.redis.cluster.nodes | none | Cluster node addresses |
| spring.redis.cluster.password | none | Cluster password |
| spring.redis.cluster.timeout | 5000ms | Command timeout |
| spring.redis.cluster.max-redirects | 5 | Max redirect count |

### Redisson Options

| Property | Default | Description |
|----------|---------|-------------|
| redisson.watchdogTimeout | 30000 | Lock watchdog timeout (ms) |

## Troubleshooting

### Connection Issues

1. **Cannot connect to Redis**
   - Verify Redis server is running: `redis-cli ping`
   - Check host/port configuration
   - Ensure firewall allows connections

2. **Cluster connection problems**
   - Test cluster status: `redis-cli --cluster check <node>:<port>`
   - Verify all nodes are running and communicating
   - Check network connectivity between nodes

### Lock Issues

1. **Lock not releasing**
   - Ensure unlock() is called in finally block
   - Check if exception is preventing unlock
   - Use executeWithLock() helper methods

2. **Deadlock concerns**
   - Always set reasonable lease times
   - Use tryLock() with timeout instead of blocking lock()
   - Monitor lock status with isLocked() methods

## Best Practices

1. **Always set expiration** for cache entries to prevent memory issues
2. **Use meaningful key names** (e.g., "user:{id}", "config:{key}")
3. **Use Redis pipelines** for batch operations when possible
4. **Monitor memory usage** and implement eviction policies
5. **For locks**:
   - Prefer tryLock() over blocking lock()
   - Set appropriate timeouts
   - Always release locks in finally blocks
   - Use watchdog feature for long-running operations

## License

This module is part of the DailyPractice project.
