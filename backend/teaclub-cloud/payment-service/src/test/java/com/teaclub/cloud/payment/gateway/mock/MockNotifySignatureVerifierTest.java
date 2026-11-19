package com.teaclub.cloud.payment.gateway.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock 支付通知身份校验单元测试。
 */
class MockNotifySignatureVerifierTest {

    private MockNotifySignatureVerifier verifier(boolean enforce) {
        MockNotifySignatureVerifier v = new MockNotifySignatureVerifier();
        ReflectionTestUtils.setField(v, "expectedAppId", "mock-appid");
        ReflectionTestUtils.setField(v, "expectedMchId", "mock-mchid");
        ReflectionTestUtils.setField(v, "signSecret", "mock-sign-secret");
        ReflectionTestUtils.setField(v, "enforce", enforce);
        return v;
    }

    private Map<String, Object> body(String appId, String mchId, String sign) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paymentNo", "PAY001");
        m.put("amount", 100);
        if (appId != null) m.put("appId", appId);
        if (mchId != null) m.put("mchId", mchId);
        if (sign != null) m.put("sign", sign);
        return m;
    }

    @Test
    @DisplayName("签名确定且可复现")
    void signDeterministic() {
        MockNotifySignatureVerifier v = verifier(false);
        String a = v.sign("mock-appid", "mock-mchid", "PAY001", "100");
        String b = v.sign("mock-appid", "mock-mchid", "PAY001", "100");
        assertEquals(a, b);
        assertEquals(64, a.length(), "SHA-256 十六进制应为 64 字符");
    }

    @Test
    @DisplayName("金额变化会改变签名")
    void signChangesWithAmount() {
        MockNotifySignatureVerifier v = verifier(false);
        assertNotEquals(
                v.sign("mock-appid", "mock-mchid", "PAY001", "100"),
                v.sign("mock-appid", "mock-mchid", "PAY001", "101"));
    }

    @Test
    @DisplayName("AppID 不匹配必须拒绝")
    void rejectWrongAppId() {
        MockNotifySignatureVerifier v = verifier(false);
        assertThrows(RuntimeException.class,
                () -> v.verify(body("other-appid", "mock-mchid", null)));
    }

    @Test
    @DisplayName("商户号不匹配必须拒绝")
    void rejectWrongMchId() {
        MockNotifySignatureVerifier v = verifier(false);
        assertThrows(RuntimeException.class,
                () -> v.verify(body("mock-appid", "other-mchid", null)));
    }

    @Test
    @DisplayName("签名错误必须拒绝")
    void rejectWrongSign() {
        MockNotifySignatureVerifier v = verifier(false);
        assertThrows(RuntimeException.class,
                () -> v.verify(body("mock-appid", "mock-mchid", "deadbeef")));
    }

    @Test
    @DisplayName("正确身份信息应通过")
    void acceptCorrect() {
        MockNotifySignatureVerifier v = verifier(false);
        String sign = v.sign("mock-appid", "mock-mchid", "PAY001", "100");
        assertDoesNotThrow(
                () -> v.verify(body("mock-appid", "mock-mchid", sign)));
    }

    @Test
    @DisplayName("宽松模式：不带校验字段可放行")
    void lenientAllowsMissing() {
        assertDoesNotThrow(() -> verifier(false).verify(body(null, null, null)));
    }

    @Test
    @DisplayName("严格模式：缺字段必须拒绝")
    void strictRejectsMissing() {
        assertThrows(RuntimeException.class,
                () -> verifier(true).verify(body(null, null, null)));
    }
}
