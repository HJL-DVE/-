package com.teaclub.cloud.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 会员端商品列表项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会员端商品列表项")
public class AppSpuItemVO {
    private Long spuId;
    private String spuName;
    private String imageUrl;
    @Schema(description = "最低价（分）：门店价优先，未设门店价时回落总部价的最小值")
    private Long minPrice;
    private Long categoryId;
    private String categoryName;
}
