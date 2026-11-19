package com.teaclub.cloud.member.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员微信绑定关系（openid ↔ member）。
 *
 * <p>同一品牌下 openid 唯一；一名会员可绑定多个 openid（多端小程序）。
 */
@Data
@TableName("mem_member_wx")
public class MemMemberWx {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private String openid;
    private Long memberId;
    private String unionid;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
