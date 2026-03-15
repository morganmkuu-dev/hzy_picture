package com.hzy.hzypicturebackend.manager;

import cn.hutool.core.util.RandomUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存统一管理器 (Caffeine L1 + Redis L2)
 */
@Component
public class MultiLevelCacheManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 1. 统一管理 Caffeine 本地缓存实例
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10000L)
            .expireAfter(new Expiry<String, String>() {
                @Override
                public long expireAfterCreate(String key, String value, long currentTime) {
                    long randomSeconds = ThreadLocalRandom.current().nextInt(60, 181);
                    return TimeUnit.SECONDS.toNanos(randomSeconds);
                }

                @Override
                public long expireAfterUpdate(String key, String value, long currentTime, @NonNegative long currentDuration) {
                    long randomSeconds = ThreadLocalRandom.current().nextInt(60, 181);
                    return TimeUnit.SECONDS.toNanos(randomSeconds);
                }

                @Override
                public long expireAfterRead(String key, String value, long currentTime, @NonNegative long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    /**
     * 从多级缓存中获取数据
     * 逻辑：先查本地缓存 -> 如果没有，查 Redis -> 如果 Redis 有，回写本地缓存 -> 返回数据
     *
     * @param cacheKey 缓存 Key
     * @return 缓存的 JSON 字符串，如果没有命中则返回 null
     */
    public String get(String cacheKey) {
        // 1. 查询本地缓存（Caffeine）
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            return cachedValue; // L1 命中，直接返回
        }

        // 2. 查询分布式缓存（Redis）
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        cachedValue = valueOps.get(cacheKey);
        if (cachedValue != null) {
            // L2 命中，存入本地缓存并返回
            LOCAL_CACHE.put(cacheKey, cachedValue);
            return cachedValue;
        }

        // 两级缓存均未命中
        return null;
    }

    /**
     * 写入多级缓存
     * 逻辑：同时写入本地缓存和 Redis（附加随机过期时间防雪崩）
     *
     * @param cacheKey 缓存 Key
     * @param cacheValue 需要缓存的 JSON 字符串
     */
    public void put(String cacheKey, String cacheValue) {
        // 更新本地缓存
        LOCAL_CACHE.put(cacheKey, cacheValue);

        // 更新 Redis（5 - 10 分钟随机过期，防止雪崩）
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        stringRedisTemplate.opsForValue().set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
    }
    
    // 预留位置：后期可以在这里添加 delete(String key) 方法，并结合 Redis Pub/Sub 清理其他节点的本地缓存
}