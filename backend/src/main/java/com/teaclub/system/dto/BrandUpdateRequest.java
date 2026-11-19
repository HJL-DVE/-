package com.teaclub.system.dto;

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

    @NotNull(message = "跨店消费开关不能为空")
    @Min(value = 0, message = "取值只能是 0 或 1")
    @Max(value = 1, message = "取值只能是 0 或 1")
    @Schema(description = "1 允许跨店消费，0 禁止")
    private Integer walletCrossStoreEnabled;

    @NotBlank(message = "扣款顺序不能为空")
    @Pattern(regexp = "^(GIFT_FIRST|PRINCIPAL_FIRST)$", message = "扣款顺序只能是 GIFT_FIRST 或 PRINCIPAL_FIRST")
    private String walletDeductOrder;

    @NotNull(message = "赠送金有效期不能为空")
    @Min(value = 0, message = "有效天数不能为负")
    @Max(value = 3650, message = "有效天数不能超过 3650")
    private Integer giftExpireDays;
}
