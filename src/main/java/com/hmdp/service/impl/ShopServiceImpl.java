package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;


    /**
     * 缓存击穿
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {

        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);

//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 缓存击穿-互斥锁解决方案
//        Shop shop = queryWithMutex(id);

        // 缓存击穿-逻辑时间解决方案
//        Shop shop = queryWithLogicalExpire(id);

        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }

        // 返回
        return Result.ok(shop);
    }

    /**
     * 线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * 缓存击穿-逻辑时间解决方案
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        // 1. 从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        // 因为是热点key,所以我们基本上认为Redis中的这个热点key是存在的
        if (StrUtil.isBlank(shopJson)) {
            // 不存在，直接返回null
            return null;
        }

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);

        JSONObject data =(JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return shop;
        }

        // 已过期，需要缓存重建
        // 缓存重建

        // 获取互斥锁
        String localKey = LOCK_SHOP_KEY + id;

        boolean isLock = tryLock(localKey);

        if (isLock) {
            // 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{

                try {
                    // 重建缓存
                    // 30 minutes
                    this.saveShop2Redis(id,1800L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(localKey);
                }



            });
        }


        return shop;
    }


    /**
     * 缓存击穿-互斥锁解决方案
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        // 1. 从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // 判断命中的是否是空值
        if (shopJson != null) {
            // 返回一个错误信息
            return null;
        }

        // 实现缓存重建
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        // 判断是否获取成功
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                // 失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 成功，根据id查询数据库
            shop = getById(id);
            // 模拟重建的延时
            Thread.sleep(200);

            if (shop == null) {
                // 5. 不存在，返回错误
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }

            // 6. 存在，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);


        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 释放互斥锁
            unlock(lockKey);
        }
        // 返回
        return shop;
    }


    /**
     * 缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        // 1. 从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // 判断命中的是否是空值
        if (shopJson != null) {
            // 返回一个错误信息
            return null;
        }

        // 4. 不存在，根据id查询数据库
        Shop shop = getById(id);

        if (shop == null) {
            // 5. 不存在，返回错误
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }

        // 6. 存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 存入热点key
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        // 模拟延迟
        Thread.sleep(200);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));

    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result update(Shop shop) {
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
