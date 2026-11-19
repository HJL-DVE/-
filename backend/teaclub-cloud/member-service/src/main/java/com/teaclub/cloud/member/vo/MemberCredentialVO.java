package com.teaclub.cloud.member.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会员登录凭据（内部接口）。
 *
 * <p>包含密码散列值，仅供 auth-service 在服务网内校验，
 * 不得经网关暴露给前端。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会员登录凭据（内部接口）")
public class MemberCredentialVO {
    private Long memberId;
    private String memberNo;
    private String phone;
    @Schema(description = "BCrypt 密码散列，未设置密码时为 null")
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String status;
}
