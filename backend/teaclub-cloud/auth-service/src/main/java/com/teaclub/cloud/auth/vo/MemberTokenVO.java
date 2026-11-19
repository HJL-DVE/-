package com.teaclub.cloud.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/** 会员端登录/注册响应（契约：token / expiresIn / memberId / memberNo / isNew / nickname / avatarUrl / phone）。 */
@Data
@Builder
@Schema(description = "会员端登录响应")
public class MemberTokenVO {
    private String token;
    private Long expiresIn;
    private Long memberId;
    private String memberNo;
    @Schema(description = "本次是否新建会员")
    private Boolean isNew;
    private String nickname;
    private String avatarUrl;
    private String phone;
}
