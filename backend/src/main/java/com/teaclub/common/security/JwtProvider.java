package com.teaclub.common.security;

import com.teaclub.common.context.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT 签发与解析。管理端与会员端使用不同密钥与不同 audience，互不通用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    public static final String AUD_ADMIN = "admin";

    private final JwtProperties props;

    private SecretKey adminKey() {
        return buildKey(props.getAdminSecret());
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT 密钥未配置");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            // 保证满足 HS256 最小密钥长度要求
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 签发管理端访问令牌。
     */
    public String issueAdminToken(LoginUser user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + props.getAccessTokenExpireSeconds() * 1000L);
        String jti = UUID.randomUUID().toString().replace("-", "");
        return Jwts.builder()
                .issuer(props.getIssuer())
                .audience().add(AUD_ADMIN).and()
                .id(jti)
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("brandId", user.getBrandId())
                .claim("jti", jti)
                .issuedAt(now)
                .expiration(exp)
                .signWith(adminKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验管理端令牌，返回基础声明。权限与门店范围在每次请求中从数据库重新加载。
     */
    public Claims parseAdminToken(String token) {
        return Jwts.parser()
                .verifyWith(adminKey())
                .requireAudience(AUD_ADMIN)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpireSeconds() {
        return props.getAccessTokenExpireSeconds();
    }
}
