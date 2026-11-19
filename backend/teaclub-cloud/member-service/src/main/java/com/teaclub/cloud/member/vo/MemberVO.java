package com.teaclub.cloud.member.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "会员信息")
public class MemberVO {
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

    @Schema(description = "累计消费金额（分）")
    private Long totalConsumeAmount;
    @Schema(description = "累计充值金额（分）")
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
