package com.teaclub.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "员工信息")
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String avatarUrl;
    private String userType;
    private String status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createTime;

    @Schema(description = "角色编码列表")
    private List<String> roleCodes;

    @Schema(description = "角色名称列表")
    private List<String> roleNames;

    @Schema(description = "已授权门店")
    private List<StoreBriefVO> stores;
}
