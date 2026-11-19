package com.teaclub.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "订单明细项")
public class OrderItemVO {
    private Long id;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuName;
    private String specJson;
    private String imageUrl;
    @Schema(description = "成交单价（分）")
    private Long unitPrice;
    private Integer quantity;
    @Schema(description = "行小计（分）")
    private Long totalAmount;
    private Long discountAmount;
    @Schema(description = "行实付（分）")
    private Long actualAmount;
    private Integer refundedQuantity;
}
