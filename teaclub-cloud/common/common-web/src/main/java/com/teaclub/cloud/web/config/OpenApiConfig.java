package com.teaclub.cloud.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 各微服务统一的 OpenAPI 文档配置。
 *
 * <p>服务名通过 {@code spring.application.name} 自动带入标题，
 * 避免每个服务重复配置。
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:teaclub-service}")
    private String appName;

    @Bean
    public OpenAPI serviceOpenAPI() {
        final String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("TeaClub " + appName)
                        .version("1.0.0")
                        .description("TeaClub 微服务接口文档")
                        .contact(new Contact().name("TeaClub")))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
