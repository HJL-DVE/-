package com.teaclub.cloud.security.config;

import com.teaclub.cloud.security.filter.InternalTokenFilter;
import com.teaclub.cloud.security.jwt.JwtProperties;
import com.teaclub.cloud.security.jwt.TokenIssuer;
import com.teaclub.cloud.security.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * common-security 自动装配入口。
 *
 * <p>关键设计：
 * <ul>
 *   <li>JWT / 用户上下文 / StoreScope 与 Web 类型无关，始终注册</li>
 *   <li>{@link InternalTokenFilter} 继承 Servlet 过滤器，
 *       <b>只在 Servlet Web 应用中注册</b>；Gateway 是 WebFlux，不含它</li>
 *   <li>Feign 拦截器不在此扫描范围内，由业务服务按需 {@code @Import}</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@Import(ResourceServerSecurityConfig.class)
public class TeaClubSecurityAutoConfiguration {

    @Bean
    public JwtProvider jwtProvider(JwtProperties props) {
        return new JwtProvider(props);
    }

    @Bean
    public TokenIssuer tokenIssuer(JwtProvider jwtProvider) {
        return new TokenIssuer(jwtProvider);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    static class ServletSecurityConfig {

        @Bean
        public InternalTokenFilter internalTokenFilter(
                @Value("${teaclub.security.internal-token:teaclub-internal-default-token}") String internalToken) {
            return new InternalTokenFilter(internalToken);
        }
    }
}
