package com.teaclub.cloud.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 会员手机号注册请求。密码由本服务用 BCrypt 散列后交给 member-service 落库。 */
@Data
@Schema(description = "会员注册请求")
public class MemberRegisterRequest {

    @Schema(description = "手机号")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "登录密码（明文，仅在本服务内散列）")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    @Schema(description = "昵称")
    @Size(max = 64)
    private String nickname;
}
