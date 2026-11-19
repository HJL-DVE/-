package com.teaclub.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "角色信息")
public class RoleVO {
    private Long id;
    private String roleCode;
    private String roleName;
    private String dataScope;
    private Integer isBuiltin;
    private Integer sort;
    private String status;
    private String remark;
    private LocalDateTime createTime;

    @Schema(description = "已分配权限 ID")
    private List<Long> permissionIds;

    @Schema(description = "已分配权限数量")
    private Integer permissionCount;
}
