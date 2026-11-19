package com.teaclub.cloud.auth.wx;

import com.teaclub.cloud.auth.config.WxProperties;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * 小程序 code → openid 解析。
 *
 * <p><b>Mock 策略</b>（{@code teaclub.auth.wx.mock=true}，默认）：
 * {@code openid = "mock_" + sha256(code) 前 24 位十六进制}，不访问微信服务器，
 * 同一个 code 得到同一个 openid，便于联调与回归。
 *
 * <p>关闭 Mock 时才请求
 * {@code https://api.weixin.qq.com/sns/jscode2session}，并要求配置 app-id / app-secret，
 * 缺失时抛出业务异常说明（不静默降级）。
 */
@Slf4j
@Component
public class WxOpenIdResolver {

    private static final String CODE2SESSION_HOST = "api.weixin.qq.com";
    private static final String CODE2SESSION_PATH = "/sns/jscode2session";
    private static final String MOCK_PREFIX = "mock_";
    private static final int MOCK_OPENID_HASH_LEN = 24;

    private final WxProperties properties;
    private final RestClient restClient;

    public WxOpenIdResolver(WxProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    public String resolveOpenId(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ResultCode.PARAM_INVALID, "code 不能为空");
        }
        if (properties.isMock()) {
            String openid = MOCK_PREFIX + sha256Hex(code.trim()).substring(0, MOCK_OPENID_HASH_LEN);
            log.info("微信登录 Mock 模式: code={} openid={}", code, openid);
            return openid;
        }
        return requestOpenId(code.trim());
    }

    private String requestOpenId(String code) {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "微信登录未配置 teaclub.auth.wx.app-id / app-secret，且 mock 已关闭，无法调用 code2session");
        }
        Map<String, Object> resp;
        try {
            resp = restClient.get()
                    .uri(uri -> uri.scheme("https").host(CODE2SESSION_HOST).path(CODE2SESSION_PATH)
                            .queryParam("appid", properties.getAppId())
                            .queryParam("secret", properties.getAppSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            log.error("调用微信 jscode2session 失败", e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "微信登录服务不可用，请稍后重试");
        }
        if (resp == null) {
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "微信登录无响应");
        }
        Object errcode = resp.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            String errmsg = String.valueOf(resp.get("errmsg"));
            throw new BizException(ResultCode.BAD_CREDENTIALS, "微信登录失败: " + errcode + " " + errmsg);
        }
        Object openid = resp.get("openid");
        if (openid == null || !StringUtils.hasText(String.valueOf(openid))) {
            throw new BizException(ResultCode.BAD_CREDENTIALS, "微信登录失败: 未返回 openid");
        }
        return String.valueOf(openid);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
