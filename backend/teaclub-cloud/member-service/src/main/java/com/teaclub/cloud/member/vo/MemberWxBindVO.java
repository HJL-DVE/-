package com.teaclub.cloud.member.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "微信绑定结果")
public class MemberWxBindVO {
    private Long memberId;
    private String memberNo;
    @Schema(description = "本次是否新建会员")
    private Boolean isNew;
}
