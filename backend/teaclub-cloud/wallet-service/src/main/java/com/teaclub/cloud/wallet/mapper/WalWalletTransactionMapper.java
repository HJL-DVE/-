package com.teaclub.cloud.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.wallet.entity.WalWalletTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WalWalletTransactionMapper extends BaseMapper<WalWalletTransaction> {

    /**
     * 某会员的实付金变动累计（资金不变量校验用）。
     */
    @Select("SELECT COALESCE(SUM(principal_change), 0) FROM wal_wallet_transaction WHERE member_id = #{memberId}")
    Long sumPrincipalChange(@Param("memberId") Long memberId);

    /**
     * 某会员的赠送金变动累计（资金不变量校验用）。
     */
    @Select("SELECT COALESCE(SUM(gift_change), 0) FROM wal_wallet_transaction WHERE member_id = #{memberId}")
    Long sumGiftChange(@Param("memberId") Long memberId);

    /**
     * 统计某业务单号的流水条数（幂等校验用）。
     */
    @Select("SELECT COUNT(*) FROM wal_wallet_transaction WHERE biz_type = #{bizType} AND biz_no = #{bizNo}")
    int countByBiz(@Param("bizType") String bizType, @Param("bizNo") String bizNo);

    /**
     * 按业务单号查询流水（幂等判断用）。
     */
    @Select("SELECT * FROM wal_wallet_transaction WHERE biz_type = #{bizType} AND biz_no = #{bizNo}")
    WalWalletTransaction selectByBiz(@Param("bizType") String bizType, @Param("bizNo") String bizNo);

    /**
     * 查询某会员发生过资金流水的门店 ID（用于判断门店是否"服务过"该会员）。
     */
    @Select("""
            <script>
            SELECT DISTINCT store_id FROM wal_wallet_transaction
            WHERE member_id = #{memberId} AND store_id IS NOT NULL AND store_id IN
            <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
            </script>
            """)
    java.util.List<Long> selectServiceStoreIdsByMember(@Param("memberId") Long memberId,
                                                       @Param("storeIds") java.util.Collection<Long> storeIds);

    /**
     * 找出已到期但尚未核销的赠送金充值流水。
     *
     * <p>幂等标记：GIFT_EXPIRE 流水的 biz_no = "GIFT-EXPIRE-" + 充值流水 ID，
     * 与 uk_txn_biz(biz_type, biz_no) 共同保证同一笔赠送金只核销一次。
     */
    @Select("""
            SELECT t.* FROM wal_wallet_transaction t
            WHERE t.biz_type = 'RECHARGE_IN'
              AND t.gift_amount > 0
              AND t.gift_expire_time IS NOT NULL
              AND t.gift_expire_time <= NOW()
              AND NOT EXISTS (
                  SELECT 1 FROM wal_wallet_transaction e
                  WHERE e.biz_type = 'GIFT_EXPIRE'
                    AND e.biz_no = CONCAT('GIFT-EXPIRE-', t.id)
              )
            ORDER BY t.gift_expire_time ASC
            LIMIT #{limit}
            """)
    List<WalWalletTransaction> findExpiredGiftRecharges(@Param("limit") int limit);

    /**
     * 对账：找出余额与流水累计不一致的钱包。
     *
     * <p>不变量：principal_balance = SUM(principal_change) 且 gift_balance = SUM(gift_change)。
     * 正常情况下应返回空列表。
     */
    @Select("""
            SELECT w.id AS walletId,
                   w.member_id AS memberId,
                   w.principal_balance AS principalBalance,
                   COALESCE(SUM(t.principal_change), 0) AS principalSum,
                   w.gift_balance AS giftBalance,
                   COALESCE(SUM(t.gift_change), 0) AS giftSum
            FROM wal_wallet w
            LEFT JOIN wal_wallet_transaction t ON t.wallet_id = w.id
            GROUP BY w.id, w.member_id, w.principal_balance, w.gift_balance
            HAVING w.principal_balance <> principalSum OR w.gift_balance <> giftSum
            """)
    List<java.util.Map<String, Object>> selectInconsistentWallets();
}
