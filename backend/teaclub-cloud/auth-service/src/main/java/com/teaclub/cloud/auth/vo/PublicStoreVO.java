package com.teaclub.cloud.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** 小程序门店信息（公开接口）。 */
@Data
@Schema(description = "小程序门店信息")
public class PublicStoreVO {
    private Long id;
    private String storeName;
    private String address;
    private String phone;
    @Schema(description = "营业状态：OPEN 营业中 / SUSPENDED 暂停营业 / CLOSED 已停业")
    private String businessStatus;
    @Schema(description = "营业开始时间，来自 business_hours 前半段")
    private String openTime;
    @Schema(description = "营业结束时间，来自 business_hours 后半段")
    private String closeTime;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
