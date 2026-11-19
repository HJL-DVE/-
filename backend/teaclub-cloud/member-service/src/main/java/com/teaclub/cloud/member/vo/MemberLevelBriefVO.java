package com.teaclub.cloud.member.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会员等级简要信息，供其它服务内部调用。
 */
@Data
@Schema(description = "会员等级简要信息")
public class MemberLevelBriefVO {
    private Long id;
    private String levelName;
    private Integer levelNo;
}
