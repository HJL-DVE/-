package com.teaclub.cloud.wallet.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * wallet-service 调用 auth-service 获取品牌配置。
 *
 * <p>跨服务调用，<b>不得直接访问 iam_db</b>。
 * 品牌配置（扣款顺序）属 auth-service 域。
 */
@FeignClient(name = "auth-service", path = "/api/auth/internal", contextId = "brandInternalClient")
public interface BrandInternalClient {

    /**
     * 查询品牌扣款顺序。
     *
     * @param brandId 品牌 ID
     * @return 扣款顺序：GIFT_FIRST / PRINCIPAL_FIRST
     */
    @GetMapping("/brand/{brandId}/deduct-order")
    Result<String> getDeductOrder(@PathVariable("brandId") Long brandId);
}
