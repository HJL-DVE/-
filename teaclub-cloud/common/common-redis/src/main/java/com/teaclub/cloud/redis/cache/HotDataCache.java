package com.teaclub.cloud.redis.cache;

import com.teaclub.cloud.redis.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 热点数据缓存（商品、分类、门店、品牌）。
 *
 * <p>统一采用「读缓存 → 未命中查库 → 回填」模式。
 * 写操作后由业务方显式调用对应 evict 方法失效缓存。
 */
@Service
@RequiredArgsConstructor
public class HotDataCache {

    private final CacheService cacheService;

    // ==================== 商品 ====================

    public <T> Optional<T> getProduct(Long spuId, Class<T> type) {
        return Optional.ofNullable(cacheService.get(RedisKeys.PRODUCT + spuId, type));
    }

    public void putProduct(Long spuId, Object value) {
        cacheService.set(RedisKeys.PRODUCT + spuId, value, RedisKeys.TTL_PRODUCT);
    }

    public void evictProduct(Long spuId) {
        cacheService.delete(RedisKeys.PRODUCT + spuId);
    }

    // ==================== 商品分类 ====================

    public <T> Optional<T> getCategory(Long categoryId, Class<T> type) {
        return Optional.ofNullable(cacheService.get(RedisKeys.PRODUCT_CATEGORY + categoryId, type));
    }

    public void putCategory(Long categoryId, Object value) {
        cacheService.set(RedisKeys.PRODUCT_CATEGORY + categoryId, value, RedisKeys.TTL_PRODUCT_CATEGORY);
    }

    public void evictCategory(Long categoryId) {
        cacheService.delete(RedisKeys.PRODUCT_CATEGORY + categoryId);
    }

    // ==================== 门店 ====================

    public <T> Optional<T> getStore(Long storeId, Class<T> type) {
        return Optional.ofNullable(cacheService.get(RedisKeys.STORE + storeId, type));
    }

    public void putStore(Long storeId, Object value) {
        cacheService.set(RedisKeys.STORE + storeId, value, RedisKeys.TTL_STORE);
    }

    public void evictStore(Long storeId) {
        cacheService.delete(RedisKeys.STORE + storeId);
    }

    /** 门店变更时清理该门店下所有商品缓存 */
    public void evictStoreProducts(Long storeId) {
        cacheService.deleteByPrefix(RedisKeys.STORE_PRODUCT + storeId + ":");
    }

    // ==================== 品牌 ====================

    public <T> Optional<T> getBrand(Long brandId, Class<T> type) {
        return Optional.ofNullable(cacheService.get(RedisKeys.BRAND + brandId, type));
    }

    public void putBrand(Long brandId, Object value) {
        cacheService.set(RedisKeys.BRAND + brandId, value, RedisKeys.TTL_BRAND);
    }

    public void evictBrand(Long brandId) {
        cacheService.delete(RedisKeys.BRAND + brandId);
    }

    // ==================== 会员 ====================

    public <T> Optional<T> getMember(Long memberId, Class<T> type) {
        return Optional.ofNullable(cacheService.get(RedisKeys.MEMBER + memberId, type));
    }

    public void putMember(Long memberId, Object value) {
        cacheService.set(RedisKeys.MEMBER + memberId, value, RedisKeys.TTL_MEMBER);
    }

    public void evictMember(Long memberId) {
        cacheService.delete(RedisKeys.MEMBER + memberId);
    }

    // ==================== 门店商品 ====================

    public <T> Optional<T> getStoreProduct(Long storeId, Long skuId, Class<T> type) {
        return Optional.ofNullable(cacheService.get(RedisKeys.STORE_PRODUCT + storeId + ":" + skuId, type));
    }

    public void putStoreProduct(Long storeId, Long skuId, Object value) {
        cacheService.set(RedisKeys.STORE_PRODUCT + storeId + ":" + skuId, value, RedisKeys.TTL_STORE_PRODUCT);
    }

    public void evictStoreProduct(Long storeId, Long skuId) {
        cacheService.delete(RedisKeys.STORE_PRODUCT + storeId + ":" + skuId);
    }

    // ==================== 钱包 ====================

    /**
     * 钱包缓存读取。
     *
     * <p><b>注意</b>：钱包余额缓存仅作读加速，
     * 扣款/充值成功后<b>必须</b>调用 {@link #evictWallet} 失效。
     */
    public <T> Optional<T> getWallet(Long memberId, Class<T> type) {
        return Optional.ofNullable(cacheService.get(RedisKeys.WALLET + memberId, type));
    }

    public void putWallet(Long memberId, Object value) {
        cacheService.set(RedisKeys.WALLET + memberId, value, RedisKeys.TTL_WALLET);
    }

    /** 资金变动后必须调用 */
    public void evictWallet(Long memberId) {
        cacheService.delete(RedisKeys.WALLET + memberId);
    }

    /** 指定自定义过期时间覆盖默认值 */
    public void putWithTtl(String key, Object value, Duration ttl) {
        cacheService.set(key, value, ttl);
    }
}
