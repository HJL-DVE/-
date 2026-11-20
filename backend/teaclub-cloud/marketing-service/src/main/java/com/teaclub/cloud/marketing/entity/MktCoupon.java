package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员持有的优惠券实例。
 *
 * <p>状态变更必须用 {@code UPDATE ... WHERE status = 前态} 的条件更新，
 * 通过受影响行数判断成功，<b>禁止先查后改</b>（并发下会双重核销）。
 */
@Data
@TableName("mkt_coupon")
public class MktCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String couponNo;
    private Long templateId;
    private String couponType;
    private Long memberId;
    private Long faceValue;
    private Integer discountRate;
    private Long maxDiscountAmount;
    private Long thresholdAmount;
    private String scopeType;
    private Long exchangeSpuId;
    private Long exchangeSkuId;
    /** {@link com.teaclub.cloud.marketing.enums.CouponStatus} */
    private String status;
    /** RECEIVE / GRANT / ACTIVITY */
    private String obtainType;
    private LocalDateTime obtainTime;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private String lockedOrderNo;
    private LocalDateTime lockedTime;
    private String usedOrderNo;
    private LocalDateTime usedTime;
    private Long discountAmount;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
