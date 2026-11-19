package com.teaclub.common.context;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 当前登录用户（请求级不可变快照）。
 */
@Data
@Builder
public class LoginUser {

    private Long userId;
    private String username;
    private String realName;
    private Long brandId;
    private String userType;
    /** 角色编码集合 */
    private Set<String> roles;
    /** 权限标识集合，如 store:order:list */
    private Set<String> permissions;
    /** 是否可访问全部门店（由角色 data_scope=ALL 决定） */
    private boolean allStoreAccess;
    /** 被授权门店 ID 集合 */
    private Set<Long> storeIds;
    private String jti;
}
