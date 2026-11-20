package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券实例 Mapper。
 *
 * <p><b>所有状态流转都是条件 UPDATE</b>，返回受影响行数；
 * 0 行表示前态不匹配（已被并发改过或本就不可操作），调用方据此判定失败，
 * 不需要也不允许「先 select 再 update」。
 */
@Mapper
public interface MktCouponMapper extends BaseMapper<MktCoupon> {

    @Select("SELECT * FROM mkt_coupon WHERE coupon_no = #{couponNo}")
    MktCoupon selectByNo(@Param("couponNo") String couponNo);

    @Select("SELECT * FROM mkt_coupon WHERE coupon_no = #{couponNo} FOR UPDATE")
    MktCoupon selectByNoForUpdate(@Param("couponNo") String couponNo);

    /** 统计某会员对某模板已领取张数（限领校验）。 */
    @Select("SELECT COUNT(*) FROM mkt_coupon WHERE template_id = #{templateId} AND member_id = #{memberId}")
    int countByTemplateAndMember(@Param("templateId") Long templateId,
                                 @Param("memberId") Long memberId);

    /**
     * 锁定券：UNUSED -> LOCKED。
     *
     * <p>同时校验有效期（过期券不允许锁定）与归属会员，
     * 保证「不是本人的券」「已过期的券」都不可能被锁上。
     */
    @Update("""
            UPDATE mkt_coupon
            SET status = 'LOCKED', locked_order_no = #{orderNo}, locked_time = NOW(3),
                version = version + 1, update_time = NOW(3)
            WHERE id = #{id}
              AND member_id = #{memberId}
              AND status = 'UNUSED'
              AND valid_start <= NOW(3)
              AND valid_end >= NOW(3)
            """)
    int tryLock(@Param("id") Long id,
                @Param("memberId") Long memberId,
                @Param("orderNo") String orderNo);

    /**
     * 释放券：LOCKED -> UNUSED（仅当锁定订单号匹配）。
     *
     * <p>订单取消时必须走这里，否则券会永久卡在 LOCKED。
     */
    @Update("""
            UPDATE mkt_coupon
            SET status = 'UNUSED', locked_order_no = NULL, locked_time = NULL,
                version = version + 1, update_time = NOW(3)
            WHERE id = #{id}
              AND status = 'LOCKED'
              AND locked_order_no = #{orderNo}
            """)
    int tryRelease(@Param("id") Long id, @Param("orderNo") String orderNo);

    /** 按订单号释放（订单取消场景：调用方只知道订单号）。 */
    @Update("""
            UPDATE mkt_coupon
            SET status = 'UNUSED', locked_order_no = NULL, locked_time = NULL,
                version = version + 1, update_time = NOW(3)
            WHERE locked_order_no = #{orderNo} AND status = 'LOCKED'
            """)
    int releaseByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 核销券：LOCKED -> USED（必须锁定订单号一致，防止别的订单核销掉）。
     *
     * <p>重复核销在第 2 次就会命中 0 行，天然幂等。
     */
    @Update("""
            UPDATE mkt_coupon
            SET status = 'USED', used_order_no = #{orderNo}, used_time = NOW(3),
                discount_amount = #{discountAmount},
                version = version + 1, update_time = NOW(3)
            WHERE id = #{id}
              AND status = 'LOCKED'
              AND locked_order_no = #{orderNo}
            """)
    int tryUse(@Param("id") Long id,
               @Param("orderNo") String orderNo,
               @Param("discountAmount") Long discountAmount);

    /**
     * 过期处理：把已到期且仍可用/锁定的券置为 EXPIRED。
     *
     * @return 本次处理条数
     */
    @Update("""
            UPDATE mkt_coupon
            SET status = 'EXPIRED', version = version + 1, update_time = NOW(3)
            WHERE status IN ('UNUSED')
              AND valid_end < #{now}
            LIMIT #{limit}
            """)
    int expireBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 会员可用券列表（未使用且在有效期内）。 */
    @Select("""
            SELECT * FROM mkt_coupon
            WHERE member_id = #{memberId} AND status = 'UNUSED'
              AND valid_start <= NOW(3) AND valid_end >= NOW(3)
            ORDER BY valid_end ASC
            """)
    List<MktCoupon> selectUsable(@Param("memberId") Long memberId);

    /** 会员全部券（含已用/过期），按状态过滤。 */
    @Select("""
            <script>
            SELECT * FROM mkt_coupon
            WHERE member_id = #{memberId}
            <if test="status != null and status != ''"> AND status = #{status} </if>
            ORDER BY obtain_time DESC
            LIMIT #{limit}
            </script>
            """)
    List<MktCoupon> selectByMember(@Param("memberId") Long memberId,
                                   @Param("status") String status,
                                   @Param("limit") int limit);
}
