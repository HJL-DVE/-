package com.teaclub.cloud.auth.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * auth-service 调用 member-service 的内部客户端。
 *
 * <p>演示服务间 REST 调用（OpenFeign）的标准写法：
 * <ul>
 *   <li>通过 {@code @FeignClient(name = "member-service")} 使用 Nacos 服务发现</li>
 *   <li>请求头由 {@code FeignRequestInterceptor} 自动透传（TraceId、用户上下文、内部令牌）</li>
 *   <li>只读查询，<b>不开启自动重试</b></li>
 * </ul>
 */
@FeignClient(name = "member-service", path = "/api/member", contextId = "memberInternalClient")
public interface MemberInternalClient {

    /**
     * 查询会员基础信息。
     *
     * @param userId 用户 ID
     * @return 会员信息
     */
    @GetMapping("/internal/user/{userId}")
    Result<Object> findUserInfo(@PathVariable("userId") Long userId);
}
