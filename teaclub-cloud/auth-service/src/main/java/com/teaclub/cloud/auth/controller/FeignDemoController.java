package com.teaclub.cloud.auth.controller;

import com.teaclub.cloud.auth.client.MemberInternalClient;
import com.teaclub.cloud.core.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务间调用演示端点。
 *
 * <p>用于验证 OpenFeign 的完整链路：
 * Nacos 服务发现 → 负载均衡 → 请求头透传 → 下游返回统一结构。
 */
@Tag(name = "内部调用演示", description = "验证 OpenFeign 服务间调用")
@RestController
@RequestMapping("/api/auth/feign")
@RequiredArgsConstructor
@Slf4j
public class FeignDemoController {

    private final MemberInternalClient memberClient;

    @Operation(summary = "通过 Feign 调用 member-service 查询会员")
    @GetMapping("/member/{userId}")
    public Result<Map<String, Object>> callMember(@PathVariable("userId") Long userId) {
        log.info("Feign 调用 member-service, userId={}", userId);
        Result<Object> resp = memberClient.findUserInfo(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caller", "auth-service");
        data.put("callee", "member-service");
        data.put("targetUserId", userId);
        data.put("downstream", resp.getData());
        data.put("downstreamCode", resp.getCode());
        return Result.ok(data);
    }
}
