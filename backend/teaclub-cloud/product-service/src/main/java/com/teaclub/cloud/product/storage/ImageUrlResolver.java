package com.teaclub.cloud.product.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 图片地址解析：把数据库里存的值转换成浏览器/小程序可访问的最终 URL。
 *
 * <p><b>为什么库里不直接存完整 URL</b>：
 * <ul>
 *   <li>本地存储存相对路径 {@code /static/uploads/...}，换域名/端口不用改库</li>
 *   <li>OSS 存 {@code oss://bucket/key} 稳定标识，
 *       将来 Bucket 换域名、切 CDN 或改回私有读时，只需改配置，无需全表 UPDATE</li>
 * </ul>
 *
 * <p>本类不依赖 OSS SDK —— 公共读 Bucket 的访问地址可直接拼接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageUrlResolver {

    private static final String OSS_PREFIX = "oss://";

    private final StorageProperties properties;

    /**
     * 解析为可访问 URL。
     *
     * @param stored 数据库里存的原始值，可能是相对路径、http(s) URL、oss:// 标识
     * @return 可直接用于 img src 的地址
     */
    public String resolve(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (!stored.startsWith(OSS_PREFIX)) {
            // 本地相对路径或已是完整 URL
            return stored;
        }
        // oss://bucket/key
        String rest = stored.substring(OSS_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return stored;
        }
        String bucket = rest.substring(0, slash);
        String key = rest.substring(slash + 1);

        StorageProperties.Oss cfg = properties.getOss();
        if (cfg == null) {
            return stored;
        }
        // 优先自定义域名 / CDN
        if (cfg.getPublicDomain() != null && !cfg.getPublicDomain().isBlank()) {
            return cfg.getPublicDomain().replaceAll("/+$", "") + "/" + key;
        }
        // 次选 Bucket 默认公网域名：https://{bucket}.{endpoint}/{key}
        String endpoint = cfg.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            log.warn("[图片地址] 缺少 OSS endpoint，原样返回 {}", stored);
            return stored;
        }
        String host = endpoint.replaceFirst("^https?://", "").replaceAll("/+$", "");
        String useBucket = (bucket == null || bucket.isBlank())
                ? (cfg.getBucket() == null ? "" : cfg.getBucket()) : bucket;
        return "https://" + useBucket + "." + host + "/" + key;
    }
}
