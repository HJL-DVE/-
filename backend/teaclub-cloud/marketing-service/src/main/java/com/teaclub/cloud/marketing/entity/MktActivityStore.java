package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 活动适用门店（scopeType=STORE 时生效）。 */
@Data
@TableName("mkt_activity_store")
public class MktActivityStore {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long activityId;
    private Long storeId;
}
