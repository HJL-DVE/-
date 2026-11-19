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

    /** 查询充值订单（供 payment-service 发起充值支付时校验）。 */
    Object getRechargeOrder(String rechargeNo);

    /** 人工调整（幂等键：审批单号） */
    WalletChangeResult adjust(Long brandId, Long memberId, Long storeId, String approvalNo,
                              Long principalChange, Long giftChange,
                              String remark, Long operatorId);
}
