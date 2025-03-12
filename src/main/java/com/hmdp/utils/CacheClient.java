package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * Redis工具类
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);

    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {

        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));


    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        // 1. 从redis查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3. 存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        // 判断命中的是否是空值
        if (json != null) {
            // 返回一个错误信息
            return null;
        }

        // 4. 不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        if (r == null) {
            // 5. 不存在，返回错误
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }

        // 6. 存在，写入redis
        this.set(key, r, time, timeUnit);

        return r;
    }

    /**
     * 线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 缓存击穿-逻辑时间解决方案
     *
     * @param id
     * @return
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        // 1. 从redis查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        // 因为是热点key,所以我们基本上认为Redis中的这个热点key是存在的
        if (StrUtil.isBlank(json)) {
            // 不存在，直接返回null
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);

        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return r;
        }

        // 已过期，需要缓存重建
        // 缓存重建

        // 获取互斥锁
        String localKey = LOCK_SHOP_KEY + id;

        boolean isLock = tryLock(localKey);

        if (isLock) {
            // 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {

                try {
                    // 重建缓存
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 写入缓存
                    this.setWithLogicExpire(key, r1, time, timeUnit);


                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(localKey);
                }


            });
        }


        return r;
    }


}
