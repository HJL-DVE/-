package com.teaclub.cloud.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 签到记录。{@code uk_sign_member_date} 保证同一天只能签一次。 */
@Data
@TableName("mkt_sign_in")
public class MktSignIn {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long memberId;
    private LocalDate signDate;
    private Integer continuousDays;
    private Long points;
    private LocalDateTime createTime;
}
