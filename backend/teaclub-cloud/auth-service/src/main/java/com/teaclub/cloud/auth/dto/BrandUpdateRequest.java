package com.teaclub.cloud.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "品牌信息更新请求")
public class BrandUpdateRequest {

    @NotBlank(message = "品牌名称不能为空")
    @Size(max = 64)
    private String brandName;

    @Size(max = 512)
    private String logoUrl;

    @Size(max = 32)
    private String contactPhone;

    @NotBlank(message = "扣款顺序不能为空")
    @Pattern(regexp = "^(GIFT_FIRST|PRINCIPAL_FIRST)$", message = "扣款顺序只能是 GIFT_FIRST 或 PRINCIPAL_FIRST")
    private String walletDeductOrder;

}
