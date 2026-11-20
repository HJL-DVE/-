package com.teaclub.cloud.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "优惠券模板")
public class CouponTemplateVO {
    private Long id;
    private String templateNo;
    private String couponName;
    private String couponType;
    private String couponTypeLabel;
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
    private String validType;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private Integer validDays;
    private Integer newMemberOnly;
    private Integer birthdayOnly;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    @Schema(description = "适用门店 ID（scopeType=STORE 时）")
    private List<Long> storeIds;
    @Schema(description = "适用商品 SPU ID（scopeType=GOODS 时）")
    private List<Long> spuIds;
    @Schema(description = "限制的会员等级 ID")
    private List<Long> levelIds;
    @Schema(description = "当前会员已领取张数（可选）")
    private Integer receivedCount;
}
