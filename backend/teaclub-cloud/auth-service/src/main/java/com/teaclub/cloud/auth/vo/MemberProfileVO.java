package com.teaclub.cloud.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 会员档案（与 member-service 的 MemberVO 字段保持一致，仅做透传）。 */
@Data
@Schema(description = "会员档案")
public class MemberProfileVO {
    private Long id;
    private String memberNo;
    private String phone;
    private String nickname;
    private String avatarUrl;
    private String gender;
    private LocalDate birthday;

    private Long levelId;
    private String levelName;
    private String levelCode;

    private Long registerStoreId;
    private String registerStoreName;
    private String registerChannel;

    private Long totalConsumeAmount;
    private Long totalRechargeAmount;
    private Integer totalOrderCount;
    private Long points;
    private LocalDateTime lastConsumeTime;

    private String status;
    private String freezeReason;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
