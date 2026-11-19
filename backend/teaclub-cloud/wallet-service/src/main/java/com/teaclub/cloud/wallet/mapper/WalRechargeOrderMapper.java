package com.teaclub.cloud.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.wallet.entity.WalRechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;

@Mapper
public interface WalRechargeOrderMapper extends BaseMapper<WalRechargeOrder> {

    @Select("SELECT * FROM wal_recharge_order WHERE recharge_no = #{rechargeNo} AND deleted = 0 FOR UPDATE")
    WalRechargeOrder selectByNoForUpdate(@Param("rechargeNo") String rechargeNo);

    @Select("SELECT * FROM wal_recharge_order WHERE recharge_no = #{rechargeNo} AND deleted = 0")
    WalRechargeOrder selectByNo(@Param("rechargeNo") String rechargeNo);

    /**
     * 统计会员在某方案下的已支付充值次数（用于每人次数限制）。
     */
    @Select("SELECT COUNT(*) FROM wal_recharge_order WHERE member_id = #{memberId} AND plan_id = #{planId} AND status = 'PAID' AND deleted = 0")
    long countPaidByPlan(@Param("memberId") Long memberId, @Param("planId") Long planId);

    // ===== 仪表盘统计：累计充值/赠送（仅统计已入账的 PAID 充值单） =====

    /** 累计充值实付金额合计（分）。 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(recharge_amount), 0) FROM wal_recharge_order
            WHERE brand_id = #{brandId} AND deleted = 0 AND status = 'PAID'
            <if test="memberIds != null">
              AND member_id IN
              <foreach collection="memberIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            </script>
            """)
    Long sumPaidRechargeAmount(@Param("brandId") Long brandId,
                               @Param("memberIds") Collection<Long> memberIds);

    /** 累计赠送金额合计（分）。 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(gift_amount), 0) FROM wal_recharge_order
            WHERE brand_id = #{brandId} AND deleted = 0 AND status = 'PAID'
            <if test="memberIds != null">
              AND member_id IN
              <foreach collection="memberIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            </script>
            """)
    Long sumPaidGiftAmount(@Param("brandId") Long brandId,
                           @Param("memberIds") Collection<Long> memberIds);
}
