package com.teaclub.cloud.auth.service;

import com.teaclub.cloud.auth.vo.PermissionVO;

import java.util.List;

public interface SysPermissionService {

    /** 权限树（全量，用于角色授权）。 */
    List<PermissionVO> tree();
}
