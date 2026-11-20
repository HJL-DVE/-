package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktPointsBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MktPointsBatchMapper extends BaseMapper<MktPointsBatch> {

    /** 待消耗批次：按到期时间升序（先进先出）。 */
    @Select("""
            SELECT * FROM mkt_points_batch
            WHERE brand_id = #{brandId} AND member_id = #{memberId}
              AND status = 'ACTIVE' AND remain_points > 0
            ORDER BY expire_time ASC, id ASC
            """)
    List<MktPointsBatch> selectConsumable(@Param("brandId") Long brandId,
                                          @Param("memberId") Long memberId);

    /** 消耗某批次积分；不足则整批清零并置 EXHAUSTED。 */
    @Update("""
            UPDATE mkt_points_batch
            SET remain_points = remain_points - #{points},
                status = IF(remain_points - #{points} <= 0, 'EXHAUSTED', status),
                update_time = NOW(3)
            WHERE id = #{id} AND remain_points >= #{points}
            """)
    int consume(@Param("id") Long id, @Param("points") long points);

    /** 已到期批次。 */
    @Select("""
            SELECT * FROM mkt_points_batch
            WHERE status = 'ACTIVE' AND remain_points > 0 AND expire_time < #{now}
            ORDER BY expire_time ASC LIMIT #{limit}
            """)
    List<MktPointsBatch> selectExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE mkt_points_batch
            SET status = 'EXPIRED', remain_points = 0, update_time = NOW(3)
            WHERE id = #{id}
            """)
    int markExpired(@Param("id") Long id);
}
