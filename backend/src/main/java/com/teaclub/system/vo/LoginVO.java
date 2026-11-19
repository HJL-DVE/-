package com.teaclub.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "登录响应")
public class LoginVO {
    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private UserInfoVO userInfo;
}
