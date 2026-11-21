package com.teaclub.cloud.product.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.product.storage.FileStorageService;
import com.teaclub.cloud.product.storage.ImageUrlResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 会员头像上传（小程序端）。
 *
 * <p>微信 {@code chooseAvatar} 返回的是临时文件路径，必须上传到服务器换成永久地址，
 * 才能写入会员档案（member_db.mem_member.avatar_url）。本接口复用商品图片的存储组件，
 * 存储介质由 {@code teaclub.storage.type} 决定（local / oss），与商品图保持一致。
 *
 * <p>安全：不带 {@code @PreAuthorize} 权限点（会员角色没有管理端权限点），
 * 仅依赖网关 JWT 鉴权 —— 会员 token 必须有效才能调用。
 */
@Slf4j
@Tag(name = "会员头像", description = "微信小程序 chooseAvatar 头像上传")
@RestController
@RequestMapping("/api/product/upload")
@RequiredArgsConstructor
public class MemberAvatarController {

    /** 头像上限 5MB，足够手机原图压缩后的头像 */
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final FileStorageService fileStorageService;
    private final ImageUrlResolver imageUrlResolver;

    @Operation(summary = "上传会员头像，返回可直接访问的 url")
    @PostMapping("/avatar")
    public Result<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "请选择要上传的头像");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ResultCode.PARAM_INVALID, "头像不能超过 5MB");
        }
        String original = file.getOriginalFilename() == null ? "avatar.png" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(ext)) {
            throw new BizException(ResultCode.PARAM_INVALID, "只支持 jpg/jpeg/png/webp/gif 格式");
        }

        String stored;
        try (var in = file.getInputStream()) {
            stored = fileStorageService.upload(in, original, file.getContentType(), file.getSize());
        } catch (java.io.IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "读取头像失败：" + e.getMessage());
        }

        String previewUrl = imageUrlResolver.resolve(stored);
        log.info("[头像上传] 已保存 storage={} stored={} size={}", fileStorageService.type(), stored, file.getSize());

        Map<String, Object> data = new LinkedHashMap<>();
        // avatarUrl 落库统一用可直接访问的 URL，避免 oss:// 标识在小程序 image 组件无法展示
        data.put("url", previewUrl);
        data.put("previewUrl", previewUrl);
        data.put("storage", fileStorageService.type());
        return Result.ok(data);
    }
}
