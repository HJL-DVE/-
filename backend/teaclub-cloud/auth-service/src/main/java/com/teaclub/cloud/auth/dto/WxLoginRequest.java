package com.teaclub.cloud.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 小程序登录请求。 */
@Data
@Schema(description = "微信小程序登录请求")
public class WxLoginRequest {

    @Schema(description = "wx.login 拿到的 code")
    @NotBlank(message = "code 不能为空")
    private String code;

    @Size(max = 64)
    private String nickname;

    @Size(max = 512)
    private String avatarUrl;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
}
