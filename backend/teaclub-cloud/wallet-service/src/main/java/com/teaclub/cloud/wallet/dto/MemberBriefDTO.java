package com.teaclub.cloud.wallet.dto;

import lombok.Data;

/**
 * 会员简要信息（wallet-service 视角）。
 *
 * <p>数据来源为 member-service 内部接口 {@code /api/member/internal/**}，
 * <b>本服务不直接访问 member_db</b>。
 */
@Data
public class MemberBriefDTO {
    private Long id;
    private String memberNo;
    private String nickname;
    private String phone;
    private Long levelId;
    private Integer levelNo;
    private String status;
    private Long registerStoreId;
}
