package com.teaclub.cloud.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "签到结果")
public class SignInResultVO {
    @Schema(description = "本次获得积分")
    private Long points;
    @Schema(description = "连续签到天数")
    private Integer continuousDays;
    @Schema(description = "签到日期")
    private LocalDate signDate;
    @Schema(description = "是否首次签到为真；已签到返回 false")
    private Boolean firstTime;
    @Schema(description = "签到后总积分")
    private Long totalPoints;
    @Schema(description = "额外加成说明")
    private String bonusRemark;
}
