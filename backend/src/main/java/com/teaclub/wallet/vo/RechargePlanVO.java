package com.teaclub.wallet.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "充值方案")
public class RechargePlanVO {
    private Long id;
    private String planCode;
    private String planName;
    @Schema(description = "充值金额（分）")
    private Long rechargeAmount;
    @Schema(description = "赠送金额（分）")
    private Long giftAmount;
    private Integer giftExpireDays;
    private Long giftPoints;
    private Long minLevelId;
    private String minLevelName;
    private Integer customAmountEnabled;
    private Long minCustomAmount;
    private Long maxCustomAmount;
    private Integer perMemberLimit;
    private Integer sort;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;
    private LocalDateTime createTime;
    @Schema(description = "参与门店；空列表表示全部门店")
    private List<Long> storeIds;
    @Schema(description = "当前是否在活动有效期内")
    private Boolean effective;
}
