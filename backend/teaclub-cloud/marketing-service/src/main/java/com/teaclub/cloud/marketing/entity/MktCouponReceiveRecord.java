package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 优惠券领取记录（限领校验与审计依据）。 */
@Data
@TableName("mkt_coupon_receive_record")
public class MktCouponReceiveRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long templateId;
    private Long memberId;
    private Long couponId;
    private String couponNo;
    private Integer qty;
    private LocalDateTime createTime;
}
