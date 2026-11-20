package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分批次：获得积分时落一批，过期时按 expire_time 先进先出消耗。 */
@Data
@TableName("mkt_points_batch")
public class MktPointsBatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long memberId;
    private Long sourceTxnId;
    private Long points;
    private Long remainPoints;
    private LocalDateTime expireTime;
    /** ACTIVE / EXHAUSTED / EXPIRED */
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
