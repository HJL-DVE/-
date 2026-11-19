package com.teaclub.cloud.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 会员端可售分类（按门店在售商品推导）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会员端商品分类")
public class AppCategoryVO {
    private Long categoryId;
    private String categoryName;
    private String iconUrl;
    private Integer sort;
}
