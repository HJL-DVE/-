package com.teaclub.cloud.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.auth.entity.SysStore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysStoreMapper extends BaseMapper<SysStore> {

    /**
     * 统计门店编码占用数量（包含已逻辑删除的数据）。
     *
     * <p>唯一索引 uk_store_code 不区分 deleted，因此已被软删除的记录仍占用编码；
     * 这里显式统计，用于给出准确的业务提示而不是让数据库抛唯一约束异常。
     */
    @Select("SELECT COUNT(*) FROM sys_store WHERE brand_id = #{brandId} AND store_code = #{storeCode}")
    long countByCodeIncludingDeleted(@Param("brandId") Long brandId, @Param("storeCode") String storeCode);
}
