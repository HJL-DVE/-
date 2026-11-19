package com.teaclub.cloud.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理端登录请求")
public class LoginRequest {

    @NotBlank(message = "登录账号不能为空")
    @Size(max = 64, message = "登录账号长度不能超过 64")
    @Schema(description = "登录账号", example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    @Schema(description = "密码", example = "TeaClub@123")
    private String password;
}
