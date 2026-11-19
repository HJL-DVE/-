package com.teaclub.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "teaclub.login")
public class LoginProperties {
    private int maxFailCount = 5;
    private int lockMinutes = 15;
}
