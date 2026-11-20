package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 优惠券模板。规则定义；会员拿到的是模板的实例（{@link MktCoupon}）。 */
@Data
@TableName("mkt_coupon_template")
public class MktCouponTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String templateNo;
    private String couponName;
    /** {@link com.teaclub.cloud.marketing.enums.CouponType} */
    private String couponType;
    /** {@link com.teaclub.cloud.marketing.enums.CouponScopeType} */
    private String scopeType;
    private Long faceValue;
    private Integer discountRate;
    private Long maxDiscountAmount;
    private Long thresholdAmount;
    private Long exchangeSpuId;
    private Long exchangeSkuId;
    private Integer totalCount;
    private Integer issuedCount;
    private Integer perMemberLimit;
    /** FIXED=固定区间；RELATIVE=领取后 validDays 天 */
    private String validType;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private Integer validDays;
    private Integer newMemberOnly;
    private Integer birthdayOnly;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
