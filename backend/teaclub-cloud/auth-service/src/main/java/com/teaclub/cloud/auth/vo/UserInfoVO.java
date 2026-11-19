package com.teaclub.cloud.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
@Schema(description = "登录用户信息")
public class UserInfoVO {

    private Long userId;
    private String username;
    private String realName;
    private String userType;
    private String avatarUrl;
    private Long brandId;

    @Schema(description = "角色编码集合")
    private Set<String> roles;

    @Schema(description = "权限标识集合")
    private Set<String> permissions;

    @Schema(description = "数据范围：ALL / STORE")
    private String dataScope;

    @Schema(description = "可访问门店列表")
    private List<StoreBriefVO> stores;
}
