package com.teaclub.cloud.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "流水查询条件")
public class TransactionQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private Long memberId;
    private Long storeId;
    private String bizType;
    private String bizNo;
    private LocalDate startDate;
    private LocalDate endDate;

    public int safePageNo() { return pageNo == null || pageNo < 1 ? 1 : pageNo; }
    public int safePageSize() { return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100); }
}
