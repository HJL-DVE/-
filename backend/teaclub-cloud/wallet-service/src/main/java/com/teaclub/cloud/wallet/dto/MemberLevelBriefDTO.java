package com.teaclub.cloud.wallet.dto;

import lombok.Data;

/**
 * 会员等级简要信息（wallet-service 视角），来源为 member-service 内部接口。
 */
@Data
public class MemberLevelBriefDTO {
    private Long id;
    private String levelName;
    private Integer levelNo;
}
