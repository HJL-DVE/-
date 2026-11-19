package com.teaclub.cloud.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "门店信息")
public class StoreVO {
    private Long id;
    private Long brandId;
    private String storeCode;
    private String storeName;
    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String managerName;
    private String businessHours;
    private LocalDate openDate;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
