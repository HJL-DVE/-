package com.teaclub.cloud.member.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * member-service 调用 auth-service 获取门店信息。
 *
 * <p>门店属 auth-service 域（iam_db），其它服务不得直接访问。
 */
@FeignClient(name = "auth-service", path = "/api/auth/internal", contextId = "authInternalClient")
public interface AuthInternalClient {

    /**
     * 查询门店名称（批量）。
     *
     * @param brandId  品牌 ID
     * @param storeIds 门店 ID 集合
     * @return 门店 ID -> 门店名称
     */
    @GetMapping("/stores/names")
    Result<Map<Long, String>> storeNames(@RequestParam("brandId") Long brandId,
                                         @RequestParam("storeIds") List<Long> storeIds);
}
