package com.teaclub.cloud.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.marketing.entity.MktActivityStore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MktActivityStoreMapper extends BaseMapper<MktActivityStore> {

    @Select("SELECT store_id FROM mkt_activity_store WHERE activity_id = #{activityId}")
    List<Long> selectStoreIds(@Param("activityId") Long activityId);

    @Select("SELECT COUNT(*) FROM mkt_activity_store WHERE activity_id = #{activityId} AND store_id = #{storeId}")
    int countStore(@Param("activityId") Long activityId, @Param("storeId") Long storeId);
}
