package com.teaclub.cloud.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 活动奖励配置（存于 {@code mkt_activity.config_json}）。
 *
 * <p>8 类活动共用这一份结构，靠字段是否为空来区分：
 * 例如满赠活动填 {@code thresholdAmount + couponTemplateIds}，
 * 生日福利填 {@code points + couponTemplateIds}。
 *
 * <p>示例：
 * <pre>
 * {"thresholdAmount":5000,"couponTemplateIds":[1],"points":100,"description":"满50送券+100积分"}
 * </pre>
 */
@Data
@Schema(description = "活动奖励配置")
public class ActivityRewardConfig {

    @Schema(description = "满额门槛（分），仅满赠活动使用；0=无门槛")
    private Long thresholdAmount;

    @Schema(description = "赠送的券模板 ID 列表")
    private List<Long> couponTemplateIds;

    @Schema(description = "赠送积分")
    private Long points;

    @Schema(description = "连续签到所需天数（连续签到活动）")
    private Integer continuousDays;

    @Schema(description = "沉睡天数（沉睡召回活动）")
    private Integer sleepingDays;

    @Schema(description = "奖励说明，展示给会员")
    private String description;
}
