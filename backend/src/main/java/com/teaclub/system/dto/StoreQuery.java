package com.teaclub.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "门店查询条件")
public class StoreQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 20;

    @Schema(description = "门店名称/编码关键字")
    private String keyword;

    private String city;
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
