package com.teaclub.cloud.wallet.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 充值入账命令。内部使用，金额由服务端计算。
 */
@Data
@Builder
public class RechargeInCmd {
    private Long brandId;
    private Long memberId;
    private Long storeId;
    /** 幂等键：充值单号 */
    private String rechargeNo;
    /** 实付金额（分） */
    private Long payAmount;
    /** 赠送金额（分） */
    private Long giftAmount;
    /** 赠送金有效天数，0 表示永久 */
    private Integer giftExpireDays;
    private Long operatorId;
    private String operatorType;
}

