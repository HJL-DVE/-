package com.teaclub.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.order.entity.OmsOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 订单 Mapper。
 */
@Mapper
public interface OmsOrderMapper extends BaseMapper<OmsOrder> {

    /**
     * 查询在指定门店有消费的会员 ID 集合。
     *
     * <p><b>用途</b>：member-service 的会员可见口径（B 方案）需要知道
     * "哪些会员在授权门店消费过" —— 会员属于品牌而非门店，
     * 会员在 A 店注册后到 B 店消费时，B 店必须能识别该会员。
     *
     * <p>这是 order-service 对外提供的数据能力，
     * member-service 通过内部接口调用，<b>不直接访问 order_db</b>。
     *
     * @param brandId  品牌 ID
     * @param storeIds 门店 ID 集合（非空）
     * @return 会员 ID 列表
     */
    @Select("""
            <script>
            SELECT DISTINCT o.member_id
            FROM oms_order o
            WHERE o.brand_id = #{brandId}
              AND o.deleted = 0
              AND o.member_id IS NOT NULL
              AND o.store_id IN
              <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
            </script>
            """)
    List<Long> selectMemberIdsByStoreIds(@Param("brandId") Long brandId,
                                        @Param("storeIds") Collection<Long> storeIds);

    /**
     * 统计某会员在指定门店的消费订单数。
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM oms_order o
            WHERE o.member_id = #{memberId}
              AND o.deleted = 0
              AND o.store_id IN
              <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
            </script>
            """)
    int countByMemberAndStores(@Param("memberId") Long memberId,
                               @Param("storeIds") Collection<Long> storeIds);
}
