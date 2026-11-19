package com.teaclub.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "门店 SKU 定价")
public class StoreSkuVO {
    private Long id;
    private Long skuId;
    private String skuCode;
    private String skuName;
    @Schema(description = "总部建议价（分）")
    private Long basePrice;
    @Schema(description = "门店售价（分）")
    private Long storePrice;
    @Schema(description = "最低保护价（分）")
    private Long minPrice;
    private Integer isOnSale;
}
