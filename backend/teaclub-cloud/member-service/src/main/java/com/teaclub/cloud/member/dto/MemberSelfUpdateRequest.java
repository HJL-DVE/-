package com.teaclub.cloud.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会员端自助修改资料请求。
 *
 * <p>只允许改昵称/头像/性别，等级、状态、备注等字段不开放给会员端。
 */
@Data
@Schema(description = "会员端资料修改请求")
public class MemberSelfUpdateRequest {

    @Size(max = 64)
    private String nickname;

    @Size(max = 512)
    private String avatarUrl;

    @Pattern(regexp = "^(MALE|FEMALE|UNKNOWN)$", message = "性别只能是 MALE / FEMALE / UNKNOWN")
    private String gender;
}
