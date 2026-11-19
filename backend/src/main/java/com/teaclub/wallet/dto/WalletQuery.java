package com.teaclub.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钱包查询条件")
public class WalletQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String phone;
    private String keyword;
    private String status;

    public int safePageNo() { return pageNo == null || pageNo < 1 ? 1 : pageNo; }
    public int safePageSize() { return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100); }
}
