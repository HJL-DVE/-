package com.teaclub.cloud.auth.client.dto;

import lombok.Data;

/** member-service 绑定/注册内部接口的返回体。 */
@Data
public class MemberBindDTO {
    private Long memberId;
    private String memberNo;
    private Boolean isNew;
}
