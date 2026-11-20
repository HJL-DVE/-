package com.teaclub.cloud.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "优惠券模板保存请求")
public class CouponTemplateSaveRequest {

    @NotBlank(message = "券名称不能为空")
    private String couponName;

    @NotBlank(message = "券类型不能为空")
    @Schema(description = "FULL_REDUCTION/DISCOUNT/NO_THRESHOLD/GOODS_EXCHANGE/NEW_MEMBER/BIRTHDAY")
    private String couponType;

    @Schema(description = "ALL/STORE/GOODS")
    private String scopeType = "ALL";

    @Schema(description = "面额（分）")
    private Long faceValue = 0L;

    @Schema(description = "折扣率万分数，9500=95折")
    private Integer discountRate = 10000;

    @Schema(description = "折扣封顶（分）")
    private Long maxDiscountAmount;

    @Schema(description = "满减门槛（分）")
    private Long thresholdAmount = 0L;

    private Long exchangeSpuId;
    private Long exchangeSkuId;

    @Schema(description = "发行总量，0=不限")
    private Integer totalCount = 0;

    @Schema(description = "每人限领")
    private Integer perMemberLimit = 1;

    @Schema(description = "FIXED / RELATIVE")
    private String validType = "RELATIVE";

    private LocalDateTime validStart;
    private LocalDateTime validEnd;

    @Schema(description = "领取后有效天数（validType=RELATIVE）")
    private Integer validDays = 30;

    private Integer newMemberOnly = 0;
    private Integer birthdayOnly = 0;

    @Schema(description = "DRAFT/ENABLED")
    private String status = "DRAFT";

    private String remark;

    @Schema(description = "适用门店（scopeType=STORE）")
    private List<Long> storeIds;

    @Schema(description = "适用商品 SPU（scopeType=GOODS）")
    private List<Long> spuIds;

    @Schema(description = "限制的会员等级")
    private List<Long> levelIds;
}
