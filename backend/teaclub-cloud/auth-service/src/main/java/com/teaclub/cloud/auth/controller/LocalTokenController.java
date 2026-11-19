package com.teaclub.cloud.auth.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.jwt.TokenIssuer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 本地联调令牌签发端点。
 *
 * <p><b>仅在 {@code local} profile 下注册</b>（见 {@code teaclub.security.dev-token-endpoint-enabled}）。
 * 生产环境该端点不存在，Spring 不会扫描到本类。
 *
 * <p>用途：在没有 auth-service 完整业务逻辑（第四阶段之后才有）的情况下，
 * 为 Gateway 路由与 Feign 联调提供合法令牌。
 */
@Tag(name = "本地调试", description = "仅 local profile 提供的联调辅助接口")
@RestController
@RequestMapping("/api/auth/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.security.dev-token-endpoint-enabled", havingValue = "true")
public class LocalTokenController {

    private final TokenIssuer tokenIssuer;

    @Operation(summary = "签发本地联调令牌")
    @GetMapping("/token")
    public Result<Map<String, Object>> token() {
        LoginUser user = LoginUser.builder()
                .userId(1L)
                .username("admin")
                .realName("本地联调用户")
                .brandId(1L)
                .userType("HQ")
                .roles(Set.of("SUPER_ADMIN"))
                .permissions(Set.of("store:list", "member:list", "order:list"))
                .allStoreAccess(true)
                .storeIds(Set.of())
                .build();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", tokenIssuer.issue(user));
        data.put("tokenType", "Bearer");
        data.put("expiresIn", tokenIssuer.expireSeconds());
        return Result.ok(data);
    }
}
