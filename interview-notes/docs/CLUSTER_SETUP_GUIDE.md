# Redis Cluster Configuration Quick Reference

## 如何配置 Redis 集群连接 (How to Configure Redis Cluster Connection)

### 方法一：修改 application.properties (Method 1: Modify application.properties)

1. **注释掉单机配置** (Comment out standalone config):
```properties
# spring.redis.host=localhost
# spring.redis.port=6379
```

2. **启用集群配置** (Enable cluster config):
```properties
spring.redis.cluster.nodes[0]=你的节点 1:端口
spring.redis.cluster.nodes[1]=你的节点 2:端口
spring.redis.cluster.nodes[2]=你的节点 3:端口
# ... 至少 3 个节点 (at least 3 nodes)
spring.redis.cluster.password=你的密码
spring.redis.cluster.timeout=5000ms
spring.redis.cluster.max-redirects=5
```

### 方法二：使用配置文件 (Method 2: Use Profile)

运行应用时指定集群配置：
```bash
java -jar mianshi.jar --spring.profiles.active=cluster
```

或在 IDE 中设置 VM options:
```
-Dspring.profiles.active=cluster
```

### 示例配置 (Example Configuration)

假设你有以下集群节点：
- 192.168.1.100:7000
- 192.168.1.101:7001
- 192.168.1.102:7002
- 192.168.1.103:7003
- 192.168.1.104:7004
- 192.168.1.105:7005

配置如下：
```properties
spring.redis.cluster.nodes[0]=192.168.1.100:7000
spring.redis.cluster.nodes[1]=192.168.1.101:7001
spring.redis.cluster.nodes[2]=192.168.1.102:7002
spring.redis.cluster.nodes[3]=192.168.1.103:7003
spring.redis.cluster.nodes[4]=192.168.1.104:7004
spring.redis.cluster.nodes[5]=192.168.1.105:7005
spring.redis.cluster.password=your_password_here
spring.redis.cluster.timeout=8000ms
spring.redis.cluster.max-redirects=5

# 连接池设置 (Connection Pool Settings)
spring.redis.lettuce.pool.max-active=16
spring.redis.lettuce.pool.max-idle=16
spring.redis.lettuce.pool.min-idle=4
spring.redis.lettuce.pool.max-wait=3000ms

# 集群刷新设置 (Cluster Refresh Settings)
spring.redis.lettuce.cluster.refresh.adaptive=true
spring.redis.lettuce.cluster.refresh.periodic=true
spring.redis.lettuce.cluster.refresh.refresh-period=60000
```

### 验证集群连接 (Verify Cluster Connection)

1. **测试集群状态**:
```bash
redis-cli -c -h <node-ip> -p <node-port> cluster info
```

2. **查看集群节点**:
```bash
redis-cli -c -h <node-ip> -p <node-port> cluster nodes
```

3. **在应用中测试**:
```bash
curl http://localhost:8080/api/redis/set?key=test&value=cluster_test
curl http://localhost:8080/api/redis/get?key=test
```

### 常见问题 (Common Issues)

**问题 1: 无法连接到集群**
- 检查所有节点是否运行正常
- 确认防火墙允许访问
- 验证节点间网络连通性

**问题 2: MOVED/ASK 错误**
- 这是正常的集群重定向
- 客户端会自动处理
- 如频繁出现，增加 max-redirects

**问题 3: 连接超时**
- 增加 timeout 值
- 检查网络延迟
- 增加连接池大小

### 注意事项 (Important Notes)

1. **最少节点**: Redis 集群至少需要 3 个主节点
2. **密码认证**: 如果启用了密码，所有节点必须使用相同密码
3. **跨槽操作**: 避免跨 slot 的操作（如多 key 操作）
4. **键命名**: 使用 hash tags 确保相关键在同一 slot

### 代码使用示例 (Code Usage Example)

```java
@Service
public class MyService {
    @Autowired
    private RedisService redisService;
    
    public void testData() {
        // 集群模式下使用方式与单机模式完全相同
        // Usage is the same as standalone mode
        
        redisService.set("user:1", userData, 5, TimeUnit.MINUTES);
        Object user = redisService.get("user:1");
        
        // 集群会自动处理数据分片和重定向
        // Cluster handles sharding and redirects automatically
    }
}
```

### 性能优化建议 (Performance Tips)

1. **连接池**: 集群模式建议使用更大的连接池
   ```properties
   spring.redis.lettuce.pool.max-active=32
   spring.redis.lettuce.pool.max-idle=24
   spring.redis.lettuce.pool.min-idle=8
   ```

2. **超时设置**: 根据网络情况调整超时
   ```properties
   spring.redis.cluster.timeout=8000ms
   ```

3. **刷新策略**: 启用拓扑自动刷新
   ```properties
   spring.redis.lettuce.cluster.refresh.adaptive=true
   spring.redis.lettuce.cluster.refresh.periodic=true
   ```

4. **监控**: 定期检查集群健康状态
   ```bash
   redis-cli --cluster check <node>:<port>
   ```
