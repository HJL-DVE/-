package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktPointsTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MktPointsTransactionMapper extends BaseMapper<MktPointsTransaction> {

    /** 查某业务单某类型的流水（退款冲正时按原记录冲正）。 */
    @Select("""
            SELECT * FROM mkt_points_transaction
            WHERE brand_id = #{brandId} AND biz_type = #{bizType} AND biz_no = #{bizNo}
            LIMIT 1
            """)
    MktPointsTransaction selectByBiz(@Param("brandId") Long brandId,
                                     @Param("bizType") String bizType,
                                     @Param("bizNo") String bizNo);

    /**
     * 按业务单号反查发放流水（退款事件里常常没有 memberId，需要靠订单号找回会员）。
     */
    @Select("""
            SELECT * FROM mkt_points_transaction
            WHERE brand_id = #{brandId} AND biz_type = #{bizType} AND biz_no = #{bizNo}
            ORDER BY id ASC LIMIT 1
            """)
    MktPointsTransaction selectEarnByBizNo(@Param("brandId") Long brandId,
                                           @Param("bizType") String bizType,
                                           @Param("bizNo") String bizNo);

    @Select("""
            <script>
            SELECT * FROM mkt_points_transaction
            WHERE member_id = #{memberId}
            <if test="bizType != null and bizType != ''"> AND biz_type = #{bizType} </if>
            ORDER BY id DESC LIMIT #{limit}
            </script>
            """)
    List<MktPointsTransaction> selectByMember(@Param("memberId") Long memberId,
                                              @Param("bizType") String bizType,
                                              @Param("limit") int limit);
}
