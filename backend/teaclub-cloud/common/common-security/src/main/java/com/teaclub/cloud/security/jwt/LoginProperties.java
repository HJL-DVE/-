package com.teaclub.cloud.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录安全配置。
 *
 * <p>由单体 backend 的 `common.security.LoginProperties` 迁移而来。
 *
 * <p>登录失败锁定策略：
 * <ul>
 *   <li>连续失败达到 `maxFailCount` 次，锁定 `lockMinutes` 分钟</li>
 *   <li>达到阈值后计数归零，避免无限叠加锁定</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "teaclub.login")
public class LoginProperties {

    /** 允许的最大连续失败次数 */
    private int maxFailCount = 5;

    /** 锁定时长（分钟） */
    private int lockMinutes = 15;
}
