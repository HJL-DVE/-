package com.teaclub.cloud.member.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * member-service 内部接口（仅供其他微服务调用）。
 *
 * <p>访问控制：由 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}，
 * 未携带或凭证错误一律拒绝，不对外暴露。
 */
@Tag(name = "内部接口", description = "服务间调用专用，需携带内部凭证")
@RestController
@RequestMapping("/api/member/internal")
@Slf4j
public class MemberInternalController {

    @Operation(summary = "查询会员基础信息（内部调用）")
    @GetMapping("/user/{userId}")
    public Result<Map<String, Object>> findUserInfo(@PathVariable("userId") Long userId) {
        LoginUser current = UserContext.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("service", "member-service");
        data.put("caller", current == null ? "anonymous" : current.getUsername());
        data.put("traceId", org.slf4j.MDC.get("traceId"));
        log.info("内部调用查询会员: userId={} caller={}", userId,
                current == null ? "anonymous" : current.getUsername());
        return Result.ok(data);
    }
}
