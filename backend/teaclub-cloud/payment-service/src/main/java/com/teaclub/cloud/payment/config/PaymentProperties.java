package com.teaclub.cloud.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "teaclub.payment")
public class PaymentProperties {

    /** mock | wechat-v3 */
    private String provider = "mock";

    private Wechat wechat = new Wechat();

    @Data
    public static class Wechat {
        private String merchantId;
        private String merchantSerialNo;
        private String apiV3Key;
        private String privateKeyPath;
        private String appId;
        private String notifyUrl;
        private String refundNotifyUrl;
        private String apiBase = "https://api.mch.weixin.qq.com";
        /** 平台证书序列号 -> 公钥，用于验签 */
        private String platformCertPath;
    }
}
