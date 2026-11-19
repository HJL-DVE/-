package com.teaclub.cloud.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "teaclub.jwt")
public class JwtProperties {

    /** 管理端签名密钥（Base64 或明文，长度需 ≥ 32 字节） */
    private String adminSecret;

    /** 会员端签名密钥（与管管端不同） */
    private String memberSecret;

    /** 访问令牌有效期（秒） */
    private long accessTokenExpireSeconds = 7200;

    private String issuer = "teaclub";
}
