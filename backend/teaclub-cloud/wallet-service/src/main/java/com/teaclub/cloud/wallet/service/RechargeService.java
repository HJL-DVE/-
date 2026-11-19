package com.teaclub.cloud.wallet.service;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.wallet.dto.RechargeCreateRequest;
import com.teaclub.cloud.wallet.dto.RechargeOrderQuery;
import com.teaclub.cloud.wallet.vo.RechargeOrderVO;

/**
 * 充值订单管理端服务。
 *
 * <p><b>充值入账不在本接口</b>：入账只由支付成功回调驱动
 * （{@code WalletService#processRechargePaid}），
 * 管理端只负责下单、查询与取消，避免出现第二条入账路径。
 */
public interface RechargeService {

    /**
     * 创建充值订单。金额由服务端按方案计算，<b>不接受客户端传入金额</b>。
     */
    RechargeOrderVO createRecharge(RechargeCreateRequest request);

    RechargeOrderVO detail(String rechargeNo);

    PageResult<RechargeOrderVO> page(RechargeOrderQuery query);

    /**
     * 取消未支付的充值订单。
     */
    void cancel(String rechargeNo, String reason);
}
