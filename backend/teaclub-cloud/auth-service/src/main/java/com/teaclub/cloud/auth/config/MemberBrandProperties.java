package com.teaclub.cloud.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会员端品牌归属配置。
 *
 * <p>会员令牌必须带 {@code brandId}（网关据此下发 {@code X-Brand-Id}）。
 * 小程序登录是白名单接口，拿不到登录态，故以配置的默认品牌为准（单品牌部署）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "teaclub.member")
public class MemberBrandProperties {

    /** 会员默认归属品牌 ID。 */
    private Long defaultBrandId = 1L;
}
