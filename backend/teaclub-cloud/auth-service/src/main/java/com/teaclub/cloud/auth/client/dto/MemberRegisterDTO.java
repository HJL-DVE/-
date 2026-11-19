package com.teaclub.cloud.auth.client.dto;

import lombok.Data;

/** 调用 member-service {@code /internal/register} 的请求体。 */
@Data
public class MemberRegisterDTO {
    private Long brandId;
    private String phone;
    /** BCrypt 散列（auth-service 生成，member-service 只落库）。 */
    private String passwordHash;
    private String nickname;
}
