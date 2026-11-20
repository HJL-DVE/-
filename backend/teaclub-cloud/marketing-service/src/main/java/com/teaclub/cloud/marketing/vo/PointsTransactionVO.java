package com.teaclub.cloud.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "积分流水")
public class PointsTransactionVO {
    private Long id;
    private String txnNo;
    private String bizType;
    private String bizTypeLabel;
    private String bizNo;
    private Long changePoints;
    private Long beforePoints;
    private Long afterPoints;
    private String remark;
    private LocalDateTime createTime;
}
