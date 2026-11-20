package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktPointsAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MktPointsAccountMapper extends BaseMapper<MktPointsAccount> {

    @Select("SELECT * FROM mkt_points_account WHERE brand_id = #{brandId} AND member_id = #{memberId}")
    MktPointsAccount selectByMember(@Param("brandId") Long brandId, @Param("memberId") Long memberId);

    /**
     * 开通积分账户（幂等：靠唯一键 uk_points_member 兜底）。
     */
    @Insert("""
            INSERT INTO mkt_points_account (brand_id, member_id, total_points, total_earned,
                                            total_consumed, total_reversed, version)
            VALUES (#{brandId}, #{memberId}, 0, 0, 0, 0, 0)
            ON DUPLICATE KEY UPDATE update_time = update_time
            """)
    int ensureAccount(@Param("brandId") Long brandId, @Param("memberId") Long memberId);

    /**
     * 加积分（条件 UPDATE，余额不为负由调用方保证）。
     *
     * @return 受影响行数；0 表示账户不存在
     */
    @Update("""
            UPDATE mkt_points_account
            SET total_points = total_points + #{points},
                total_earned = total_earned + #{points},
                version = version + 1, update_time = NOW(3)
            WHERE brand_id = #{brandId} AND member_id = #{memberId}
            """)
    int addPoints(@Param("brandId") Long brandId, @Param("memberId") Long memberId,
                  @Param("points") long points);

    /**
     * 扣积分。
     *
     * <p><b>条件 {@code total_points >= points} 是防超扣的核心</b>：
     * 并发扣减时只有余额足够的那次会成功，不需要先查余额再判断。
     *
     * @return 受影响行数；0 表示余额不足或账户不存在
     */
    @Update("""
            UPDATE mkt_points_account
            SET total_points = total_points - #{points},
                version = version + 1, update_time = NOW(3)
            WHERE brand_id = #{brandId} AND member_id = #{memberId}
              AND total_points >= #{points}
            """)
    int deductPoints(@Param("brandId") Long brandId, @Param("memberId") Long memberId,
                     @Param("points") long points);

    /** 退款冲正：独立累加 total_reversed，便于对账。 */
    @Update("""
            UPDATE mkt_points_account
            SET total_points = total_points - #{points},
                total_reversed = total_reversed + #{points},
                version = version + 1, update_time = NOW(3)
            WHERE brand_id = #{brandId} AND member_id = #{memberId}
              AND total_points >= #{points}
            """)
    int reversePoints(@Param("brandId") Long brandId, @Param("memberId") Long memberId,
                      @Param("points") long points);

    /** 累计消耗（兑换/扣减）单独维护统计字段。 */
    @Update("""
            UPDATE mkt_points_account
            SET total_consumed = total_consumed + #{points}, version = version + 1,
                update_time = NOW(3)
            WHERE brand_id = #{brandId} AND member_id = #{memberId}
            """)
    int addConsumed(@Param("brandId") Long brandId, @Param("memberId") Long memberId,
                    @Param("points") long points);
}
