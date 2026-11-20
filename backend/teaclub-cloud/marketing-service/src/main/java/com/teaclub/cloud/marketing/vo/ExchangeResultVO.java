package com.teaclub.cloud.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "积分兑换结果")
public class ExchangeResultVO {
    private String exchangeNo;
    private String goodsName;
    private Long pointsCost;
    @Schema(description = "兑换后剩余积分")
    private Long remainPoints;
    @Schema(description = "券类商品生成的券码")
    private String couponNo;
    private LocalDateTime createTime;
}
