package com.teaclub.cloud.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "门店商品上下架请求")
public class StoreShelfRequest {

    @NotNull(message = "上架状态不能为空")
    @Min(value = 0, message = "取值只能是 0 或 1")
    @Max(value = 1, message = "取值只能是 0 或 1")
    @Schema(description = "1 上架，0 下架")
    private Integer isOnSale;

    private Integer sort;
}
