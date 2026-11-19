package com.teaclub.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.wallet.entity.WalRechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
