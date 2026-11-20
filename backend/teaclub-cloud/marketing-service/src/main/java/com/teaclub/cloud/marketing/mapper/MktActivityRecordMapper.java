package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktActivityRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MktActivityRecordMapper extends BaseMapper<MktActivityRecord> {

    @Select("""
            SELECT COUNT(*) FROM mkt_activity_record
            WHERE activity_id = #{activityId} AND member_id = #{memberId} AND biz_no = #{bizNo}
            """)
    int countByBiz(@Param("activityId") Long activityId,
                   @Param("memberId") Long memberId,
                   @Param("bizNo") String bizNo);

    /** 该会员在该活动下的参与次数（每人限次校验）。 */
    @Select("SELECT COUNT(*) FROM mkt_activity_record WHERE activity_id = #{activityId} AND member_id = #{memberId}")
    int countByMember(@Param("activityId") Long activityId, @Param("memberId") Long memberId);
}
