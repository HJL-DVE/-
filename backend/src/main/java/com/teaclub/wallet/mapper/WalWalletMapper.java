package com.teaclub.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.wallet.entity.WalWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WalWalletMapper extends BaseMapper<WalWallet> {

    /**
     * 加行锁读取钱包。并发扣款/入账的唯一正确入口。
     *
     * <p>使用 {@code FOR UPDATE} 而非乐观锁重试：资金写入需要确定性语义，
     * 锁冲突时宁可让后到者等待，也不能让其基于过期余额计算。
     */
    @Select("SELECT * FROM wal_wallet WHERE brand_id = #{brandId} AND member_id = #{memberId} FOR UPDATE")
    WalWallet selectForUpdate(@Param("brandId") Long brandId, @Param("memberId") Long memberId);

    @Select("SELECT * FROM wal_wallet WHERE id = #{id} FOR UPDATE")
    WalWallet selectByIdForUpdate(@Param("id") Long id);
}
