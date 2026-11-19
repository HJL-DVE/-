package com.teaclub.cloud.auth.client.dto;

import lombok.Data;

/** 调用 member-service {@code /internal/wx/bind} 的请求体。 */
@Data
public class MemberWxBindRequestDTO {
    private String openid;
    private Long brandId;
    private String nickname;
    private String avatarUrl;
    private String phone;
}
