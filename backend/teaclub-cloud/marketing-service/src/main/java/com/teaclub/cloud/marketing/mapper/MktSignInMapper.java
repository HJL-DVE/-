package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktSignIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MktSignInMapper extends BaseMapper<MktSignIn> {

    @Select("SELECT * FROM mkt_sign_in WHERE brand_id = #{brandId} AND member_id = #{memberId} AND sign_date = #{signDate}")
    MktSignIn selectByDate(@Param("brandId") Long brandId,
                           @Param("memberId") Long memberId,
                           @Param("signDate") java.time.LocalDate signDate);

    /** 最近一次签到（判断连续天数）。 */
    @Select("""
            SELECT * FROM mkt_sign_in
            WHERE brand_id = #{brandId} AND member_id = #{memberId}
            ORDER BY sign_date DESC LIMIT 1
            """)
    MktSignIn selectLatest(@Param("brandId") Long brandId, @Param("memberId") Long memberId);

    @Select("""
            SELECT COUNT(*) FROM mkt_sign_in
            WHERE brand_id = #{brandId} AND member_id = #{memberId}
              AND sign_date >= #{from}
            """)
    long countSince(@Param("brandId") Long brandId, @Param("memberId") Long memberId,
                    @Param("from") java.time.LocalDate from);
}
