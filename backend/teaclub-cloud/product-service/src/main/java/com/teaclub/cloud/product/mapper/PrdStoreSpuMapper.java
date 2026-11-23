package com.teaclub.cloud.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.product.entity.PrdStoreSpu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PrdStoreSpuMapper extends BaseMapper<PrdStoreSpu> {

    /**
     * 查询**已逻辑删除**的门店商品关系（绕过 @TableLogic 过滤）。
     *
     * <p>存在唯一键 uk_store_spu(store_id, spu_id)，而唯一索引不含 deleted 列，
     * 因此逻辑删除的记录仍占用唯一键。重新上架同款商品时需要先恢复它，
     * 否则 insert 会撞唯一键报 500。
     */
    @Select("""
            SELECT * FROM prd_store_spu
            WHERE store_id = #{storeId} AND spu_id = #{spuId} AND deleted = 1
            LIMIT 1
            """)
    PrdStoreSpu selectDeletedRel(@Param("storeId") Long storeId,
                                @Param("spuId") Long spuId);
}
