package com.teaclub.cloud.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "充值方案保存请求")
public class RechargePlanSaveRequest {

    @NotBlank(message = "方案编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{2,32}$", message = "方案编码由 2-32 位字母数字组成")
    private String planCode;

    @NotBlank(message = "方案名称不能为空")
    @Size(max = 64)
    private String planName;

    @NotNull(message = "充值金额不能为空")
    @Min(value = 0, message = "充值金额不能为负")
    @Schema(description = "充值金额（分）")
    private Long rechargeAmount;

    @NotNull(message = "赠送金额不能为空")
    @Min(value = 0, message = "赠送金额不能为负")
    @Schema(description = "赠送金额（分）")
    private Long giftAmount;

    @Min(value = 0)
    @Schema(description = "赠送金有效期天数，0=永久")
    private Integer giftExpireDays = 365;

    @Min(value = 0)
    private Long giftPoints = 0L;

    @Schema(description = "最低会员等级要求")
    private Long minLevelId;

    @Schema(description = "是否自定义金额方案")
    private Integer customAmountEnabled = 0;

    @Min(value = 0)
    @Schema(description = "自定义金额下限（分）")
    private Long minCustomAmount = 0L;

    @Min(value = 0)
    @Schema(description = "自定义金额上限（分），0=不限")
    private Long maxCustomAmount = 0L;

    @Min(value = 0)
    @Schema(description = "每人充值次数上限，0=不限")
    private Integer perMemberLimit = 0;

    private Integer sort = 0;

    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能是 ENABLED 或 DISABLED")
    private String status = "ENABLED";

    @Schema(description = "活动开始时间，为空表示不限")
    private LocalDateTime startTime;

    @Schema(description = "活动结束时间，为空表示不限")
    private LocalDateTime endTime;

    @Size(max = 255)
    private String remark;

    @Schema(description = "参与门店 ID 列表；为空表示全部门店")
    private List<Long> storeIds;
}
