package com.teaclub.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "teaclub.jwt")
public class JwtProperties {
    private String adminSecret;
    private String memberSecret;
    private long accessTokenExpireSeconds = 7200;
    private String issuer = "teaclub";
}
