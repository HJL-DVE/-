package com.teaclub.cloud.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "权限点保存请求")
public class PermissionSaveRequest {

    @Schema(description = "父级 ID，0 表示根节点")
    private Long parentId = 0L;

    @NotBlank(message = "权限标识不能为空")
    @Size(max = 96)
    @Pattern(regexp = "^[a-z][a-zA-Z0-9:_-]{2,95}$", message = "权限标识格式不合法")
    private String permCode;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 64)
    private String permName;

    @NotBlank(message = "权限类型不能为空")
    @Pattern(regexp = "^(MENU|BUTTON|API)$", message = "权限类型只能是 MENU / BUTTON / API")
    private String permType;

    @Size(max = 128)
    private String routePath;

    @Size(max = 128)
    private String component;

    @Size(max = 64)
    private String icon;

    private Integer sort = 0;

    private Integer visible = 1;

    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能是 ENABLED 或 DISABLED")
    private String status = "ENABLED";
}
