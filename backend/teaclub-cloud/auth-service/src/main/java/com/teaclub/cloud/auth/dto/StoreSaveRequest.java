package com.teaclub.cloud.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "门店保存请求")
public class StoreSaveRequest {

    @NotBlank(message = "门店编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,32}$", message = "门店编码由 2-32 位字母、数字、下划线或中划线组成")
    private String storeCode;

    @NotBlank(message = "门店名称不能为空")
    @Size(max = 64)
    private String storeName;

    @Size(max = 32)
    private String province;

    @Size(max = 32)
    private String city;

    @Size(max = 32)
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255)
    private String address;

    @DecimalMin(value = "-180.0", message = "经度范围 -180 ~ 180")
    @DecimalMax(value = "180.0", message = "经度范围 -180 ~ 180")
    @Schema(description = "经度")
    private BigDecimal longitude;

    @DecimalMin(value = "-90.0", message = "纬度范围 -90 ~ 90")
    @DecimalMax(value = "90.0", message = "纬度范围 -90 ~ 90")
    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Pattern(regexp = "^[0-9-]{6,32}$", message = "门店电话格式不正确")
    private String phone;

    @Size(max = 32)
    private String managerName;

    @Size(max = 64)
    private String businessHours;

    private LocalDate openDate;

    @Pattern(regexp = "^(OPEN|SUSPENDED|CLOSED)$", message = "状态只能是 OPEN / SUSPENDED / CLOSED")
    private String status = "OPEN";

    @Size(max = 255)
    private String remark;
}
