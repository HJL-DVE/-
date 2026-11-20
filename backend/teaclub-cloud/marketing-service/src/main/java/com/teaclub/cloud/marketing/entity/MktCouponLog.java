package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 优惠券操作流水（锁定/解锁/核销/释放/过期），用于排障与对账。 */
@Data
@TableName("mkt_coupon_log")
public class MktCouponLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long couponId;
    private String couponNo;
    private Long memberId;
    /** {@link com.teaclub.cloud.marketing.enums.CouponLogAction} */
    private String action;
    private String orderNo;
    private Long amount;
    private String fromStatus;
    private String toStatus;
    private String traceId;
    private String remark;
    private LocalDateTime createTime;
}
