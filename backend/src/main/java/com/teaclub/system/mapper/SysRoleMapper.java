package com.teaclub.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询用户拥有的启用中角色。
     */
    @Select("""
            SELECT r.* FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.deleted = 0 AND r.status = 'ENABLED'
            """)
    List<SysRole> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询角色已分配的权限 ID 列表。
     */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIds(@Param("roleId") Long roleId);
}
