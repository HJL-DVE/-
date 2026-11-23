package com.teaclub.cloud.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 会员端自助修改资料请求。
 *
 * <p>只允许改昵称/生日，等级、状态、备注等字段不开放给会员端。
 * 生日一旦设置后会员端不可再改（防止反复修改薅生日福利），如需变更联系门店。
 */
@Data
@Schema(description = "会员端资料修改请求")
public class MemberSelfUpdateRequest {

    @Size(max = 64)
    private String nickname;

    @Schema(description = "生日（yyyy-MM-dd），仅首次设置生效")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "生日格式必须是 yyyy-MM-dd")
    private String birthday;
}
