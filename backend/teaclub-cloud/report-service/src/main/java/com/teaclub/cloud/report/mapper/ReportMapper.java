package com.teaclub.cloud.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报表聚合 Mapper —— 跨库<b>只读</b> SQL。
 *
 * <p><b>为什么不全部走 Feign</b>：
 * 各业务服务目前只有零散的 summary 接口，无趋势/排行/占比聚合；
 * 为不改动 7 个稳定服务，报表库账号 tc_report 授予全部业务库的
 * <b>SELECT-only</b> 权限（与阶段三 tc_migrator 同模式）。
 * 本 Mapper 只写 SELECT，杜绝跨库写。
 *
 * <p>所有查询带 brandId + 门店过滤（storeId 为 null 表示全部授权门店）。
 */
@Mapper
public interface ReportMapper {

    // ==================== 门店 ====================

    @Select("""
            SELECT COUNT(*) FROM iam_db.sys_store
            WHERE deleted = 0 AND brand_id = #{brandId}
              AND (#{storeId} IS NULL OR id = #{storeId})
            """)
    long countStores(@Param("brandId") Long brandId, @Param("storeId") Long storeId);

    @Select("SELECT id, store_code AS storeCode, store_name AS storeName "
            + "FROM iam_db.sys_store WHERE deleted = 0 AND brand_id = #{brandId} "
            + "AND (#{storeId} IS NULL OR id = #{storeId}) "
            + "ORDER BY id")
    List<Map<String, Object>> listStores(@Param("brandId") Long brandId,
                                         @Param("storeId") Long storeId);

    // ==================== 会员 ====================

    /** register_store_id = 注册来源门店；storeId 为 null 表示品牌全部门店。 */
    @Select("""
            SELECT COUNT(*) FROM member_db.mem_member
            WHERE deleted = 0 AND brand_id = #{brandId}
              AND (#{storeId} IS NULL OR register_store_id = #{storeId})
              AND (#{since} IS NULL OR create_time >= #{since})
            """)
    long countMembers(@Param("brandId") Long brandId,
                      @Param("since") LocalDateTime since,
                      @Param("storeId") Long storeId);

    @Select("""
            SELECT DATE(create_time) AS d, COUNT(*) AS cnt
            FROM member_db.mem_member
            WHERE deleted = 0 AND brand_id = #{brandId}
              AND (#{storeId} IS NULL OR register_store_id = #{storeId})
              AND create_time >= #{since}
            GROUP BY DATE(create_time) ORDER BY d
            """)
    List<Map<String, Object>> memberGrowth(@Param("brandId") Long brandId,
                                           @Param("since") LocalDateTime since,
                                           @Param("storeId") Long storeId);

    @Select("""
            SELECT COALESCE(l.level_name,'未知等级') AS name, COUNT(m.id) AS value
            FROM member_db.mem_member m
            LEFT JOIN member_db.mem_member_level l ON l.id = m.level_id
            WHERE m.deleted = 0 AND m.brand_id = #{brandId}
              AND (#{storeId} IS NULL OR m.register_store_id = #{storeId})
            GROUP BY l.id, l.level_name ORDER BY value DESC
            """)
    List<Map<String, Object>> memberLevelDistribution(@Param("brandId") Long brandId,
                                                      @Param("storeId") Long storeId);

    @Select("""
            SELECT id, member_no AS memberNo, nickname,
                   total_consume_amount AS totalConsume, total_order_count AS orderCount
            FROM member_db.mem_member
            WHERE deleted = 0 AND brand_id = #{brandId} AND total_consume_amount > 0
              AND (#{storeId} IS NULL OR register_store_id = #{storeId})
            ORDER BY total_consume_amount DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> memberConsumeRank(@Param("brandId") Long brandId,
                                                @Param("storeId") Long storeId,
                                                @Param("limit") int limit);

    // ==================== 订单（销售） ====================

    @Select("""
            SELECT COALESCE(SUM(paid_amount - refunded_amount),0) AS sales,
                   COUNT(*) AS orders
            FROM order_db.oms_order
            WHERE brand_id = #{brandId} AND deleted = 0
              AND status IN ('PAID','COMPLETED','REFUNDING')
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND (#{since} IS NULL OR pay_time >= #{since})
              AND (#{until} IS NULL OR pay_time < #{until})
            """)
    Map<String, Object> salesSummary(@Param("brandId") Long brandId,
                                     @Param("storeId") Long storeId,
                                     @Param("since") LocalDateTime since,
                                     @Param("until") LocalDateTime until);

    @Select("""
            SELECT DATE(pay_time) AS d,
                   COALESCE(SUM(paid_amount - refunded_amount),0) AS sales,
                   COUNT(*) AS orders
            FROM order_db.oms_order
            WHERE brand_id = #{brandId} AND deleted = 0
              AND status IN ('PAID','COMPLETED','REFUNDING')
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND pay_time >= #{since}
              AND (#{until} IS NULL OR pay_time < #{until})
            GROUP BY DATE(pay_time) ORDER BY d
            """)
    List<Map<String, Object>> salesTrend(@Param("brandId") Long brandId,
                                         @Param("storeId") Long storeId,
                                         @Param("since") LocalDateTime since,
                                         @Param("until") LocalDateTime until);

    @Select("""
            SELECT store_id AS storeId,
                   COALESCE(SUM(paid_amount - refunded_amount),0) AS sales,
                   COUNT(*) AS orders
            FROM order_db.oms_order
            WHERE brand_id = #{brandId} AND deleted = 0
              AND status IN ('PAID','COMPLETED','REFUNDING')
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND (#{since} IS NULL OR pay_time >= #{since})
              AND (#{until} IS NULL OR pay_time < #{until})
            GROUP BY store_id ORDER BY sales DESC
            """)
    List<Map<String, Object>> salesByStore(@Param("brandId") Long brandId,
                                           @Param("storeId") Long storeId,
                                           @Param("since") LocalDateTime since,
                                           @Param("until") LocalDateTime until);


    /** 今日每小时订单趋势（0-23 点；无单的小时由服务层补零）。 */
    @Select("""
            SELECT HOUR(pay_time) AS h, COUNT(*) AS orders,
                   COALESCE(SUM(paid_amount - refunded_amount),0) AS sales
            FROM order_db.oms_order
            WHERE brand_id = #{brandId} AND deleted = 0
              AND status IN ('PAID','COMPLETED','REFUNDING')
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND pay_time >= #{since}
              AND pay_time < #{until}
            GROUP BY HOUR(pay_time) ORDER BY h
            """)
    List<Map<String, Object>> hourlyOrders(@Param("brandId") Long brandId,
                                           @Param("storeId") Long storeId,
                                           @Param("since") LocalDateTime since,
                                           @Param("until") LocalDateTime until);

    /** 商品分类销售排行（经 product_db.prd_spu 关联分类；metric 白名单由服务层传入）。 */
    @Select("""
            SELECT COALESCE(c.category_name,'未分类') AS name,
                   SUM(i.quantity) AS qty,
                   COALESCE(SUM(i.actual_amount),0) AS amount
            FROM order_db.oms_order_item i
            JOIN order_db.oms_order o ON o.id = i.order_id
            JOIN product_db.prd_spu s ON s.id = i.spu_id
            LEFT JOIN product_db.prd_category c ON c.id = s.category_id
            WHERE i.brand_id = #{brandId} AND o.deleted = 0
              AND o.status IN ('PAID','COMPLETED','REFUNDING')
              AND (#{storeId} IS NULL OR i.store_id = #{storeId})
              AND (#{since} IS NULL OR o.pay_time >= #{since})
              AND (#{until} IS NULL OR o.pay_time < #{until})
            GROUP BY c.id, c.category_name
            ORDER BY ${metric} DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> categoryRank(@Param("brandId") Long brandId,
                                           @Param("storeId") Long storeId,
                                           @Param("since") LocalDateTime since,
                                           @Param("until") LocalDateTime until,
                                           @Param("metric") String metric,
                                           @Param("limit") int limit);

    /** 商品销售趋势（按日：销量与销售额）。 */
    @Select("""
            SELECT DATE(o.pay_time) AS d, SUM(i.quantity) AS qty,
                   COALESCE(SUM(i.actual_amount),0) AS amount
            FROM order_db.oms_order_item i
            JOIN order_db.oms_order o ON o.id = i.order_id
            WHERE i.brand_id = #{brandId} AND o.deleted = 0
              AND o.status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
              AND (#{storeId} IS NULL OR i.store_id = #{storeId})
              AND o.pay_time >= #{since}
            GROUP BY DATE(o.pay_time) ORDER BY d
            """)
    List<Map<String, Object>> productTrend(@Param("brandId") Long brandId,
                                           @Param("storeId") Long storeId,
                                           @Param("since") LocalDateTime since);
    /**
     * 商品排行（商品级）：按 spu_id 聚合，每个商品一行。
     *
     * <p>不能按 sku_id —— 同一商品的大杯/中杯是不同 sku，会产出多条同名行；
     * 也不能按 sku_name 快照 —— 历史数据存在"完整名/纯规格名"混用，会出现孤立的"大杯/中杯"。
     * 按 spu_id 聚合后展示名取 MAX(spu_name) 即商品名，保证"一种商品一行"。
     */
    @Select("""
            SELECT i.spu_id AS spuId, MAX(i.spu_name) AS name,
                   SUM(i.quantity) AS qty,
                   COALESCE(SUM(i.actual_amount),0) AS amount
            FROM order_db.oms_order_item i
            JOIN order_db.oms_order o ON o.id = i.order_id
            WHERE i.brand_id = #{brandId} AND o.deleted = 0
              AND o.status IN ('PAID','COMPLETED','REFUNDING')
              AND (#{storeId} IS NULL OR i.store_id = #{storeId})
              AND (#{since} IS NULL OR o.pay_time >= #{since})
              AND (#{until} IS NULL OR o.pay_time < #{until})
            GROUP BY i.spu_id
            ORDER BY ${metric} DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> productRank(@Param("brandId") Long brandId,
                                          @Param("storeId") Long storeId,
                                          @Param("since") LocalDateTime since,
                                          @Param("until") LocalDateTime until,
                                          @Param("metric") String metric,
                                          @Param("limit") int limit);

    // ==================== 储值 ====================

    @Select("""
            SELECT COUNT(*) AS cnt,
                   COALESCE(SUM(recharge_amount),0) AS principal,
                   COALESCE(SUM(gift_amount),0) AS gift
            FROM wallet_db.wal_recharge_order
            WHERE brand_id = #{brandId} AND deleted = 0 AND status = 'PAID'
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND (#{since} IS NULL OR pay_time >= #{since})
              AND (#{until} IS NULL OR pay_time < #{until})
            """)
    Map<String, Object> rechargeSummary(@Param("brandId") Long brandId,
                                        @Param("storeId") Long storeId,
                                        @Param("since") LocalDateTime since,
                                        @Param("until") LocalDateTime until);

    @Select("""
            SELECT DATE(pay_time) AS d,
                   COALESCE(SUM(recharge_amount),0) AS principal,
                   COALESCE(SUM(gift_amount),0) AS gift
            FROM wallet_db.wal_recharge_order
            WHERE brand_id = #{brandId} AND deleted = 0 AND status = 'PAID'
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND pay_time >= #{since}
            GROUP BY DATE(pay_time) ORDER BY d
            """)
    List<Map<String, Object>> rechargeTrend(@Param("brandId") Long brandId,
                                            @Param("storeId") Long storeId,
                                            @Param("since") LocalDateTime since);

    /** 钱包消费金额：余额本金 + 赠送抵扣（不走 pay_payment_order，不重复计收）。 */
    @Select("""
            SELECT COALESCE(SUM(balance_principal_amount + balance_gift_amount),0) AS amount
            FROM order_db.oms_order
            WHERE brand_id = #{brandId} AND deleted = 0
              AND status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND (#{since} IS NULL OR pay_time >= #{since})
              AND (#{until} IS NULL OR pay_time < #{until})
            """)
    Map<String, Object> walletConsumeSummary(@Param("brandId") Long brandId,
                                             @Param("storeId") Long storeId,
                                             @Param("since") LocalDateTime since,
                                             @Param("until") LocalDateTime until);

    // ==================== 支付 / 退款 ====================

    @Select("""
            SELECT channel, COUNT(*) AS cnt, COALESCE(SUM(amount),0) AS amount
            FROM payment_db.pay_payment_order
            WHERE brand_id = #{brandId} AND status = 'SUCCESS'
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND (#{since} IS NULL OR success_time >= #{since})
              AND (#{until} IS NULL OR success_time < #{until})
            GROUP BY channel ORDER BY amount DESC
            """)
    List<Map<String, Object>> payByChannel(@Param("brandId") Long brandId,
                                           @Param("storeId") Long storeId,
                                           @Param("since") LocalDateTime since,
                                           @Param("until") LocalDateTime until);

    @Select("""
            SELECT COUNT(*) AS cnt, COALESCE(SUM(amount),0) AS amount
            FROM payment_db.pay_payment_order
            WHERE brand_id = #{brandId} AND status = 'SUCCESS'
              AND biz_type = 'RECHARGE'
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND (#{since} IS NULL OR success_time >= #{since})
              AND (#{until} IS NULL OR success_time < #{until})
            """)
    Map<String, Object> rechargePaySummary(@Param("brandId") Long brandId,
                                           @Param("storeId") Long storeId,
                                           @Param("since") LocalDateTime since,
                                           @Param("until") LocalDateTime until);

    @Select("""
            SELECT COUNT(*) AS cnt, COALESCE(SUM(refund_amount),0) AS amount
            FROM payment_db.pay_refund_order
            WHERE brand_id = #{brandId} AND status = 'SUCCESS'
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND (#{since} IS NULL OR success_time >= #{since})
              AND (#{until} IS NULL OR success_time < #{until})
            """)
    Map<String, Object> refundSummary(@Param("brandId") Long brandId,
                                      @Param("storeId") Long storeId,
                                      @Param("since") LocalDateTime since,
                                      @Param("until") LocalDateTime until);

    @Select("""
            SELECT DATE(success_time) AS d, COALESCE(SUM(refund_amount),0) AS amount
            FROM payment_db.pay_refund_order
            WHERE brand_id = #{brandId} AND status = 'SUCCESS'
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND success_time >= #{since}
            GROUP BY DATE(success_time) ORDER BY d
            """)
    List<Map<String, Object>> refundTrend(@Param("brandId") Long brandId,
                                          @Param("storeId") Long storeId,
                                          @Param("since") LocalDateTime since);

    // ==================== 营销 ====================

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM marketing_db.mkt_coupon
                WHERE brand_id = #{brandId}
                  AND (#{since} IS NULL OR obtain_time >= #{since})) AS received,
              (SELECT COUNT(*) FROM marketing_db.mkt_coupon
                WHERE brand_id = #{brandId} AND status = 'USED'
                  AND (#{since} IS NULL OR used_time >= #{since})) AS used
            """)
    Map<String, Object> couponStats(@Param("brandId") Long brandId,
                                    @Param("since") LocalDateTime since,
                                    @Param("until") LocalDateTime until);

    @Select("""
            SELECT
              (SELECT COALESCE(SUM(CASE WHEN change_points > 0 THEN change_points ELSE 0 END),0)
                 FROM marketing_db.mkt_points_transaction
                 WHERE brand_id = #{brandId}
                   AND (#{storeId} IS NULL OR store_id = #{storeId})
                   AND (#{since} IS NULL OR create_time >= #{since})) AS earned,
              (SELECT COALESCE(SUM(-change_points),0)
                 FROM marketing_db.mkt_points_transaction
                 WHERE brand_id = #{brandId} AND biz_type = 'DEDUCT'
                   AND (#{storeId} IS NULL OR store_id = #{storeId})
                   AND (#{since} IS NULL OR create_time >= #{since})) AS consumed
            """)
    Map<String, Object> pointsStats(@Param("brandId") Long brandId,
                                    @Param("since") LocalDateTime since,
                                    @Param("storeId") Long storeId);
}