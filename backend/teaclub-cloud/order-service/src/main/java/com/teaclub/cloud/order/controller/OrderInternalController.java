package com.teaclub.cloud.order.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.order.mapper.OmsOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * order-service 内部接口（仅供其他微服务调用）。
 *
 * <p>访问控制：由 `InternalTokenFilter` 校验 `X-Internal-Token`。
 */
@Tag(name = "订单内部接口", description = "服务间调用专用，需携带内部凭证")
@RestController
@RequestMapping("/api/order/internal")
@RequiredArgsConstructor
@Slf4j
public class OrderInternalController {

    private final OmsOrderMapper orderMapper;

    @Operation(summary = "查询在指定门店有消费记录的会员 ID")
    @GetMapping("/member-ids")
    public Result<List<Long>> memberIdsByStoreIds(
            @RequestParam Long brandId,
            @RequestParam List<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = orderMapper.selectMemberIdsByStoreIds(brandId, storeIds);
        log.info("查询门店消费过的会员: brandId={} stores={} count={}", brandId, storeIds, ids.size());
        return Result.ok(ids);
    }
}
