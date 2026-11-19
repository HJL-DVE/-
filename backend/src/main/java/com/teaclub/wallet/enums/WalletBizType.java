package com.teaclub.wallet.enums;

import lombok.Getter;

/**
 * 钱包流水业务类型。与 uk_txn_biz(biz_type, biz_no) 共同构成幂等键。
 */
@Getter
public enum WalletBizType {

    /** 充值入账 */
    RECHARGE_IN,
    /** 消费扣款 */
    CONSUME_OUT,
    /** 退款回冲（消费订单退款） */
    REFUND_IN,
    /** 充值退款（扣回已入账的实付金与赠送金） */
    RECHARGE_REFUND_OUT,
    /** 人工调整 */
    ADMIN_ADJUST,
    /** 赠送金过期核销 */
    GIFT_EXPIRE
}
