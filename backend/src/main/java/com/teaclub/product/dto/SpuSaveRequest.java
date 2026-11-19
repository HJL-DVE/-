package com.teaclub.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "商品 SPU 保存请求")
public class SpuSaveRequest {

    @NotNull(message = "商品分类不能为空")
    private Long categoryId;

    @NotBlank(message = "商品编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,32}$", message = "商品编码由 2-32 位字母、数字、下划线或中划线组成")
    private String spuCode;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128)
    private String spuName;

    @Size(max = 255)
    private String subtitle;

    private String description;

    @Size(max = 512)
    private String mainImage;

    private String imagesJson;

    @NotNull(message = "建议售价不能为空")
    @Min(value = 1, message = "建议售价必须大于 0")
    @Schema(description = "总部建议起售价（分）")
    private Long basePrice;

    @NotNull(message = "最低保护价不能为空")
    @Min(value = 0, message = "最低保护价不能为负")
    @Schema(description = "最低保护价（分），门店定价不得低于此值")
    private Long minPrice;

    @Size(max = 16)
    private String unit = "杯";

    @Pattern(regexp = "^(ON_SALE|OFF_SALE)$", message = "状态只能是 ON_SALE 或 OFF_SALE")
    private String status = "ON_SALE";

    private Integer sort = 0;

    @Schema(description = "SKU 列表（至少一个）")
    @NotEmpty(message = "至少需要一个 SKU")
    @Valid
    private List<SkuSaveRequest> skus;
}
