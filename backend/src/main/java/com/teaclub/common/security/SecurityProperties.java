package com.teaclub.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "teaclub.security")
public class SecurityProperties {
    private List<String> permitUrls = new ArrayList<>();
}
