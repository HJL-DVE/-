package com.teaclub.cloud.member.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "会员等级信息")
public class MemberLevelVO {
    private Long id;
    private String levelCode;
    private String levelName;
    private Integer levelNo;
    private String upgradeType;
    @Schema(description = "升级门槛（分）")
    private Long upgradeThreshold;
    @Schema(description = "折扣万分比")
    private Integer discountRate;
    private Integer pointRate;
    private String iconUrl;
    private String benefits;
    private String status;
    private LocalDateTime createTime;
    @Schema(description = "该等级会员数")
    private Long memberCount;
}
