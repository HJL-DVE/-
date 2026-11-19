package com.teaclub.cloud.wallet.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * wallet-service 调用 auth-service 获取品牌配置与门店信息。
 *
 * <p>跨服务调用，<b>不得直接访问 iam_db</b>。
 * 品牌配置（扣款顺序）与门店均属 auth-service 域。
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

    /**
     * 批量查询门店名称（流水/充值单展示用）。
     *
     * @param brandId  品牌 ID
     * @param storeIds 门店 ID 集合
     * @return 门店 ID -> 门店名称
     */
    @GetMapping("/stores/names")
    Result<Map<Long, String>> storeNames(@RequestParam("brandId") Long brandId,
                                         @RequestParam("storeIds") List<Long> storeIds);
}
