package com.teaclub.cloud.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序登录配置。
 *
 * <p>{@code mock=true}（默认）时不访问微信服务器，openid 由 code 哈希模拟，
 * 与支付 Mock 保持一致的既定策略；关闭后才调用 jscode2session。
 */
@Data
@Component
@ConfigurationProperties(prefix = "teaclub.auth.wx")
public class WxProperties {

    /** 是否启用 Mock 适配器（无商户资质时的默认策略）。 */
    private boolean mock = true;

    /** 小程序 appId，仅 mock=false 时需要。 */
    private String appId;

    /** 小程序 appSecret，仅 mock=false 时需要。 */
    private String appSecret;
}
