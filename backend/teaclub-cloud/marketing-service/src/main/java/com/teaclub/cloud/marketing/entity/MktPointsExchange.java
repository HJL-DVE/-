package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分兑换记录。 */
@Data
@TableName("mkt_points_exchange")
public class MktPointsExchange {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String exchangeNo;
    private Long memberId;
    private Long goodsId;
    private String goodsName;
    private Long pointsCost;
    /** 兑换券类商品后生成的券实例 */
    private Long couponId;
    private String status;
    private LocalDateTime createTime;
}
