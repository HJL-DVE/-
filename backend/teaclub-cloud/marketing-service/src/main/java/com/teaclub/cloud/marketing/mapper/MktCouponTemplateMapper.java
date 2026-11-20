package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktCouponTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MktCouponTemplateMapper extends BaseMapper<MktCouponTemplate> {

    /**
     * 发放计数原子 +1。
     *
     * <p>用条件 UPDATE 保证「不超发」：{@code total_count = 0} 表示不限量，
     * 否则必须 {@code issued_count < total_count}。返回 0 表示已发完。
     */
    @Update("""
            UPDATE mkt_coupon_template
            SET issued_count = issued_count + 1, update_time = NOW(3)
            WHERE id = #{id}
              AND deleted = 0
              AND status = 'ENABLED'
              AND (total_count = 0 OR issued_count < total_count)
            """)
    int tryIssueOne(@Param("id") Long id);

    // ===== 关联表查询（门店 / 商品 / 等级范围）=====

    /** 模板是否允许在指定门店使用。 */
    @Select("SELECT COUNT(*) FROM mkt_coupon_template_store WHERE template_id = #{templateId} AND store_id = #{storeId}")
    int countStoreScope(@Param("templateId") Long templateId, @Param("storeId") Long storeId);

    /** 订单商品中是否有该模板适用的 SPU。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM mkt_coupon_template_spu
            WHERE template_id = #{templateId} AND spu_id IN
            <foreach collection="spuIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
            </script>
            """)
    int countSpuScope(@Param("templateId") Long templateId, @Param("spuIds") List<Long> spuIds);

    /** 模板限制的等级 ID；空列表表示不限等级。 */
    @Select("SELECT level_id FROM mkt_coupon_template_level WHERE template_id = #{templateId}")
    List<Long> selectLevelScope(@Param("templateId") Long templateId);

    /** 模板适用门店 ID。 */
    @Select("SELECT store_id FROM mkt_coupon_template_store WHERE template_id = #{templateId}")
    List<Long> selectStoreScope(@Param("templateId") Long templateId);

    /** 模板适用商品 ID。 */
    @Select("SELECT spu_id FROM mkt_coupon_template_spu WHERE template_id = #{templateId}")
    List<Long> selectSpuScope(@Param("templateId") Long templateId);
}
