package com.teaclub.cloud.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会员注册请求（内部接口）。
 *
 * <p>密码由 auth-service 用 BCrypt 加密后传入，本服务只负责落库，
 * 不持有明文、也不做算法选择。
 */
@Data
@Schema(description = "会员注册请求（内部接口）")
public class MemberRegisterRequest {

    @Schema(description = "品牌 ID")
    @NotNull(message = "brandId 不能为空")
    private Long brandId;

    @Schema(description = "手机号")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "密码散列值（BCrypt）")
    @NotBlank(message = "密码不能为空")
    @Size(max = 100)
    private String passwordHash;

    @Schema(description = "昵称")
    @Size(max = 64)
    private String nickname;
}
