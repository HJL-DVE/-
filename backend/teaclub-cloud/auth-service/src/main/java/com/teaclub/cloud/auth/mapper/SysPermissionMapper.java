package com.teaclub.cloud.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.auth.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询用户通过角色获得的全部权限点（去重，仅启用）。
     */
    @Select("""
            SELECT DISTINCT p.* FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
            INNER JOIN sys_role r ON r.id = rp.role_id
            WHERE ur.user_id = #{userId}
              AND p.deleted = 0 AND p.status = 'ENABLED'
              AND r.deleted = 0 AND r.status = 'ENABLED'
            """)
    List<SysPermission> selectByUserId(@Param("userId") Long userId);
}
