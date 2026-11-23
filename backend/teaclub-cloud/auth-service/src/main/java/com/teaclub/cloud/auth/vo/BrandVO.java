package com.teaclub.cloud.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "品牌信息")
public class BrandVO {
    private Long id;
    private String brandCode;
    private String brandName;
    private String logoUrl;
    private String contactPhone;

    @Schema(description = "扣款顺序 GIFT_FIRST / PRINCIPAL_FIRST")
    private String walletDeductOrder;
    /** 退款是否需要人工审核：1 需要 0 自助秒退 */
    private Integer refundNeedAudit;
    /** 同一会员每日退卡次数上限，0 表示不限 */
    private Integer refundDailyLimit;
    /** 赠送金已被消费时的处置：REJECT 拒绝转人工 / ALLOW 按剩余扣回 */
    private String refundGiftConsumedAction;

    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
