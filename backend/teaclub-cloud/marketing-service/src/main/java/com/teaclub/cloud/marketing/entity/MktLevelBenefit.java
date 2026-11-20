package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 会员等级营销权益。等级定义本身在 member-service。 */
@Data
@TableName("mkt_level_benefit")
public class MktLevelBenefit {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long levelId;
    /** {@link com.teaclub.cloud.marketing.enums.BenefitCode} */
    private String benefitCode;
    private String benefitName;
    /** 权益参数：券模板 ID / 积分倍率 ×100 / 积分数 */
    private String benefitValue;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
