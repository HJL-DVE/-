package com.teaclub.cloud.security.feign;

import com.teaclub.cloud.core.api.TraceIdHolder;
import com.teaclub.cloud.security.context.InternalHeaders;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.stream.Collectors;

/**
 * Feign 请求拦截器：把 TraceId、用户身份上下文、内部凭证透传到下游服务。
 *
 * <p><b>仅在 classpath 存在 feign-core 时装配</b>。
 * Gateway 使用 WebFlux + spring-cloud-starter-gateway，默认不含 feign-core，
 * 若不加条件会导致网关启动时扫描到此配置类而失败。
 */
@ConditionalOnClass(name = "feign.RequestInterceptor")
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Value("${teaclub.security.internal-token:teaclub-internal-default-token}")
    private String internalToken;

    @Override
    public void apply(RequestTemplate template) {
        String traceId = TraceIdHolder.get();
        if (traceId == null) {
            traceId = MDC.get("traceId");
        }
        if (traceId != null && !traceId.isBlank()) {
            template.header(InternalHeaders.TRACE_ID, traceId);
        }

        template.header(InternalHeaders.INTERNAL_TOKEN, internalToken);

        LoginUser user = UserContext.get();
        if (user != null) {
            template.header(InternalHeaders.USER_ID, String.valueOf(user.getUserId()));
            if (user.getUsername() != null) {
                template.header(InternalHeaders.USERNAME, user.getUsername());
            }
            if (user.getBrandId() != null) {
                template.header(InternalHeaders.BRAND_ID, String.valueOf(user.getBrandId()));
            }
            if (user.getUserType() != null) {
                template.header(InternalHeaders.USER_TYPE, user.getUserType());
            }
            if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                template.header(InternalHeaders.ROLES, String.join(",", user.getRoles()));
            }
            template.header(InternalHeaders.ALL_STORE_ACCESS, String.valueOf(user.isAllStoreAccess()));
            if (user.getStoreIds() != null && !user.getStoreIds().isEmpty()) {
                template.header(InternalHeaders.STORE_IDS,
                        user.getStoreIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
            }
        } else {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String auth = req.getHeader("Authorization");
                if (auth != null) {
                    template.header("Authorization", auth);
                }
            }
        }
    }
}
