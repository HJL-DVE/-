package com.teaclub.cloud.security.context;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * 当前登录用户（请求级不可变快照）。
 *
 * <p>由 Gateway 解析 JWT 后通过请求头传给下游服务；
 * 下游服务用 {@link UserContext} 读取，**仍需自行做权限与数据范围校验**，
 * 不能因为网关校验过就跳过。
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

    /** 权限标识集合 */
    private Set<String> permissions;

    /** 是否可访问全部门店 */
    private boolean allStoreAccess;

    /** 被授权门店 ID 集合 */
    private Set<Long> storeIds;

    private String jti;
}
