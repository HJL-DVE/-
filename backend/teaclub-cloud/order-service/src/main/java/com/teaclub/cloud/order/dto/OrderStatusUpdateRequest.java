package com.teaclub.cloud.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "订单状态变更请求")
public class OrderStatusUpdateRequest {

    @NotBlank(message = "目标状态不能为空")
    @Schema(description = "目标状态：PAID / COMPLETED / CANCELLED / REFUNDING / REFUNDED")
    private String targetStatus;

    private String remark;
}
