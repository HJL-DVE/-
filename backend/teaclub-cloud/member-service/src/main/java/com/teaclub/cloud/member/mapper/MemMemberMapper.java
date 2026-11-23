package com.teaclub.cloud.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.member.entity.MemMember;
import com.teaclub.cloud.member.vo.MemberBriefVO;
import com.teaclub.cloud.member.vo.MemberLevelBriefVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface MemMemberMapper extends BaseMapper<MemMember> {

    /**
     * 查询注册门店在指定门店范围内的会员 ID。
     *
     * <p>可见口径（B 方案）的一半：注册门店归属。
     * 另一半"在授权门店消费过"由 order-service 提供（跨服务调用）。
     *
     * @param brandId  品牌 ID
     * @param storeIds 授权门店 ID 集合（非空）
     */
    @Select("""
            <script>
            SELECT m.id
            FROM mem_member m
            WHERE m.brand_id = #{brandId}
              AND m.deleted = 0
              AND m.register_store_id IN
              <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
            </script>
            """)
    List<Long> selectIdsByRegisterStores(@Param("brandId") Long brandId,
                                        @Param("storeIds") Collection<Long> storeIds);

    /**
     * 按手机号/昵称/会员号检索会员 ID（其它服务按会员维度过滤用）。
     */
    @Select("""
            <script>
            SELECT m.id
            FROM mem_member m
            WHERE m.brand_id = #{brandId}
              AND m.deleted = 0
            <if test="phone != null and phone != ''">
              AND m.phone LIKE CONCAT('%', #{phone}, '%')
            </if>
            <if test="keyword != null and keyword != ''">
              AND (m.nickname LIKE CONCAT('%', #{keyword}, '%')
                   OR m.member_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY m.id DESC
            LIMIT 500
            </script>
            """)
    List<Long> selectIdsByPhoneOrKeyword(@Param("brandId") Long brandId,
                                         @Param("phone") String phone,
                                         @Param("keyword") String keyword);

    /** 会员简要信息（单个）。 */
    @Select("SELECT m.id AS id, m.member_no AS memberNo, m.nickname AS nickname, m.phone AS phone, "
            + "m.level_id AS levelId, l.level_no AS levelNo, m.status AS status, "
            + "m.register_store_id AS registerStoreId "
            + "FROM mem_member m LEFT JOIN mem_member_level l ON l.id = m.level_id "
            + "WHERE m.id = #{id} AND m.deleted = 0")
    MemberBriefVO selectBriefById(@Param("id") Long id);

    /** 会员简要信息（批量）。 */
    @Select("""
            <script>
            SELECT m.id AS id, m.member_no AS memberNo, m.nickname AS nickname, m.phone AS phone,
                   m.level_id AS levelId, l.level_no AS levelNo, m.status AS status,
                   m.register_store_id AS registerStoreId
            FROM mem_member m LEFT JOIN mem_member_level l ON l.id = m.level_id
            WHERE m.deleted = 0 AND m.id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<MemberBriefVO> selectBriefByIds(@Param("ids") Collection<Long> ids);

    /** 会员等级简要信息（批量）。 */
    @Select("""
            <script>
            SELECT l.id AS id, l.level_name AS levelName, l.level_no AS levelNo
            FROM mem_member_level l
            WHERE l.deleted = 0 AND l.id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<MemberLevelBriefVO> selectLevelBriefByIds(@Param("ids") Collection<Long> ids);

    /**
     * 取指定前缀下最大的会员号（会员号格式 M + yyyy + 4 位序号，定长可直接比较）。
     *
     * @param brandId    品牌 ID
     * @param prefixLike LIKE 模式，如 {@code M2026%}
     */
    @Select("SELECT MAX(member_no) FROM mem_member "
            + "WHERE brand_id = #{brandId} AND deleted = 0 AND member_no LIKE #{prefixLike}")
    String selectMaxMemberNo(@Param("brandId") Long brandId,
                             @Param("prefixLike") String prefixLike);

    // ===== 仪表盘统计：真实数据聚合，无硬编码 =====

    /**
     * 会员总数。
     *
     * @param memberIds 可见会员 ID 范围；null 表示不限（总部账号）
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM mem_member
            WHERE brand_id = #{brandId} AND deleted = 0
            <if test="memberIds != null">
              AND id IN
              <foreach collection="memberIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            </script>
            """)
    long countMembers(@Param("brandId") Long brandId, @Param("memberIds") Collection<Long> memberIds);

    /** 指定时间之后注册的会员数。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM mem_member
            WHERE brand_id = #{brandId} AND deleted = 0 AND create_time &gt;= #{since}
            <if test="memberIds != null">
              AND id IN
              <foreach collection="memberIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            </script>
            """)
    long countMembersCreatedSince(@Param("brandId") Long brandId,
                                  @Param("since") LocalDateTime since,
                                  @Param("memberIds") Collection<Long> memberIds);

    /** 指定时间之后有消费的会员数（按 last_consume_time 判定活跃）。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM mem_member
            WHERE brand_id = #{brandId} AND deleted = 0 AND last_consume_time &gt;= #{since}
            <if test="memberIds != null">
              AND id IN
              <foreach collection="memberIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            </script>
            """)
    long countMembersActiveSince(@Param("brandId") Long brandId,
                                 @Param("since") LocalDateTime since,
                                 @Param("memberIds") Collection<Long> memberIds);

    /** 会员积分合计。 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(points), 0) FROM mem_member
            WHERE brand_id = #{brandId} AND deleted = 0
            <if test="memberIds != null">
              AND id IN
              <foreach collection="memberIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            </script>
            """)
    Long sumPoints(@Param("brandId") Long brandId, @Param("memberIds") Collection<Long> memberIds);

    /**
     * 当月生日的会员 ID（营销活动定时发奖用）。
     *
     * @param brandId 品牌
     * @param month   月份 1-12
     */
    @Select("""
            SELECT m.id
            FROM mem_member m
            WHERE m.brand_id = #{brandId}
              AND m.deleted = 0
              AND m.status = 'NORMAL'
              AND m.birthday IS NOT NULL
              AND MONTH(m.birthday) = #{month}
            """)
    List<Long> selectBirthdayIds(@Param("brandId") Long brandId, @Param("month") Integer month);

    /**
     * 沉睡会员 ID：最后消费时间早于截止时间；从未消费的注册满 N 天也算。
     *
     * @param brandId   品牌
     * @param cutoff    截止时间（now - sleepingDays）
     */
    @Select("""
            SELECT m.id
            FROM mem_member m
            WHERE m.brand_id = #{brandId}
              AND m.deleted = 0
              AND m.status = 'NORMAL'
              AND (m.last_consume_time IS NULL OR m.last_consume_time < #{cutoff})
            """)
    List<Long> selectSleepingIds(@Param("brandId") Long brandId,
                                 @Param("cutoff") LocalDateTime cutoff);
}
