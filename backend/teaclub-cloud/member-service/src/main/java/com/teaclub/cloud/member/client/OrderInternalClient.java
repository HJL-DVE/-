package com.teaclub.cloud.member.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * member-service 调用 order-service。
 *
 * <p><b>用途</b>：会员可见口径（B 方案）需要知道"哪些会员在授权门店消费过"。
 * 单体实现是 SQL 里的 `EXISTS (SELECT 1 FROM oms_order ...)` 跨库子查询，
 * 迁移后必须改为服务间调用 —— <b>member-service 不得访问 order_db</b>。
 */
@FeignClient(name = "order-service", path = "/api/order/internal", contextId = "orderInternalClient")
public interface OrderInternalClient {

    /**
     * 查询在指定门店有消费记录的会员 ID 集合。
     *
     * @param brandId  品牌 ID
     * @param storeIds 门店 ID 集合
     */
    @GetMapping("/member-ids")
    Result<List<Long>> memberIdsByStoreIds(@RequestParam("brandId") Long brandId,
                                          @RequestParam("storeIds") List<Long> storeIds);
}
