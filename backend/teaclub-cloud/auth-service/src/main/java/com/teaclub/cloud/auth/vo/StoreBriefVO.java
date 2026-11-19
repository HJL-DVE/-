package com.teaclub.cloud.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "门店简要信息")
public class StoreBriefVO {
    private Long storeId;
    private String storeCode;
    private String storeName;
    private Boolean isDefault;
}
