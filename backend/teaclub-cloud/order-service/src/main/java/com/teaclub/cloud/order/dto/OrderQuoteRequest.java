package com.teaclub.cloud.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "订单试算请求（不落单，仅返回金额明细）")
public class OrderQuoteRequest {

    @NotNull(message = "门店不能为空")
    private Long storeId;

    private Long memberId;
    private Long memberCouponId;

    @Schema(description = "使用积分数（1 积分=1 分钱；可为空=不用）")
    private Long usePoints;

    @NotEmpty(message = "商品不能为空")
    @Valid
    private List<OrderItemRequest> items;
}
