package com.teaclub.cloud.inventory.controller;

import com.teaclub.cloud.core.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务自检接口。
 *
 * <p>用于验证：服务已启动、已注册 Nacos、Gateway 路由可通。
 */
@Tag(name = "服务自检", description = "健康与身份信息，供 Gateway 路由验证使用")
@RestController
@RequestMapping("/api/inventory")
public class HealthController {

    @Value("${spring.application.name:inventory-service}")
    private String appName;

    @Value("${server.port:9008}")
    private String port;

    @Operation(summary = "服务信息")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", appName);
        data.put("port", port);
        data.put("status", "UP");
        data.put("database", "inventory_db");
        data.put("timestamp", System.currentTimeMillis());
        return Result.ok(data);
    }

    @Operation(summary = "内部接口示例（需 X-Internal-Token）")
    @GetMapping("/internal/ping")
    public Result<String> internalPing() {
        return Result.ok(appName + " internal pong");
    }
}
