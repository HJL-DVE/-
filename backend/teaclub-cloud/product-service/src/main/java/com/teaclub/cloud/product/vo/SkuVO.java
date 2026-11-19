package com.teaclub.cloud.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "商品规格")
public class SkuVO {
    private Long id;
    private String skuCode;
    private String skuName;
    private String specJson;
    @Schema(description = "总部建议价（分）")
    private Long price;
    @Schema(description = "成本价（分）")
    private Long costPrice;
    private String imageUrl;
    private Integer sort;
    private String status;
}
