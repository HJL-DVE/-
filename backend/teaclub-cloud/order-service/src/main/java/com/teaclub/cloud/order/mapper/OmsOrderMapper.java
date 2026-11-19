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

    // ===== 仪表盘统计（只读聚合，遵守门店数据权限）=====

    /**
     * 区间内订单数。
     *
     * @param storeId null 表示不限门店（总部）
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM oms_order
            WHERE brand_id = #{brandId} AND deleted = 0 AND create_time &gt;= #{since}
            <if test="storeId != null"> AND store_id = #{storeId} </if>
            </script>
            """)
    long countSince(@Param("brandId") Long brandId, @Param("storeId") Long storeId,
                    @Param("since") java.time.LocalDateTime since);

    /** 区间内已支付（含后续退款/完成态）订单数。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM oms_order
            WHERE brand_id = #{brandId} AND deleted = 0 AND create_time &gt;= #{since}
              AND status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
            <if test="storeId != null"> AND store_id = #{storeId} </if>
            </script>
            """)
    long countPaidSince(@Param("brandId") Long brandId, @Param("storeId") Long storeId,
                        @Param("since") java.time.LocalDateTime since);

    /** 区间内成交金额（实付应付额之和）。 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(payable_amount), 0) FROM oms_order
            WHERE brand_id = #{brandId} AND deleted = 0 AND create_time &gt;= #{since}
              AND status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
            <if test="storeId != null"> AND store_id = #{storeId} </if>
            </script>
            """)
    Long sumGmvSince(@Param("brandId") Long brandId, @Param("storeId") Long storeId,
                     @Param("since") java.time.LocalDateTime since);

    /** 区间内某支付渠道的成交金额。 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(payable_amount), 0) FROM oms_order
            WHERE brand_id = #{brandId} AND deleted = 0 AND create_time &gt;= #{since}
              AND status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
              AND pay_channel = #{payChannel}
            <if test="storeId != null"> AND store_id = #{storeId} </if>
            </script>
            """)
    Long sumGmvByChannelSince(@Param("brandId") Long brandId, @Param("storeId") Long storeId,
                              @Param("since") java.time.LocalDateTime since,
                              @Param("payChannel") String payChannel);

    /** 按日趋势（订单数 + 成交额）。 */
    @Select("""
            <script>
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate,
                   COUNT(*) AS orderCount,
                   COALESCE(SUM(CASE WHEN status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
                                     THEN payable_amount ELSE 0 END), 0) AS gmv
            FROM oms_order
            WHERE brand_id = #{brandId} AND deleted = 0 AND create_time &gt;= #{since}
            <if test="storeId != null"> AND store_id = #{storeId} </if>
            GROUP BY statDate ORDER BY statDate
            </script>
            """)
    List<java.util.Map<String, Object>> dailyTrend(@Param("brandId") Long brandId,
                                                   @Param("storeId") Long storeId,
                                                   @Param("since") java.time.LocalDateTime since);

    /**
     * 按订单号查询（不加锁）。
     */
    @Select("SELECT * FROM oms_order WHERE order_no = #{orderNo} AND deleted = 0")
    OmsOrder selectByNo(@Param("orderNo") String orderNo);

    /**
     * 按订单号加行锁读取（并发支付/取消时保证一致性）。
     */
    @Select("SELECT * FROM oms_order WHERE order_no = #{orderNo} AND deleted = 0 FOR UPDATE")
    OmsOrder selectByNoForUpdate(@Param("orderNo") String orderNo);

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

    /**
     * 查询最近 N 天内使用余额支付的订单（用于订单-钱包三方对账）。
     */
    @Select("SELECT * FROM oms_order WHERE deleted = 0 "
            + "AND pay_channel = 'BALANCE' "
            + "AND status IN ('PAID','COMPLETED','REFUNDING','REFUNDED') "
            + "AND pay_time > DATE_SUB(NOW(3), INTERVAL #{days} DAY) "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<OmsOrder> selectBalancePaidSince(
            @Param("days") int days, @Param("limit") int limit);
}
