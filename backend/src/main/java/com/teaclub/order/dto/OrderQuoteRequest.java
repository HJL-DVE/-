package com.teaclub.order.dto;

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

    @NotEmpty(message = "商品不能为空")
    @Valid
    private List<OrderItemRequest> items;
}
