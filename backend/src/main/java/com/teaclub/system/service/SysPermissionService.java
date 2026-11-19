package com.teaclub.system.service;

import com.teaclub.system.dto.PermissionSaveRequest;
import com.teaclub.system.vo.PermissionVO;

import java.util.List;

public interface SysPermissionService {

    /** 权限树（全量）。 */
    List<PermissionVO> tree();

    /** 当前用户拥有的权限树（用于前端动态菜单）。 */
    List<PermissionVO> currentUserTree();

    PermissionVO detail(Long id);

    Long create(PermissionSaveRequest request);

    void update(Long id, PermissionSaveRequest request);

    void delete(Long id);
}
