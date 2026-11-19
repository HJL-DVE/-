package com.teaclub.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "门店 SKU 定价请求")
public class StoreSkuPriceRequest {

    @NotNull(message = "门店售价不能为空")
    @Min(value = 1, message = "门店售价必须大于 0")
    @Schema(description = "门店售价（分）")
    private Long storePrice;

    @Min(value = 0, message = "取值只能是 0 或 1")
    @Max(value = 1, message = "取值只能是 0 或 1")
    @Schema(description = "1 上架，0 下架")
    private Integer isOnSale = 1;
}
