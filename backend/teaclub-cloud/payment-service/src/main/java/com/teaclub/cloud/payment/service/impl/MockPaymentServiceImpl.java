package com.teaclub.cloud.payment.service.impl;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.payment.entity.PayNotifyLog;
import com.teaclub.cloud.payment.entity.PayPaymentOrder;
import com.teaclub.cloud.payment.mapper.PayNotifyLogMapper;
import com.teaclub.cloud.payment.mapper.PayPaymentOrderMapper;
import com.teaclub.cloud.payment.service.MockPaymentService;
import com.teaclub.cloud.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "teaclub.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentServiceImpl implements MockPaymentService {

    private final PayPaymentOrderMapper paymentMapper;
    private final PayNotifyLogMapper notifyMapper;
    private final PaymentService paymentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String simulatePayNotify(String paymentNo, Long amount) {
        PayPaymentOrder payment = paymentMapper.selectByNo(paymentNo);
        if (payment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "支付单不存在: " + paymentNo);
        }

        // 记录通知日志（与真实通知一致，带幂等键）
        String notifyId = "MOCK-EV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        PayNotifyLog logEntity = new PayNotifyLog();
        logEntity.setProvider("MOCK");
        logEntity.setNotifyId(notifyId);
        logEntity.setNotifyType("PAY");
        logEntity.setEventType("TRANSACTION.SUCCESS");
        logEntity.setOutTradeNo(paymentNo);
        logEntity.setVerifyResult(1);
        logEntity.setProcessStatus("RECEIVED");
        logEntity.setRetryCount(0);
        logEntity.setCreateTime(LocalDateTime.now());
        notifyMapper.insert(logEntity);

        try {
            paymentService.handlePaySuccess(paymentNo,
                    "MOCK-TXN-" + payment.getPaymentNo(),
                    amount == null ? payment.getAmount() : amount,
                    "MOCK");

            PayNotifyLog patch = new PayNotifyLog();
            patch.setId(logEntity.getId());
            patch.setProcessStatus("PROCESSED");
            patch.setProcessMsg("Mock 模拟支付成功");
            notifyMapper.updateById(patch);
            return "支付成功已处理 paymentNo=" + paymentNo;
        } catch (BizException e) {
            PayNotifyLog patch = new PayNotifyLog();
            patch.setId(logEntity.getId());
            patch.setProcessStatus("FAILED");
            patch.setProcessMsg(e.getMessage());
            notifyMapper.updateById(patch);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String simulatePayFail(String paymentNo, String reason) {
        PayPaymentOrder payment = paymentMapper.selectByNo(paymentNo);
        if (payment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "支付单不存在: " + paymentNo);
        }
        if ("SUCCESS".equals(payment.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "支付单已成功，不能改为失败");
        }
        PayPaymentOrder patch = new PayPaymentOrder();
        patch.setId(payment.getId());
        patch.setStatus("FAILED");
        patch.setFailReason(reason == null ? "Mock 模拟支付失败" : reason);
        paymentMapper.updateById(patch);

        PayNotifyLog logEntity = new PayNotifyLog();
        logEntity.setProvider("MOCK");
        logEntity.setNotifyId("MOCK-EV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        logEntity.setNotifyType("PAY");
        logEntity.setEventType("TRANSACTION.FAIL");
        logEntity.setOutTradeNo(paymentNo);
        logEntity.setVerifyResult(1);
        logEntity.setProcessStatus("PROCESSED");
        logEntity.setProcessMsg("Mock 模拟支付失败");
        notifyMapper.insert(logEntity);

        return "支付失败已记录 paymentNo=" + paymentNo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String simulateRefundNotify(String refundNo) {
        PayNotifyLog logEntity = new PayNotifyLog();
        logEntity.setProvider("MOCK");
        logEntity.setNotifyId("MOCK-EV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        logEntity.setNotifyType("REFUND");
        logEntity.setEventType("REFUND.SUCCESS");
        logEntity.setVerifyResult(1);
        logEntity.setProcessStatus("RECEIVED");
        logEntity.setRetryCount(0);
        logEntity.setCreateTime(LocalDateTime.now());
        notifyMapper.insert(logEntity);

        paymentService.handleRefundSuccess(refundNo, "MOCK-REFUND-" + refundNo);

        PayNotifyLog patch = new PayNotifyLog();
        patch.setId(logEntity.getId());
        patch.setProcessStatus("PROCESSED");
        patch.setProcessMsg("Mock 模拟退款成功");
        notifyMapper.updateById(patch);
        return "退款成功已处理 refundNo=" + refundNo;
    }
}
