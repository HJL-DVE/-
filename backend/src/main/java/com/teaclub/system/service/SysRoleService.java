package com.teaclub.system.service;

import com.teaclub.common.api.PageResult;
import com.teaclub.system.dto.RoleSaveRequest;
import com.teaclub.system.vo.RoleVO;

import java.util.List;

public interface SysRoleService {

    PageResult<RoleVO> page(Integer pageNo, Integer pageSize, String keyword, String status);

    List<RoleVO> listAll();

    RoleVO detail(Long id);

    Long create(RoleSaveRequest request);

    void update(Long id, RoleSaveRequest request);

    void delete(Long id);

    void grantPermissions(Long roleId, List<Long> permissionIds);
}
