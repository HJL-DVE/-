package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动参与记录。
 *
 * <p>{@code uk_activity_member_biz(activity_id, member_id, biz_no)} 是
 * 「同一活动同一会员同一业务单只发一次」的最终防线 —— 依赖它可以放心重放消息。
 */
@Data
@TableName("mkt_activity_record")
public class MktActivityRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long activityId;
    private Long memberId;
    private String bizNo;
    private String rewardJson;
    private LocalDateTime createTime;
}
