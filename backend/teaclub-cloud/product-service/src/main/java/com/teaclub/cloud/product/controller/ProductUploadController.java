package com.teaclub.cloud.product.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.product.storage.FileStorageService;
import com.teaclub.cloud.product.storage.ImageUrlResolver;
import com.teaclub.cloud.web.annotation.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 商品图片上传。
 *
 * <p><b>存储介质由 {@code teaclub.storage.type} 决定</b>（local / oss），
 * 本控制器只做校验与转发，不感知具体存储实现。
 *
 * <p>返回两个地址：
 * <ul>
 *   <li>{@code url}：写入数据库的稳定标识（本地相对路径 或 oss://bucket/key）</li>
 *   <li>{@code previewUrl}：立即可访问的地址（OSS 私有 Bucket 为签名 URL，会过期）</li>
 * </ul>
 * 分开的原因：签名 URL 会过期，落库必须是稳定标识；而前端上传后要立刻预览，需要一个可用地址。
 */
@Slf4j
@Tag(name = "商品图片上传", description = "商品主图/分类图标上传，支持本地存储与阿里云 OSS")
@RestController
@RequestMapping("/api/admin/product/upload")
@RequiredArgsConstructor
public class ProductUploadController {

    /** 单文件上限 10MB，允许手机原图直传；与 application.yml 的 multipart 配置保持一致 */
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final FileStorageService fileStorageService;
    private final ImageUrlResolver imageUrlResolver;

    @Operation(summary = "上传商品图片")
    @PostMapping("/image")
    @PreAuthorize("hasAuthority('product:spu:update')")
    @AuditLog(module = "商品管理", value = "上传商品图片")
    public Result<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ResultCode.PARAM_INVALID, "图片不能超过 10MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(ext)) {
            throw new BizException(ResultCode.PARAM_INVALID, "只支持 jpg/jpeg/png/webp/gif 格式");
        }

        String stored;
        try (var in = file.getInputStream()) {
            stored = fileStorageService.upload(in, original, file.getContentType(), file.getSize());
        } catch (java.io.IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "读取上传文件失败：" + e.getMessage());
        }

        log.info("[上传] 商品图片已保存 storage={} path={} size={}",
                fileStorageService.type(), stored, file.getSize());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", stored);
        data.put("previewUrl", imageUrlResolver.resolve(stored));
        data.put("name", original);
        data.put("size", file.getSize());
        data.put("storage", fileStorageService.type());
        return Result.ok(data);
    }
}
