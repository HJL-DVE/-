package com.teaclub.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "商品分类保存请求")
public class CategorySaveRequest {

    @Schema(description = "父级分类 ID，0 表示顶级")
    private Long parentId = 0L;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64)
    private String categoryName;

    @Size(max = 512)
    private String iconUrl;

    private Integer sort = 0;

    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能是 ENABLED 或 DISABLED")
    private String status = "ENABLED";
}
