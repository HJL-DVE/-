package com.teaclub.cloud.product.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * product-service 调用 auth-service 获取门店信息。
 *
 * <p>门店属 auth-service 域（iam_db），product-service 不得直接访问。
 */
@FeignClient(name = "auth-service", path = "/api/auth/internal", contextId = "productAuthInternalClient")
public interface AuthInternalClient {

    /**
     * 查询门店是否存在。
     *
     * @return 存在返回门店信息，不存在返回 null data
     */
    @GetMapping("/store/{storeId}")
    Result<Map<String, Object>> store(@PathVariable("storeId") Long storeId);
}
