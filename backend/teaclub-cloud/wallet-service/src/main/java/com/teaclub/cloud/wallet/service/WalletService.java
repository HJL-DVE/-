package com.teaclub.cloud.wallet.service;

import com.teaclub.cloud.wallet.dto.ConsumeOutCmd;
import com.teaclub.cloud.wallet.dto.RechargeInCmd;
import com.teaclub.cloud.wallet.dto.RefundInCmd;
import com.teaclub.cloud.wallet.dto.WalletChangeResult;
import com.teaclub.cloud.wallet.entity.WalWallet;

/**
 * 钱包服务接口。
 *
 * <p><b>资金唯一入口</b>：任何余额变动都必须经过本接口，
 * 禁止其他服务直接 UPDATE `wal_wallet`。
 */
public interface WalletService {

    /** 查询钱包，不存在则创建 */
    WalWallet getOrCreateWallet(Long brandId, Long memberId);

    /** 查询钱包，不存在则抛异常 */
    WalWallet getWallet(Long brandId, Long memberId);

    /** 充值入账（幂等键：充值单号） */
    WalletChangeResult rechargeIn(RechargeInCmd cmd);

    /** 消费扣款（幂等键：订单号） */
    WalletChangeResult consumeOut(ConsumeOutCmd cmd);

    /** 退款回冲（幂等键：退款单号） */
    WalletChangeResult refundIn(RefundInCmd cmd);

    /** 充值退款：扣回已入账金额（幂等键：退款单号） */
    WalletChangeResult rechargeRefundOut(Long brandId, Long memberId, Long storeId,
                                         String refundNo, Long principalAmount, Long giftAmount,
                                         String remark, Long operatorId);

    /**
     * 充值单退款完成：将状态推进为 REFUNDED。
     *
     * <p>前置条件：必须已存在该退款单对应的 RECHARGE_REFUND_OUT 流水，
     * 避免出现「充值单显示已退款、钱包却没扣回」的账实不符。
     */
    void markRechargeRefunded(String rechargeNo, String refundNo);

    /**
     * 创建充值订单（状态 PENDING，等待支付）。
     *
     * @return 充值单号
     */
    String createRechargeOrder(Long brandId, Long memberId, Long storeId,
                               Long rechargeAmount, Long giftAmount, Integer giftExpireDays);

    /**
     * 充值支付成功入账（幂等键：充值单号）。
     *
     * <p>由 payment-service 在收到充值支付成功通知后调用。
     * 内部先锁定充值单校验状态，再走 rechargeIn 写资金流水，
     * 保证「充值单状态 + 钱包余额 + 资金流水」在同一事务内一致。
     */
    WalletChangeResult processRechargePaid(String rechargeNo, String paymentNo);

    /**
     * 判断某业务单是否已有对应资金流水（供跨服务对账使用）。
     */
    boolean existsTxn(String bizType, String bizNo);

    /** 查询充值订单（供 payment-service 发起充值支付时校验）。 */
    Object getRechargeOrder(String rechargeNo);

    /**
     * 冻结余额（不修改账户余额，仅写冻结记录）。
     *
     * @return 冻结记录 ID
     */
    Long freeze(Long brandId, Long memberId, String orderNo,
                Long freezePrincipal, Long freezeGift, Integer expireMinutes);

    /** 查询可用余额 = 账户余额 - 有效冻结额。 */
    java.util.Map<String, Object> getAvailableBalance(Long brandId, Long memberId);

    /**
     * 确认冻结扣款：释放冻结记录 + 走原有 consumeOut 写资金流水。
     *
     * @return 资金变动结果
     */
    WalletChangeResult confirmFreeze(Long brandId, Long memberId, String orderNo,
                                     Long storeId, Long operatorId);

    /**
     * 部分确认冻结扣款：只扣指定金额，其余部分视为释放。
     *
     * <p>用于「订单部分成交」或「实际应付小于冻结额」的场景。
     * 未扣除的冻结额度会随记录状态变更一并释放，不需要额外调用。
     *
     * @param principal 本次实际扣除的实付额（不得超过冻结额）
     * @param gift      本次实际扣除的赠送额（不得超过冻结额）
     */
    WalletChangeResult confirmFreezePartial(Long brandId, Long memberId, String orderNo,
                                            Long storeId, Long operatorId,
                                            Long principal, Long gift);

    /**
     * 释放冻结（失败订单/超时订单，不写流水）。
     *
     * @return 是否成功释放
     */
    boolean releaseFreeze(Long brandId, Long memberId, String orderNo, String reason);

    /** 人工调整（幂等键：审批单号） */
    WalletChangeResult adjust(Long brandId, Long memberId, Long storeId, String approvalNo,
                              Long principalChange, Long giftChange,
                              String remark, Long operatorId);

    /**
     * 赠送金到期核销（幂等键：GIFT-EXPIRE-{充值流水ID}）。
     *
     * <p>核销金额 = min(该笔充值赠送额, 当前赠送余额)，保证不把余额扣成负数。
     * 已花掉的赠送金不追回；赠送金已花完时仍写一条 0 额流水作为"已处理"标记。
     *
     * @param rechargeTxnId 充值入账流水 ID
     * @return 核销结果；流水已过期或不存在时返回 null
     */
    WalletChangeResult expireGift(Long rechargeTxnId);
}
