package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分商城商品。 */
@Data
@TableName("mkt_points_goods")
public class MktPointsGoods {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String goodsNo;
    private String goodsName;
    /** COUPON / PHYSICAL / POINTS_PACK */
    private String goodsType;
    private String imageUrl;
    private Long pointsPrice;
    /** -1 = 不限库存 */
    private Integer stock;
    /** goodsType=COUPON 时对应的券模板 */
    private Long templateId;
    private Integer exchangeLimit;
    private Integer sort;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
