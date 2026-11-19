package com.teaclub.cloud.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "会员资料更新请求")
public class MemberUpdateRequest {

    @Size(max = 64)
    private String nickname;

    @Size(max = 512)
    private String avatarUrl;

    @Pattern(regexp = "^(MALE|FEMALE|UNKNOWN)$", message = "性别只能是 MALE / FEMALE / UNKNOWN")
    private String gender;

    private LocalDate birthday;

    @Schema(description = "会员等级 ID（为空表示不修改）")
    private Long levelId;

    @Size(max = 255)
    private String remark;
}
