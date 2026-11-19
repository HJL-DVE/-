package com.teaclub.cloud.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建订单请求。所有金额由服务端计算，请求体不接受任何金额字段")
public class OrderCreateRequest {

    @NotNull(message = "门店不能为空")
    private Long storeId;

    @Schema(description = "会员 ID；散客可为空（仅 POS）")
    private Long memberId;

    @Schema(description = "订单来源 MINIAPP / POS / ADMIN")
    private String orderSource = "POS";

    @Schema(description = "使用的会员优惠券 ID")
    private Long memberCouponId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否立即使用余额支付")
    private Boolean payWithBalance = false;

    @NotEmpty(message = "订单商品不能为空")
    @Valid
    private List<OrderItemRequest> items;
}
