package com.teaclub.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "下单商品项。注意：<b>不含价格字段</b>，价格由服务端按门店现价计算")
public class OrderItemRequest {

    @NotNull(message = "商品规格不能为空")
    private Long skuId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 999, message = "单品数量不能超过 999")
    private Integer quantity;
}
