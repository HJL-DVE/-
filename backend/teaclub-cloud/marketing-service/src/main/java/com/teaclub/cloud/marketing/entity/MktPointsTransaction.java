package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水。
 *
 * <p>{@code uk_points_biz(brand_id, biz_type, biz_no)} 是
 * 「同一订单不重复发积分」的最终防线：重复消息走到 INSERT 就会抛唯一键冲突。
 */
@Data
@TableName("mkt_points_transaction")
public class MktPointsTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long accountId;
    private Long memberId;
    private String txnNo;
    /** ORDER_EARN / ORDER_REFUND_REVERSE / SIGN_IN / ACTIVITY / EXCHANGE / DEDUCT / EXPIRE / ADJUST */
    private String bizType;
    private String bizNo;
    private Long changePoints;
    private Long beforePoints;
    private Long afterPoints;
    private Long storeId;
    private String remark;
    private String traceId;
    private LocalDateTime createTime;
}
