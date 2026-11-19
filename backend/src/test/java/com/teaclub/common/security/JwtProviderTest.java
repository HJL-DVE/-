package com.teaclub.common.security;

import com.teaclub.common.context.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * JWT 签发与校验测试。
 */
class JwtProviderTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtZm9yLXVuaXQtdGVzdC1vbmx5LTEyMzQ1Ng==";

    private JwtProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setAdminSecret(SECRET);
        props.setMemberSecret(SECRET + "-member");
        props.setAccessTokenExpireSeconds(7200);
        props.setIssuer("teaclub");
        provider = new JwtProvider(props);
    }

    private LoginUser sampleUser() {
        return LoginUser.builder()
                .userId(42L).username("tester").brandId(1L)
                .roles(Set.of("STORE_MANAGER"))
                .permissions(Set.of("store:list"))
                .allStoreAccess(false)
                .storeIds(Set.of(1L))
                .build();
    }

    @Test
    @DisplayName("签发的令牌可被正确解析")
    void issueAndParse() {
        String token = provider.issueAdminToken(sampleUser());
        assertThat(token).isNotBlank();

        Claims claims = provider.parseAdminToken(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("username")).isEqualTo("tester");
        assertThat(claims.getAudience()).contains("admin");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    @DisplayName("篡改后的令牌校验失败")
    void tamperedTokenRejected() {
        String token = provider.issueAdminToken(sampleUser());
        String tampered = token.substring(0, token.length() - 4) + "abcd";
        assertThatThrownBy(() -> provider.parseAdminToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("使用其他密钥签发的令牌被拒绝")
    void foreignSecretRejected() {
        JwtProperties other = new JwtProperties();
        other.setAdminSecret("YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1kaWZmZXJlbnQtMTIzNDU2Nzg5MA==");
        other.setAccessTokenExpireSeconds(7200);
        other.setIssuer("teaclub");
        JwtProvider otherProvider = new JwtProvider(other);

        String foreignToken = otherProvider.issueAdminToken(sampleUser());
        assertThatThrownBy(() -> provider.parseAdminToken(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("过期令牌被拒绝")
    void expiredTokenRejected() {
        JwtProperties shortLived = new JwtProperties();
        shortLived.setAdminSecret(SECRET);
        shortLived.setAccessTokenExpireSeconds(-10);
        shortLived.setIssuer("teaclub");
        JwtProvider shortProvider = new JwtProvider(shortLived);

        String token = shortProvider.issueAdminToken(sampleUser());
        assertThatThrownBy(() -> provider.parseAdminToken(token))
                .isInstanceOf(JwtException.class);
    }
}
