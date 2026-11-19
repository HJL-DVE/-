package com.teaclub.cloud.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "微信 openid 绑定会员请求（内部接口）")
public class MemberWxBindRequest {

    @Schema(description = "微信小程序 openid")
    @NotBlank(message = "openid 不能为空")
    @Size(max = 64)
    private String openid;

    @Schema(description = "品牌 ID")
    @NotNull(message = "brandId 不能为空")
    private Long brandId;

    @Schema(description = "昵称（首次登录同步）")
    @Size(max = 64)
    private String nickname;

    @Schema(description = "头像地址（首次登录同步）")
    @Size(max = 512)
    private String avatarUrl;

    @Schema(description = "手机号（小程序授权手机号时传入，可空）")
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
}
