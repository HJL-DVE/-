package com.teaclub.cloud.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "角色保存请求")
public class RoleSaveRequest {

    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$", message = "角色编码需为大写字母、数字或下划线，3-64 位")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64)
    private String roleName;

    @NotBlank(message = "数据范围不能为空")
    @Pattern(regexp = "^(ALL|STORE)$", message = "数据范围只能是 ALL 或 STORE")
    @Schema(description = "数据范围：ALL 全品牌 / STORE 授权门店")
    private String dataScope;

    private Integer sort;

    private String remark;

    @Schema(description = "分配的权限 ID 列表")
    private List<Long> permissionIds;
}
