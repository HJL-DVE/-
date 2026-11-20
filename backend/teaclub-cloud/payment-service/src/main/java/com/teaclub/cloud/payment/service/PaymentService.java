package com.teaclub.cloud.payment.service;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.payment.dto.PayCreateRequest;
import com.teaclub.cloud.payment.dto.RefundApplyRequest;
import com.teaclub.cloud.payment.vo.PayCreateVO;
import com.teaclub.cloud.payment.vo.PaymentVO;
import com.teaclub.cloud.payment.vo.RefundVO;

import java.util.Map;

public interface PaymentService {

    /** 创建支付单并发起渠道下单。金额由服务端按业务单确定。 */
    /**
     * 管理端发起支付（桌面收银台）。
     *
     * <p>未显式指定 channel 时默认 <b>WECHAT_NATIVE</b>（返回 codeUrl 供前端出二维码）。
     */
    PayCreateVO createPayment(PayCreateRequest request);

    /**
     * 会员端发起支付（微信小程序）。
     *
     * <p>未显式指定 channel 时默认 <b>WECHAT_JSAPI</b>（返回 invokeParams 供 wx.requestPayment）。
     * 与管理端入口的差别<b>只有默认渠道</b>，金额校验、状态机、归属校验全部共用。
     */
    PayCreateVO createPaymentForMember(PayCreateRequest request);

    /** 主动查单（真实渠道），Mock 渠道返回明确提示 */
    PaymentVO queryPayment(String paymentNo);

    /** 处理支付成功回调（幂等）。由通知/模拟通知/查单共同调用。 */
    void handlePaySuccess(String paymentNo, String transactionId, Long notifyAmount, String provider);

    /** 申请退款 */
    RefundVO applyRefund(RefundApplyRequest request);

    /** 处理退款成功回调（幂等） */
    void handleRefundSuccess(String refundNo, String wxRefundId);

    /** 支付单分页 */
    PageResult<PaymentVO> pagePayment(Integer pageNo, Integer pageSize, String status, String bizType);

    /** 退款单分页 */
    PageResult<RefundVO> pageRefund(Integer pageNo, Integer pageSize, String status);

    /** 通知记录分页 */
    PageResult<Map<String, Object>> pageNotify(Integer pageNo, Integer pageSize, String status);
}
