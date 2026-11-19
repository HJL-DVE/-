package com.teaclub.cloud.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.wallet.entity.WalWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;

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

    // ===== 仪表盘统计：真实数据聚合，无硬编码 =====

    /**
     * 钱包账户数。
     *
     * @param memberIds 会员 ID 范围；null 表示不限（总部账号）
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM wal_wallet
            WHERE brand_id = #{brandId}
            <if test="memberIds != null">
              AND member_id IN
              <foreach collection="memberIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            </script>
            """)
    long countWallets(@Param("brandId") Long brandId,
                      @Param("memberIds") Collection<Long> memberIds);

    /** 实付余额合计（分）。 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(principal_balance), 0) FROM wal_wallet
            WHERE brand_id = #{brandId}
            <if test="memberIds != null">
              AND member_id IN
              <foreach collection="memberIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            </script>
            """)
    Long sumPrincipalBalance(@Param("brandId") Long brandId,
                             @Param("memberIds") Collection<Long> memberIds);

    /** 赠送余额合计（分）。 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(gift_balance), 0) FROM wal_wallet
            WHERE brand_id = #{brandId}
            <if test="memberIds != null">
              AND member_id IN
              <foreach collection="memberIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            </script>
            """)
    Long sumGiftBalance(@Param("brandId") Long brandId,
                        @Param("memberIds") Collection<Long> memberIds);
}
