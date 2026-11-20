package com.teaclub.cloud.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "营销活动")
public class ActivityVO {
    private Long id;
    private String activityNo;
    private String activityName;
    private String activityType;
    private String activityTypeLabel;
    private String scopeType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String configJson;
    private Integer totalQuota;
    private Integer usedQuota;
    private Integer perMemberLimit;
    private Integer priority;
    private String status;
    private String statusLabel;
    private String description;
    private LocalDateTime createTime;
    private List<Long> storeIds;
    @Schema(description = "当前会员是否已参与（活动列表页用）")
    private Boolean joined;
    @Schema(description = "当前会员还可参与次数")
    private Integer remainTimes;
}
