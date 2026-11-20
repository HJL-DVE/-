package com.teaclub.cloud.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "活动保存请求")
public class ActivitySaveRequest {

    @NotBlank(message = "活动名称不能为空")
    private String activityName;

    @NotBlank(message = "活动类型不能为空")
    @Schema(description = "NEW_MEMBER_GIFT/BIRTHDAY/FESTIVAL/CONTINUOUS_SIGN/FULL_GIFT/POINTS_MALL/STORE_SPECIAL/SLEEPING_RECALL")
    private String activityType;

    @Schema(description = "ALL / STORE")
    private String scopeType = "ALL";

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Schema(description = "奖励配置 JSON")
    private String configJson;

    @Schema(description = "总名额，0=不限")
    private Integer totalQuota = 0;

    @Schema(description = "每人限参与次数")
    private Integer perMemberLimit = 1;

    private Integer priority = 0;

    @Schema(description = "DRAFT/ENABLED/DISABLED")
    private String status = "DRAFT";

    private String description;

    @Schema(description = "适用门店（scopeType=STORE）")
    private List<Long> storeIds;
}
