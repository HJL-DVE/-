package com.teaclub.cloud.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "积分账户")
public class PointsAccountVO {
    private Long memberId;
    @Schema(description = "可用积分")
    private Long totalPoints;
    @Schema(description = "累计获得")
    private Long totalEarned;
    @Schema(description = "累计消耗")
    private Long totalConsumed;
    @Schema(description = "累计冲正（退款扣回）")
    private Long totalReversed;
    private LocalDateTime updateTime;
}
