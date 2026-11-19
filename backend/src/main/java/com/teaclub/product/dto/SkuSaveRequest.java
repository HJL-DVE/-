package com.teaclub.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "SKU 保存请求")
public class SkuSaveRequest {

    @Schema(description = "SKU ID，新增时为空")
    private Long id;

    @NotBlank(message = "规格编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,32}$", message = "规格编码由 2-32 位字母、数字、下划线或中划线组成")
    private String skuCode;

    @NotBlank(message = "规格名称不能为空")
    @Size(max = 128)
    private String skuName;

    @Schema(description = "规格键值对 JSON，如 \u007b\"杯型\":\"大杯\"\u007d")
    private String specJson;

    @NotNull(message = "售价不能为空")
    @Min(value = 1, message = "售价必须大于 0")
    @Schema(description = "总部建议价（分）")
    private Long price;

    @Min(value = 0, message = "成本价不能为负")
    @Schema(description = "成本价（分）")
    private Long costPrice = 0L;

    @Size(max = 512)
    private String imageUrl;

    private Integer sort = 0;

    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能是 ENABLED 或 DISABLED")
    private String status = "ENABLED";
}
