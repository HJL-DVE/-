package com.teaclub.cloud.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "状态变更请求")
public class StatusUpdateRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(ENABLED|DISABLED|LOCKED|OPEN|SUSPENDED|CLOSED|NORMAL|FROZEN|ON_SALE|OFF_SALE)$",
            message = "状态值不合法")
    private String status;

    @Schema(description = "变更原因（冻结/禁用时填写）")
    private String reason;
}
