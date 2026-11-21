package com.teaclub.cloud.product.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置。
 *
 * <p><b>两种实现二选一</b>（与 payment-service 的 mock/wechat-v3 双适配器同一思路）：
 * <ul>
 *   <li>{@code local}：落 Docker 卷，nginx 直接提供，答辩断网可用（默认）</li>
 *   <li>{@code oss}：上传到阿里云 OSS，跨机器共享、可 CDN 加速</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "teaclub.storage")
public class StorageProperties {

    /** local | oss */
    private String type = "local";

    /** 本地存储目录（仅 type=local 时生效） */
    private String localDir = "/app/uploads";

    /** 本地存储对应的访问前缀 */
    private String localUrlPrefix = "/static/uploads";

    private Oss oss = new Oss();

    @Data
    public static class Oss {
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucket;
        /** 自定义域名 / CDN 域名，为空时用 https://{bucket}.{endpoint} */
        private String publicDomain;
    }
}
