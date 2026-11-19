package com.teaclub.cloud.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "商品查询条件")
public class SpuQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 20;

    @Schema(description = "商品名称/编码关键字")
    private String keyword;

    private Long categoryId;
    private String status;

    public int safePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
