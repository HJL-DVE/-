package com.teaclub.cloud.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 会员端商品详情：SPU 概要 + 该门店可售 SKU。 */
@Data
@Schema(description = "会员端商品详情")
public class AppSpuDetailVO {

    @Schema(description = "商品概要")
    private Spu spu;

    @Schema(description = "可售规格")
    private List<Sku> skus;

    @Data
    @Schema(description = "商品概要")
    public static class Spu {
        private Long spuId;
        private String spuName;
        private String imageUrl;
        private String description;
        private Long categoryId;
    }

    @Data
    @Schema(description = "可售规格")
    public static class Sku {
        private Long skuId;
        private String skuName;
        @Schema(description = "售价（分）：门店价优先，未设门店价时回落总部价")
        private Long price;
        @Schema(description = "规格 JSON（糖度/温度/加料枚举）")
        private String specJson;
    }
}
