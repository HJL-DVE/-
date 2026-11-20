package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员积分账户（marketing_db 是积分的唯一事实来源）。
 *
 * <p>member_db 的 {@code mem_member.points} 只是快照，由本服务回写，
 * 不做任何业务判断依据。
 */
@Data
@TableName("mkt_points_account")
public class MktPointsAccount {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long memberId;
    private Long totalPoints;
    private Long totalEarned;
    private Long totalConsumed;
    private Long totalReversed;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
