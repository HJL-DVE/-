package com.teaclub.cloud.product.storage;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地文件存储：落 Docker 卷，由 nginx 提供 {@code /static/uploads/**}。
 *
 * <p>默认实现。答辩现场断网也能用，不依赖任何外部服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final StorageProperties properties;

    @Override
    public String type() {
        return "local";
    }

    @Override
    public String upload(InputStream in, String originalName, String contentType, long sizeBytes) {
        String ext = extOf(originalName);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            Path dir = Paths.get(properties.getLocalDir(), datePath);
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            String url = properties.getLocalUrlPrefix() + "/" + datePath + "/" + fileName;
            log.info("[存储-local] 保存成功 url={} size={}", url, sizeBytes);
            return url;
        } catch (IOException e) {
            log.error("[存储-local] 保存失败", e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "图片保存失败：" + e.getMessage());
        }
    }

    /** 取小写扩展名，无扩展名时默认 jpg */
    static String extOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "jpg";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
