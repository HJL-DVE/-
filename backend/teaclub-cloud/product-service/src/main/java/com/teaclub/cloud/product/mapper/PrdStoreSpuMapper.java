package com.teaclub.cloud.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.product.entity.PrdStoreSpu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 恢复已逻辑删除的门店商品关系。
     *
     * <p><b>为什么不能用 updateById</b>：{@code @TableLogic} 会让 MyBatis-Plus
     * 在 UPDATE 时自动追加 {@code AND deleted = 0} 条件，对已删除的行更新不到（影响行数为 0）。
     * 唯一键 uk_store_spu(store_id, spu_id) 又不包含 deleted 列，重新上架同款商品
     * 直接 insert 会撞唯一键报 500。因此这里用自定义 UPDATE 显式恢复。
     */
    @Update("""
            UPDATE prd_store_spu
            SET deleted = 0, is_on_sale = #{isOnSale}, sort = #{sort}, update_time = NOW(3)
            WHERE store_id = #{storeId} AND spu_id = #{spuId}
            """)
    int restoreDeletedRel(@Param("storeId") Long storeId,
                         @Param("spuId") Long spuId,
                         @Param("isOnSale") Integer isOnSale,
                         @Param("sort") Integer sort);
}
