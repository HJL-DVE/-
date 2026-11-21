package com.teaclub.cloud.product.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.web.annotation.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 商品图片上传。
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>落盘位置由 {@code teaclub.upload.dir} 配置，容器内映射到与 nginx 共享的卷，
 *       因此返回的相对路径 {@code /static/uploads/...} 浏览器可直接访问</li>
 *   <li>只接受图片扩展名，按扩展名白名单校验；文件名由服务端生成（UUID），
 *       不使用客户端文件名，避免路径穿越与同名覆盖</li>
 *   <li>按 {@code yyyyMMdd} 分目录，避免单目录文件过多</li>
 * </ul>
 */
@Slf4j
@Tag(name = "商品图片上传", description = "商品主图/分类图标上传，返回可直接落库的相对路径")
@RestController
@RequestMapping("/api/admin/product/upload")
public class ProductUploadController {

    /** 单文件上限 10MB，允许手机原图直传；与 application.yml 的 multipart 配置保持一致 */
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private static final java.util.Set<String> ALLOWED =
            java.util.Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Value("${teaclub.upload.dir:/app/uploads}")
    private String uploadDir;

    @Operation(summary = "上传商品图片，返回相对路径（如 /static/uploads/20261121/xxx.jpg）")
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
            throw new BizException(ResultCode.PARAM_INVALID,
                    "只支持 jpg/jpeg/png/webp/gif 格式");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            Path dir = Paths.get(uploadDir, datePath);
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String url = "/static/uploads/" + datePath + "/" + fileName;
            log.info("[上传] 商品图片已保存 path={} size={}", url, file.getSize());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            data.put("name", original);
            data.put("size", file.getSize());
            return Result.ok(data);
        } catch (IOException e) {
            log.error("[上传] 保存失败", e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "图片保存失败：" + e.getMessage());
        }
    }
}
