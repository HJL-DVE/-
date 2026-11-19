package com.teaclub.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "会员等级保存请求")
public class MemberLevelSaveRequest {

    @NotBlank(message = "等级编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$", message = "等级编码需为大写字母、数字或下划线，2-32 位")
    private String levelCode;

    @NotBlank(message = "等级名称不能为空")
    @Size(max = 32)
    private String levelName;

    @NotNull(message = "等级序号不能为空")
    @Min(value = 1, message = "等级序号需大于 0")
    @Max(value = 99, message = "等级序号不能超过 99")
    private Integer levelNo;

    @NotBlank(message = "升级方式不能为空")
    @Pattern(regexp = "^(CONSUME_AMOUNT|RECHARGE_AMOUNT|MANUAL)$",
            message = "升级方式不合法")
    private String upgradeType;

    @NotNull(message = "升级门槛不能为空")
    @Min(value = 0, message = "升级门槛不能为负")
    @Schema(description = "升级门槛（分）")
    private Long upgradeThreshold;

    @NotNull(message = "折扣率不能为空")
    @Min(value = 1, message = "折扣率需大于 0")
    @Max(value = 10000, message = "折扣率不能超过 10000")
    @Schema(description = "折扣万分比，10000=不打折，8800=8.8折")
    private Integer discountRate;

    @NotNull(message = "积分倍率不能为空")
    @Min(value = 1, message = "积分倍率需大于 0")
    @Max(value = 1000, message = "积分倍率不能超过 1000")
    @Schema(description = "积分倍率百分比，100=1倍")
    private Integer pointRate;

    @Size(max = 512)
    private String iconUrl;

    @Size(max = 1024)
    private String benefits;

    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能是 ENABLED 或 DISABLED")
    private String status = "ENABLED";
}
