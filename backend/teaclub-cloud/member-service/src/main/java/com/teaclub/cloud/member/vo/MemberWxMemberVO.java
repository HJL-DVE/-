package com.teaclub.cloud.member.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "openid 对应的会员身份")
public class MemberWxMemberVO {
    private Long memberId;
    private String memberNo;
}
