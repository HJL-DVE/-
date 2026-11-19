package com.teaclub.cloud.wallet.service.support;

import com.teaclub.cloud.wallet.client.BrandInternalClient;
import com.teaclub.cloud.wallet.enums.DeductOrder;
import com.teaclub.cloud.redis.cache.CacheService;
import com.teaclub.cloud.redis.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 扣款顺序解析器。
 *
 * <p>品牌配置属 auth-service 域，wallet-service 通过 Feign 获取，
 * 并在 Redis 中短暂缓存以降低跨服务调用频次。
 *
 * <p><b>降级策略</b>：auth-service 不可用时回退到默认的 `GIFT_FIRST`，
 * 保证钱包核心扣款能力不因配置服务故障而中断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeductOrderResolver {

    /** 品牌扣款顺序缓存时长（秒）—— 60 秒，配置变更可较快生效 */
    private static final long CACHE_TTL_SECONDS = 60L;

    private final BrandInternalClient brandClient;
    private final CacheService cacheService;

    /**
     * 解析品牌扣款顺序。
     *
     * @param brandId   品牌 ID
     * @param override  调用方显式指定（为空则取品牌配置）
     */
    public DeductOrder resolve(Long brandId, String override) {
        if (override != null && !override.isBlank()) {
            return DeductOrder.of(override);
        }
        String cached = cacheService.get(cacheKey(brandId), String.class);
        if (cached != null && !cached.isBlank()) {
            return DeductOrder.of(cached);
        }
        try {
            var resp = brandClient.getDeductOrder(brandId);
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                cacheService.set(cacheKey(brandId), resp.getData(), Duration.ofSeconds(CACHE_TTL_SECONDS));
                return DeductOrder.of(resp.getData());
            }
            log.warn("获取品牌扣款顺序失败，按默认 GIFT_FIRST 处理: brandId={} resp={}", brandId, resp);
        } catch (Exception e) {
            log.error("调用 auth-service 获取品牌配置异常，降级为 GIFT_FIRST: brandId={}", brandId, e);
        }
        return DeductOrder.GIFT_FIRST;
    }

    private String cacheKey(Long brandId) {
        return RedisKeys.BRAND + brandId + ":deduct-order";
    }
}
