package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MktActivityMapper extends BaseMapper<MktActivity> {

    /**
     * 占用一个活动名额（原子）。
     *
     * <p>{@code total_quota = 0} 表示不限量；否则必须 {@code used_quota < total_quota}。
     * 返回 0 表示名额已满 —— 并发下不会超发。
     */
    @Update("""
            UPDATE mkt_activity
            SET used_quota = used_quota + 1, update_time = NOW(3)
            WHERE id = #{id} AND deleted = 0 AND status = 'ENABLED'
              AND (total_quota = 0 OR used_quota < total_quota)
            """)
    int tryConsumeQuota(@Param("id") Long id);

    /** 该会员在某活动下已参与次数。 */
    @Select("SELECT COUNT(*) FROM mkt_activity_record WHERE activity_id = #{activityId} AND member_id = #{memberId}")
    int countByMember(@Param("activityId") Long activityId, @Param("memberId") Long memberId);

    /** 命中的进行中活动（按优先级降序）。 */
    @Select("""
            <script>
            SELECT * FROM mkt_activity
            WHERE brand_id = #{brandId} AND deleted = 0 AND status = 'ENABLED'
              AND activity_type = #{activityType}
              AND start_time &lt;= #{now} AND end_time &gt;= #{now}
            ORDER BY priority DESC, id ASC
            </script>
            """)
    List<MktActivity> selectActiveByType(@Param("brandId") Long brandId,
                                         @Param("activityType") String activityType,
                                         @Param("now") LocalDateTime now);

    /** 全部进行中活动（不限类型）。 */
    @Select("""
            SELECT * FROM mkt_activity
            WHERE brand_id = #{brandId} AND deleted = 0 AND status = 'ENABLED'
              AND start_time <= #{now} AND end_time >= #{now}
            ORDER BY priority DESC, id ASC
            """)
    List<MktActivity> selectAllActive(@Param("brandId") Long brandId, @Param("now") LocalDateTime now);

    /**
     * 把已过期的活动置为 EXPIRED。
     *
     * <p>注意：本语句<b>没有</b> {@code <script>} 包裹，SQL 按字面量下发，
     * 因此这里必须用原生 {@code <}，写成 XML 转义 {@code &lt;} 会导致
     * 数据库收到字面量 "&lt;" 而报语法错误。
     */
    @Update("UPDATE mkt_activity SET status = 'EXPIRED', update_time = NOW(3) "
            + "WHERE status = 'ENABLED' AND end_time < #{now} AND deleted = 0 "
            + "LIMIT #{limit}")
    int expireBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
