package com.teaclub.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "品牌信息")
public class BrandVO {
    private Long id;
    private String brandCode;
    private String brandName;
    private String logoUrl;
    private String contactPhone;

    @Schema(description = "储值是否允许跨店消费")
    private Integer walletCrossStoreEnabled;

    @Schema(description = "扣款顺序 GIFT_FIRST / PRINCIPAL_FIRST")
    private String walletDeductOrder;

    @Schema(description = "赠送金有效天数")
    private Integer giftExpireDays;

    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
