package com.teaclub.wallet.service;

import com.teaclub.common.api.PageResult;
import com.teaclub.wallet.dto.RechargeCreateRequest;
import com.teaclub.wallet.dto.RechargeOrderQuery;
import com.teaclub.wallet.vo.RechargeOrderVO;

public interface RechargeService {

    /**
     * 创建充值订单。金额由服务端按方案计算，<b>不接受客户端传入金额</b>。
     */
    RechargeOrderVO createRecharge(RechargeCreateRequest request);

    RechargeOrderVO detail(String rechargeNo);

    PageResult<RechargeOrderVO> page(RechargeOrderQuery query);

    /**
     * 支付成功处理（由支付通知/主动查单调用）。幂等。
     */
    void processPaySuccess(String rechargeNo, String paymentNo);

    /**
     * 取消未支付的充值订单。
     */
    void cancel(String rechargeNo, String reason);
}
