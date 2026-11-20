package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 营销活动。
 *
 * <p>8 类活动共用一张表，差异由 {@code activityType} 与 {@code configJson} 决定，
 * 避免每加一类活动就加一张表。
 */
@Data
@TableName("mkt_activity")
public class MktActivity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String activityNo;
    private String activityName;
    /** {@link com.teaclub.cloud.marketing.enums.ActivityType} */
    private String activityType;
    /** ALL / STORE */
    private String scopeType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 奖励与规则参数（赠券模板、满额门槛、积分倍率等） */
    private String configJson;
    private Integer totalQuota;
    private Integer usedQuota;
    private Integer perMemberLimit;
    private Integer priority;
    /** {@link com.teaclub.cloud.marketing.enums.ActivityStatus} */
    private String status;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
