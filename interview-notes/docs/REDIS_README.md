# Redis Integration Guide for Mianshi Module

## Overview
This module now includes Redis integration for caching, session management, and other distributed features.
**Supports both Standalone and Cluster modes!**

## Quick Start

### For Standalone Redis:
Use the default `application.properties` configuration.

### For Redis Cluster:
1. Update `application.properties` with cluster settings (see Cluster Configuration section below)
2. Or use Spring profile: `--spring.profiles.active=cluster`

## Dependencies Added
- Spring Boot Starter Data Redis
- Redisson (for advanced Redis features like distributed locks)
- Spring Boot Web
- Lombok

## Configuration

### Standalone Mode (Default)

**application.properties:**
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

### Cluster Mode

**Option 1: Update application.properties**

Comment out standalone config and uncomment cluster config:
```properties
# Comment these out for cluster mode
# spring.redis.host=localhost
# spring.redis.port=6379

# Enable cluster configuration
spring.redis.cluster.nodes[0]=127.0.0.1:7000
spring.redis.cluster.nodes[1]=127.0.0.1:7001
spring.redis.cluster.nodes[2]=127.0.0.1:7002
spring.redis.cluster.nodes[3]=127.0.0.1:7003
spring.redis.cluster.nodes[4]=127.0.0.1:7004
spring.redis.cluster.nodes[5]=127.0.0.1:7005
spring.redis.cluster.password=your_cluster_password
spring.redis.cluster.timeout=5000ms
spring.redis.cluster.max-redirects=5

# Cluster pool settings (recommended higher values)
spring.redis.lettuce.pool.max-active=16
spring.redis.lettuce.pool.max-idle=16
spring.redis.lettuce.pool.min-idle=4
spring.redis.lettuce.pool.max-wait=3000ms

# Lettuce cluster refresh options
spring.redis.lettuce.cluster.refresh.adaptive=true
spring.redis.lettuce.cluster.refresh.periodic=true
spring.redis.lettuce.cluster.refresh.refresh-period=60000
```

**Option 2: Use Spring Profile**

Run with cluster profile:
```bash
java -jar mianshi.jar --spring.profiles.active=cluster
```

Or use the provided `application-cluster.properties` as a template.

**Cluster Configuration Requirements:**
- Minimum 3 master nodes required
- Provide at least 3 node addresses in format `host:port`
- All nodes should be accessible from the application

## Components Created

### 1. RedisConfig
Configuration class that sets up RedisTemplate with proper serializers.

### 2. RedisService
Service class providing convenient methods for Redis operations:
- String operations (set, get, delete)
- Expiration operations (expire, TTL)
- Counter operations (increment, decrement)
- Hash operations (hashSet, hashGet, hashDelete)
- List operations (leftPush, rightPush, listRange)

### 3. RedisDemoController
REST controller demonstrating Redis usage with endpoints for all operations.

## Usage Examples

### Using RedisService
```java
@Autowired
private RedisService redisService;

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
```

### Using RedisTemplate Directly
```java
@Autowired
private RedisTemplate<String, Object> redisTemplate;

// String operations
redisTemplate.opsForValue().set("key", "value");
Object value = redisTemplate.opsForValue().get("key");

// Hash operations
redisTemplate.opsForHash().put("user", "name", "John");
Object name = redisTemplate.opsForHash().get("user", "name");

// List operations
redisTemplate.opsForList().rightPush("mylist", "item1");
List<Object> items = redisTemplate.opsForList().range("mylist", 0, -1);
```

## API Endpoints

### Basic Operations
- POST `/api/redis/set?key=xxx&value=xxx` - Set a value
- GET `/api/redis/get?key=xxx` - Get a value
- POST `/api/redis/setWithExpiry?key=xxx&value=xxx&timeout=60` - Set with expiration
- DELETE `/api/redis/delete?key=xxx` - Delete a key
- GET `/api/redis/exists?key=xxx` - Check if key exists
- GET `/api/redis/ttl?key=xxx` - Get remaining TTL

### Counter Operations
- POST `/api/redis/increment?key=xxx&delta=1` - Increment value
- POST `/api/redis/decrement?key=xxx&delta=1` - Decrement value

### Hash Operations
- POST `/api/redis/hash/set?key=xxx&field=xxx&value=xxx` - Set hash field
- GET `/api/redis/hash/get?key=xxx&field=xxx` - Get hash field

### List Operations
- POST `/api/redis/list/push?key=xxx&value=xxx` - Push to list
- GET `/api/redis/list/range?key=xxx&start=0&end=-1` - Get list range

### Cache Example
- GET `/api/redis/cache/user/{userId}` - Demo of caching user data

## Running the Application

1. Make sure Redis is running on localhost:6379 (or update configuration)
2. Run the application: `MianshiApplication.java`
3. Test endpoints using Postman or curl

Example with curl:
```bash
# Set a value
curl -X POST "http://localhost:8080/api/redis/set?key=mykey&value=myvalue"

# Get the value
curl "http://localhost:8080/api/redis/get?key=mykey"

# Set with expiration (60 seconds)
curl -X POST "http://localhost:8080/api/redis/setWithExpiry?key=tempkey&value=tempvalue&timeout=60"

# Increment counter
curl -X POST "http://localhost:8080/api/redis/increment?key=views&delta=1"
```

## Advanced Features (Redisson)

The module also includes Redisson for advanced features:

### Distributed Lock Example
```java
@Autowired
private RedissonClient redissonClient;

RLock lock = redissonClient.getLock("myLock");
lock.lock();
try {
    // Critical section
} finally {
    lock.unlock();
}
```

## Common Use Cases

### 1. Caching
Cache frequently accessed data to reduce database load.

### 2. Session Management
Store user sessions in Redis for distributed applications.

### 3. Rate Limiting
Use counters to implement rate limiting.

### 4. Distributed Locks
Coordinate operations across multiple service instances.

### 5. Message Queues
Use Redis lists as simple message queues.

## Troubleshooting

### Connection Issues
- Verify Redis server is running: `redis-cli ping` should return PONG
- Check host/port in application.properties
- Ensure firewall allows connections to Redis port

### Cluster-Specific Issues

**Cannot connect to cluster:**
```bash
# Test cluster connectivity
redis-cli -c -h 127.0.0.1 -p 7000 cluster info
```

**Cluster nodes not discovered:**
- Ensure all cluster nodes are running and communicating
- Check cluster status: `redis-cli -c -p 7000 cluster nodes`
- Verify network connectivity between all nodes
- Make sure cluster-node-timeout is configured properly

**MOVED/ASK errors:**
- These are normal in cluster mode
- Lettuce client handles redirects automatically
- Increase `max-redirects` if you see too many redirect errors

**Connection pool exhausted:**
- Increase pool settings (max-active, max-idle)
- Cluster mode may need larger pools due to multiple connections

### Serialization Issues
- The default JSON serializer handles most objects
- For custom objects, ensure they have no-arg constructor

## Best Practices

1. Always set expiration times for cache entries
2. Use meaningful key names (e.g., "user:{id}", "config:{key}")
3. Use Redis pipelines for batch operations
4. Monitor memory usage and implement eviction policies
5. Use connection pooling for better performance
6. **For Cluster Mode:**
   - Distribute keys evenly using hash tags if needed
   - Be aware of cross-slot operations limitations
   - Monitor cluster health and rebalance when necessary
   - Use larger connection pools than standalone mode

## Example: Real Cluster Configuration

Here's a production-ready cluster configuration:

```properties
# Production Redis Cluster
spring.redis.cluster.nodes[0]=redis-cluster-1.example.com:7000
spring.redis.cluster.nodes[1]=redis-cluster-2.example.com:7001
spring.redis.cluster.nodes[2]=redis-cluster-3.example.com:7002
spring.redis.cluster.nodes[3]=redis-cluster-4.example.com:7003
spring.redis.cluster.nodes[4]=redis-cluster-5.example.com:7004
spring.redis.cluster.nodes[5]=redis-cluster-6.example.com:7005
spring.redis.cluster.password=SecurePassword123!
spring.redis.cluster.timeout=8000ms
spring.redis.cluster.max-redirects=5

# Production pool settings
spring.redis.lettuce.pool.max-active=32
spring.redis.lettuce.pool.max-idle=24
spring.redis.lettuce.pool.min-idle=8
spring.redis.lettuce.pool.max-wait=5000ms

# Cluster refresh for topology changes
spring.redis.lettuce.cluster.refresh.adaptive=true
spring.redis.lettuce.cluster.refresh.periodic=true
spring.redis.lettuce.cluster.refresh.refresh-period=30000
```
