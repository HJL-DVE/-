package com.teaclub.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.wallet.entity.WalWalletTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WalWalletTransactionMapper extends BaseMapper<WalWalletTransaction> {

    /**
     * 按业务单号查询流水（幂等判断用）。
     */
    @Select("SELECT * FROM wal_wallet_transaction WHERE biz_type = #{bizType} AND biz_no = #{bizNo}")
    WalWalletTransaction selectByBiz(@Param("bizType") String bizType, @Param("bizNo") String bizNo);

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
