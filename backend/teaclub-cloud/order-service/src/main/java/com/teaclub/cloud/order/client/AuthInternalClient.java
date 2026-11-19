package com.teaclub.cloud.order.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * order-service 调用 auth-service 获取门店信息。
 *
 * <p>门店属 auth-service 域（iam_db），order-service 不得直接访问。
 */
@FeignClient(name = "auth-service", path = "/api/auth/internal", contextId = "orderAuthInternalClient")
public interface AuthInternalClient {

    /**
     * 查询门店信息。
     */
    @GetMapping("/store/{storeId}")
    Result<Map<String, Object>> store(@PathVariable("storeId") Long storeId);
}
