package com.teaclub.cloud.wallet.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 消费扣款命令。金额由服务端计算，绝不来自客户端。
 */
@Data
@Builder
public class ConsumeOutCmd {
    private Long brandId;
    private Long memberId;
    private Long storeId;
    /** 幂等键：订单号 */
    private String orderNo;
    /** 应付金额（分） */
    private Long payableAmount;
    /** 扣款顺序覆盖（为空则取品牌配置） */
    private String deductOrder;
    private Long operatorId;
    private String operatorType;
}
