package com.teaclub.cloud.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.wallet.entity.WalWalletFreeze;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 钱包冻结记录 Mapper。
 */
@Mapper
public interface WalWalletFreezeMapper extends BaseMapper<WalWalletFreeze> {

    /** 按业务单号查询（幂等判断）。 */
    @Select("SELECT * FROM wal_wallet_freeze WHERE order_no = #{orderNo}")
    WalWalletFreeze selectByOrderNo(@Param("orderNo") String orderNo);

    /** 查询某钱包当前有效冻结总额。 */
    @Select("SELECT COALESCE(SUM(freeze_principal),0) AS principal, "
            + "COALESCE(SUM(freeze_gift),0) AS gift "
            + "FROM wal_wallet_freeze WHERE wallet_id = #{walletId} AND status = 'FROZEN'")
    java.util.Map<String, Object> sumFrozen(@Param("walletId") Long walletId);

    /** 条件更新：仅当仍为 FROZEN 时才释放（防并发冲突）。 */
    @Update("UPDATE wal_wallet_freeze SET status='RELEASED', "
            + "remark=COALESCE(remark,'') + ' [auto-expired]', update_time=NOW(3) "
            + "WHERE id=#{id} AND status='FROZEN' AND expire_time < NOW()")
    int releaseExpired(@Param("id") Long id);

    /** 查询已过期未释放的冻结记录（供定时任务释放）。 */
    @Select("SELECT * FROM wal_wallet_freeze WHERE status = 'FROZEN' "
            + "AND expire_time IS NOT NULL AND expire_time < NOW()")
    List<WalWalletFreeze> findExpired();
}
