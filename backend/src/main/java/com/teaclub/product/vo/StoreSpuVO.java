package com.teaclub.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "门店商品")
public class StoreSpuVO {
    private Long id;
    private Long storeId;
    private Long spuId;
    private String spuCode;
    private String spuName;
    private String mainImage;
    private Long basePrice;
    private Long minPrice;
    private String spuStatus;
    private Integer isOnSale;
    private Integer sort;
    private List<StoreSkuVO> skus;
}
