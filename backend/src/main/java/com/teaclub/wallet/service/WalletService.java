package com.teaclub.wallet.service;

import com.teaclub.wallet.dto.*;
import com.teaclub.wallet.entity.WalWallet;

/**
 * 钱包服务。**余额变更的唯一入口**。
 *
 * <p>资金安全不变量：
 * <ol>
 *   <li>每次余额变动必须产生一条 {@code wal_wallet_transaction}</li>
 *   <li>流水必须记录变动前余额、变动额、变动后余额</li>
 *   <li>实付金与赠送金分列记账，永不混记</li>
 *   <li>余额永不为负</li>
 *   <li>同一幂等键只能成功一次</li>
 * </ol>
 */
public interface WalletService {

    /**
     * 获取或创建会员钱包（品牌级）。
     */
    WalWallet getOrCreateWallet(Long brandId, Long memberId);

    /**
     * 查询钱包（不创建）。
     */
    WalWallet getWallet(Long brandId, Long memberId);

    /**
     * 充值入账：实付金与赠送金分别增加。
     *
     * @param cmd 幂等键为 cmd.rechargeNo
     */
    WalletChangeResult rechargeIn(RechargeInCmd cmd);

    /**
     * 消费扣款：按配置顺序扣减赠送金/实付金。
     *
     * @param cmd 幂等键为 cmd.orderNo
     */
    WalletChangeResult consumeOut(ConsumeOutCmd cmd);

    /**
     * 退款回冲：按原交易资金来源分别退回实付金与赠送金。
     *
     * @param cmd 幂等键为 cmd.refundNo
     */
    WalletChangeResult refundIn(RefundInCmd cmd);

    /**
     * 充值退款：扣回已入账的实付金与赠送金。
     *
     * <p>与 {@link #refundIn} 的区别：这是资金流出方向，需校验余额充足。
     */
    WalletChangeResult rechargeRefundOut(Long brandId, Long memberId, Long storeId,
                                         String refundNo, Long principalAmount, Long giftAmount,
                                         String remark, Long operatorId);

    /**
     * 人工调整（仅财务权限，需审批单号作为幂等键）。
     */
    WalletChangeResult adjust(Long brandId, Long memberId, Long storeId, String approvalNo,
                              Long principalChange, Long giftChange, String remark, Long operatorId);
}
