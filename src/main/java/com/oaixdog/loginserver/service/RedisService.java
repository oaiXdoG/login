package com.oaixdog.loginserver.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, Duration timeout) {
        redisTemplate.opsForValue().set(key, value, timeout);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public boolean setIfAbsent(String key, String value, Duration timeout) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout));
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean expire(String key, Duration timeout) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout));
    }

    /** 执行 Lua 脚本（只操作一个 key），脚本返回 nil 时为 0 */
    public long execute(RedisScript<Long> script, String key, String... args) {
        Long result = redisTemplate.execute(script, List.of(key), (Object[]) args);
        return result == null ? 0 : result;
    }

    public long getExpireSeconds(String key) {
        Long seconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return seconds == null ? -2 : seconds;
    }

    public long increment(String key) {
        return increment(key, 1);
    }

    public long increment(String key, long delta) {
        Long value = redisTemplate.opsForValue().increment(key, delta);
        return value == null ? 0 : value;
    }

    public long decrement(String key) {
        return decrement(key, 1);
    }

    public long decrement(String key, long delta) {
        Long value = redisTemplate.opsForValue().decrement(key, delta);
        return value == null ? 0 : value;
    }
}
