package com.teaclub.cloud.security.jwt;

import com.teaclub.cloud.security.context.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析（微服务版）。
 *
 * <p>与单体项目的 JwtProvider 语义一致，额外支持：
 * 在 Claims 中携带 roles / storeIds，便于 Gateway 一次解析后透传下游，
 * 减少每个服务都查库的开销。
 *
 * <p><b>安全说明</b>：透传的权限仅用于快速判定，
 * 涉及写操作与资金操作时，各服务仍应以本地数据为准重新校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    public static final String AUD_ADMIN = "admin";
    public static final String AUD_MEMBER = "member";

    private final JwtProperties props;

    private SecretKey adminKey() {
        return buildKey(props.getAdminSecret());
    }

    private SecretKey memberKey() {
        return buildKey(props.getMemberSecret());
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT 密钥未配置，请设置 JWT_ADMIN_SECRET / JWT_MEMBER_SECRET");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        return Keys.hmacShaKeyFor(bytes);
    }

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
                .claim("roles", user.getRoles())
                .claim("permissions", user.getPermissions())
                .claim("allStoreAccess", user.isAllStoreAccess())
                .claim("storeIds", user.getStoreIds())
                .issuedAt(now)
                .expiration(exp)
                .signWith(adminKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 签发会员端令牌。
     *
     * <p>与管理员令牌使用不同的 audience 与密钥，二者不能互相冒用：
     * 管理端令牌解析要求 aud=admin，会员端要求 aud=member。
     */
    public String issueMemberToken(LoginUser user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + props.getAccessTokenExpireSeconds() * 1000L);
        String jti = UUID.randomUUID().toString().replace("-", "");
        return Jwts.builder()
                .issuer(props.getIssuer())
                .audience().add(AUD_MEMBER).and()
                .id(jti)
                .subject(String.valueOf(user.getMemberId()))
                .claim("memberId", user.getMemberId())
                .claim("brandId", user.getBrandId())
                .issuedAt(now)
                .expiration(exp)
                .signWith(memberKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAdminToken(String token) {
        return Jwts.parser()
                .verifyWith(adminKey())
                .requireAudience(AUD_ADMIN)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims parseMemberToken(String token) {
        return Jwts.parser()
                .verifyWith(memberKey())
                .requireAudience(AUD_MEMBER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isExpired(String token) {
        try {
            parseAdminToken(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpireSeconds() {
        return props.getAccessTokenExpireSeconds();
    }
}
