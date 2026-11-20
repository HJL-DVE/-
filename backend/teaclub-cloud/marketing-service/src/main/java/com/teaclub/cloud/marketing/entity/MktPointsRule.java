package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分规则（按品牌）。 */
@Data
@TableName("mkt_points_rule")
public class MktPointsRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String ruleCode;
    private String ruleName;
    /** 每消费多少分（1 元=100 分）得 1 积分 */
    private Long amountPerPoint;
    private Long signInPoints;
    private Integer signInContinuousMax;
    private Long signInBonus;
    private Integer expireDays;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
