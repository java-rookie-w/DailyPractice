package org.wang.redis.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis Service providing convenient methods for common Redis operations
 * Supports String, Hash, List, Set, and ZSet operations
 */
@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ==================== Common Operations ====================

    /**
     * Delete a key
     * @param key the key to delete
     * @return true if the key was deleted
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * Delete multiple keys
     * @param keys the keys to delete
     */
    public void delete(Collection<String> keys) {
        redisTemplate.delete(keys);
    }

    /**
     * Check if a key exists
     * @param key the key to check
     * @return true if the key exists
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Set expiration time for a key
     * @param key the key
     * @param timeout the timeout value
     * @param unit the time unit
     * @return true if the timeout was set
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    /**
     * Get the time to live for a key
     * @param key the key
     * @param unit the time unit
     * @return the remaining time to live
     */
    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    /**
     * Remove expiration from a key
     * @param key the key
     * @return true if the expiration was removed
     */
    public boolean persist(String key) {
        return Boolean.TRUE.equals(redisTemplate.persist(key));
    }

    // ==================== String Operations ====================

    /**
     * Set a string value
     * @param key the key
     * @param value the value
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * Set a string value with expiration
     * @param key the key
     * @param value the value
     * @param timeout the timeout
     * @param unit the time unit
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * Get a string value
     * @param key the key
     * @return the value, or null if not found
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Get string value with type conversion
     * @param key the key
     * @param clazz the target class type
     * @param <T> the type
     * @return the value converted to target type
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Increment a numeric value
     * @param key the key
     * @param delta the increment value
     * @return the new value after increment
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * Decrement a numeric value
     * @param key the key
     * @param delta the decrement value
     * @return the new value after decrement
     */
    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    /**
     * Set a value if the key does not exist
     * @param key the key
     * @param value the value
     * @return true if the key was set, false if it already existed
     */
    public boolean setIfAbsent(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value));
    }

    /**
     * Set a value with expiration if the key does not exist
     * @param key the key
     * @param value the value
     * @param timeout the timeout
     * @param unit the time unit
     * @return true if the key was set, false if it already existed
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    // ==================== Hash Operations ====================

    /**
     * Set a hash field
     * @param key the key
     * @param field the field
     * @param value the value
     */
    public void hashSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * Get a hash field
     * @param key the key
     * @param field the field
     * @return the value, or null if not found
     */
    public Object hashGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * Get all fields and values of a hash
     * @param key the key
     * @return a map of all fields and values
     */
    public Map<Object, Object> hashGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * Delete a hash field
     * @param key the key
     * @param fields the fields to delete
     * @return the number of fields deleted
     */
    public Long hashDelete(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    /**
     * Check if a hash field exists
     * @param key the key
     * @param field the field
     * @return true if the field exists
     */
    public boolean hashHasKey(String key, Object field) {
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, field));
    }

    /**
     * Increment a hash field value
     * @param key the key
     * @param field the field
     * @param delta the increment value
     * @return the new value after increment
     */
    public Long hashIncrement(String key, String field, long delta) {
        return redisTemplate.opsForHash().increment(key, field, delta);
    }

    // ==================== List Operations ====================

    /**
     * Push an element to the left of a list
     * @param key the key
     * @param value the value
     * @return the size of the list after push
     */
    public Long leftPush(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * Push an element to the right of a list
     * @param key the key
     * @param value the value
     * @return the size of the list after push
     */
    public Long rightPush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * Pop an element from the left of a list
     * @param key the key
     * @return the popped value, or null if list is empty
     */
    public Object leftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * Pop an element from the right of a list
     * @param key the key
     * @return the popped value, or null if list is empty
     */
    public Object rightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * Get a range of elements from a list
     * @param key the key
     * @param start the start index
     * @param end the end index
     * @return a list of elements in the range
     */
    public List<Object> listRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * Get the size of a list
     * @param key the key
     * @return the size of the list
     */
    public Long listSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    /**
     * Trim a list to the specified range
     * @param key the key
     * @param start the start index
     * @param end the end index
     */
    public void listTrim(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    // ==================== Set Operations ====================

    /**
     * Add members to a set
     * @param key the key
     * @param values the values to add
     * @return the number of members added
     */
    public Long setAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    /**
     * Get all members of a set
     * @param key the key
     * @return a set of all members
     */
    public Set<Object> setMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * Remove members from a set
     * @param key the key
     * @param values the values to remove
     * @return the number of members removed
     */
    public Long setRemove(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    /**
     * Get the size of a set
     * @param key the key
     * @return the size of the set
     */
    public Long setSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    /**
     * Check if a value is a member of a set
     * @param key the key
     * @param value the value
     * @return true if the value is a member
     */
    public boolean setIsMember(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }
}
