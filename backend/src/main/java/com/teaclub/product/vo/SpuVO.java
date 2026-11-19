package com.teaclub.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "商品信息")
public class SpuVO {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String spuCode;
    private String spuName;
    private String subtitle;
    private String description;
    private String mainImage;
    private String imagesJson;
    @Schema(description = "总部建议起售价（分）")
    private Long basePrice;
    @Schema(description = "最低保护价（分）")
    private Long minPrice;
    private String unit;
    private Integer hasSku;
    private Integer sort;
    private String status;
    private LocalDateTime createTime;
    private List<SkuVO> skus;
}
