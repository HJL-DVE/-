package com.teaclub.cloud.security.jwt;

import com.teaclub.cloud.security.context.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 令牌签发器。
 *
 * <p>供 auth-service 登录接口与本地联调使用。
 */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtProvider jwtProvider;

    public String issue(LoginUser user) {
        return jwtProvider.issueAdminToken(user);
    }

    public long expireSeconds() {
        return jwtProvider.getExpireSeconds();
    }
}
