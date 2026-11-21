package com.teaclub.cloud.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "会员优惠券")
public class CouponVO {
    private Long id;
    private String couponNo;
    private Long templateId;
    private String couponName;
    private String couponType;
    private String couponTypeLabel;
    private Long faceValue;
    private Integer discountRate;
    private Long maxDiscountAmount;
    private Long thresholdAmount;
    private String scopeType;
    private String status;
    private String statusLabel;
    private String obtainType;
    private LocalDateTime obtainTime;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private String lockedOrderNo;
    private String usedOrderNo;
    private Long discountAmount;
    @Schema(description = "是否当前可用（未使用 + 在有效期内）")
    private Boolean usable;

    /** 不可用原因（例如 满30元可用），仅 usable=false 时有值 */
    private String unusableReason;
}
