package com.teaclub.cloud.member.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会员简要信息，供 other 服务内部调用（钱包/订单展示用）。
 */
@Data
@Schema(description = "会员简要信息")
public class MemberBriefVO {
    private Long id;
    private String memberNo;
    private String nickname;
    private String phone;
    private Long levelId;
    private Integer levelNo;
    private String status;
    private Long registerStoreId;
}
