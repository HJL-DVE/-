package com.teaclub.cloud.product.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云 OSS 存储。
 *
 * <p><b>访问方式</b>：Bucket 为公共读，图片可直接通过
 * {@code https://{bucket}.{endpoint}/{key}} 访问。
 * 数据库仍存 {@code oss://bucket/key} 稳定标识，由 {@link ImageUrlResolver}
 * 统一转成公网地址 —— 将来换域名或切 CDN 只改配置，不用全表 UPDATE。
 *
 * <p><b>配置不完整时启动即失败</b>，绝不静默降级为本地存储 ——
 * 与 payment-service 的 wechat-v3 适配器同一原则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.storage.type", havingValue = "oss")
public class AliyunOssStorageService implements FileStorageService {

    private final StorageProperties properties;

    private OSS client;
    private String bucket;

    @PostConstruct
    void init() {
        StorageProperties.Oss cfg = properties.getOss();
        if (isBlank(cfg.getEndpoint()) || isBlank(cfg.getBucket())
                || isBlank(cfg.getAccessKeyId()) || isBlank(cfg.getAccessKeySecret())) {
            throw new IllegalStateException(
                    "OSS 配置不完整：需要 teaclub.storage.oss.{endpoint,bucket,access-key-id,access-key-secret}");
        }
        this.bucket = cfg.getBucket();
        this.client = new OSSClientBuilder()
                .build(cfg.getEndpoint(), cfg.getAccessKeyId(), cfg.getAccessKeySecret());
        log.info("[存储-oss] 初始化完成 endpoint={} bucket={}", cfg.getEndpoint(), bucket);
    }

    @PreDestroy
    void destroy() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Override
    public String type() {
        return "oss";
    }

    @Override
    public String upload(InputStream in, String originalName, String contentType, long sizeBytes) {
        StorageProperties.Oss cfg = properties.getOss();
        String ext = LocalFileStorageService.extOf(originalName);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String objectKey = "products/" + datePath + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(sizeBytes);
            if (contentType != null && !contentType.isBlank()) {
                meta.setContentType(contentType);
            }
            client.putObject(bucket, objectKey, in, meta);
            log.info("[存储-oss] 上传成功 objectKey={} size={}", objectKey, sizeBytes);

            // 公共读 Bucket：落库存稳定标识 oss://bucket/key，
            // 由 ImageUrlResolver 统一拼接公网地址（换域名/CDN 时无需改库）
            return "oss://" + bucket + "/" + objectKey;
        } catch (Exception e) {
            log.error("[存储-oss] 上传失败", e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "图片上传到 OSS 失败：" + e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
