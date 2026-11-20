package com.teaclub.cloud.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "优惠券试算结果（给 order-service 调用）")
public class CouponQuoteVO {

    @Schema(description = "券是否可用")
    private Boolean available;

    @Schema(description = "不可用原因码")
    private String reasonCode;

    @Schema(description = "不可用原因描述")
    private String reason;

    @Schema(description = "实际可抵扣金额（分）")
    private Long discountAmount;

    @Schema(description = "券 ID")
    private Long couponId;

    @Schema(description = "券码")
    private String couponNo;

    @Schema(description = "券类型")
    private String couponType;
}
