package com.teaclub.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "商品分类")
public class CategoryVO {
    private Long id;
    private Long parentId;
    private String categoryName;
    private String iconUrl;
    private Integer sort;
    private String status;
    @Schema(description = "该分类下商品数量")
    private Long productCount;
    private List<CategoryVO> children = new ArrayList<>();
}
