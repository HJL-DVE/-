package com.teaclub.cloud.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 积分规则（管理端展示，读 mkt_points_rule 真实数据）。 */
@Data
@Schema(description = "积分规则")
public class PointsRuleVO {

    @Schema(description = "规则编码，如 ORDER_EARN / SIGN_IN")
    private String ruleCode;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "每消费多少分得 1 积分（1 元 = 100 分）")
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

    @Schema(description = "生效场景说明")
    private String usedBy;
}
