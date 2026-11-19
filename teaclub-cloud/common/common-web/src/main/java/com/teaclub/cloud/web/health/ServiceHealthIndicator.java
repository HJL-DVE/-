package com.teaclub.cloud.web.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.beans.factory.annotation.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务自身健康信息。
 *
 * <p>补充标准 actuator health 中缺失的服务标识信息，
 * 便于在 Nacos 与容器编排中识别各服务实例。
 */
public class ServiceHealthIndicator implements HealthIndicator {

    private final String serviceName;
    private final String database;

    public ServiceHealthIndicator(String serviceName, String database) {
        this.serviceName = serviceName;
        this.database = database;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("service", serviceName);
        details.put("database", database);
        details.put("status", "UP");
        return Health.up().withDetails(details).build();
    }
}
