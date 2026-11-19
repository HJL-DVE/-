package com.teaclub.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 统计登录账号占用数量（包含已逻辑删除的数据）。
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE brand_id = #{brandId} AND username = #{username}")
    long countByUsernameIncludingDeleted(@Param("brandId") Long brandId, @Param("username") String username);
}
