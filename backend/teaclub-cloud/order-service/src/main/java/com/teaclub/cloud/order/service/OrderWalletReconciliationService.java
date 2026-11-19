package com.teaclub.cloud.order.service;

import com.teaclub.cloud.order.client.WalletInternalClient;
import com.teaclub.cloud.order.entity.OmsOrder;
import com.teaclub.cloud.order.mapper.OmsOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单 ↔ 钱包 跨服务对账。
 *
 * <p>wallet-service 的对账只校验「库内自洽」（余额 = 流水累计）。
 * 但它无法回答另一个问题：
 * <b>一张标记为余额已支付的订单，钱包里到底有没有对应的扣款流水？</b>
 *
 * <p>本服务专门补这一层：遍历最近 N 天余额支付的订单，
 * 逐笔调用 wallet-service 的 `/txn-exists` 核对是否存在 CONSUME_OUT 流水。
 *
 * <p>判定为不一致的情况：
 * <ul>
 *   <li>订单 PAY/COMPLETED/REFUNDED，但钱包<b>没有</b>扣款流水 → 订单虚标已付</li>
 *   <li>调用失败 → 计入 unknown，<b>不误判为不一致</b></li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderWalletReconciliationService {

    private final OmsOrderMapper orderMapper;
    private final WalletInternalClient walletClient;

    private static final int LIMIT = 200;

    @Scheduled(fixedDelayString = "${teaclub.reconcile.order-wallet-ms:900000}")
    public void scheduled() {
        Map<String, Object> r = reconcile(7);
        if (!Boolean.TRUE.equals(r.get("passed"))) {
            log.error("[订单-钱包对账] 发现不一致: {}", r.get("issues"));
        } else {
            log.info("[订单-钱包对账] 通过 checked={} unknown={}",
                    r.get("checked"), r.get("unknown"));
        }
    }

    /**
     * 执行对账。
     *
     * @param days 回溯天数
     */
    public Map<String, Object> reconcile(int days) {
        List<OmsOrder> orders = orderMapper.selectBalancePaidSince(days, LIMIT);
        List<Map<String, Object>> issues = new ArrayList<>();
        int checked = 0;
        int unknown = 0;

        for (OmsOrder o : orders) {
            if (o.getMemberId() == null) {
                continue;
            }
            checked++;
            try {
                var resp = walletClient.txnExists("CONSUME_OUT", o.getOrderNo());
                if (resp == null || resp.getCode() != 0) {
                    unknown++;
                    continue;
                }
                if (!Boolean.TRUE.equals(resp.getData())) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("code", "ORDER_PAID_WITHOUT_WALLET_TXN");
                    item.put("orderNo", o.getOrderNo());
                    item.put("status", o.getStatus());
                    item.put("paidAmount", o.getPaidAmount());
                    item.put("memberId", o.getMemberId());
                    issues.add(item);
                }
            } catch (Exception e) {
                // 钱包不可用：不能据此判定订单有问题
                unknown++;
                log.warn("[订单-钱包对账] 查询失败 orderNo={} err={}", o.getOrderNo(), e.getMessage());
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("checked", checked);
        report.put("unknown", unknown);
        report.put("issueCount", issues.size());
        report.put("issues", issues);
        report.put("passed", issues.isEmpty());
        report.put("checkedAt", LocalDateTime.now().toString());
        return report;
    }
}
