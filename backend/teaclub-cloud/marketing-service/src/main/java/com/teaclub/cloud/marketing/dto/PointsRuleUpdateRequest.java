package com.teaclub.cloud.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 积分规则更新请求（ruleCode 不可改，只允许改数值与状态）。 */
@Data
@Schema(description = "积分规则更新请求")
public class PointsRuleUpdateRequest {

    @Schema(description = "多少金额得 1 积分（分），必须 > 0")
    private Long amountPerPoint;

    @Schema(description = "签到基础积分")
    private Long signInPoints;

    @Schema(description = "连续签到上限（天）")
    private Integer signInContinuousMax;

    @Schema(description = "连续签到加成（分/天）")
    private Long signInBonus;

    @Schema(description = "积分有效期（天），0 表示永不过期")
    private Integer expireDays;

    @Schema(description = "状态 ENABLED / DISABLED")
    private String status;
}
