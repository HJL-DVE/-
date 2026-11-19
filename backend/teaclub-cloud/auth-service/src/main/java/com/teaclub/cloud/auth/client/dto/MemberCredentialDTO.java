package com.teaclub.cloud.auth.client.dto;

import lombok.Data;

/** member-service 会员登录凭据（内部接口返回）。 */
@Data
public class MemberCredentialDTO {
    private Long memberId;
    private String memberNo;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String status;
}
