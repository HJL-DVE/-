package com.teaclub.cloud.wallet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钱包资金对账服务。
 *
 * <p>校验资金不变量 I1~I6，发现任何不一致都立即告警。
 * 这是资金安全的独立第三方校验 —— 不依赖业务代码自证。
 *
 * <p>不变量定义：
 * <pre>
 * I1 余额非负：principal_balance >= 0 且 gift_balance >= 0
 * I2 余额守恒：账户余额 == 该钱包全部流水变动之和
 * I3 单笔自洽：principal_after == principal_before + principal_change（赠送同理）
 * I4 链式衔接：相邻流水中后一条的 before == 前一条的 after
 * I5 业务唯一：(biz_type, biz_no) 不得重复
 * I6 冻结可控：有效冻结额 <= 账户余额
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletReconciliationService {

    private final JdbcTemplate jdbcTemplate;

    /** 定时对账（默认 10 分钟一次）。 */
    @Scheduled(fixedDelayString = "${teaclub.reconcile.ms:600000}")
    public void scheduledReconcile() {
        Map<String, Object> report = reconcile();
        if (Boolean.TRUE.equals(report.get("passed"))) {
            log.info("[对账] 通过 wallets={} ledgers={}",
                    report.get("walletCount"), report.get("ledgerCount"));
        } else {
            log.error("[对账] 发现不一致: {}", report.get("issues"));
        }
    }

    /** 执行全量对账。 */
    public Map<String, Object> reconcile() {
        List<Map<String, Object>> issues = new ArrayList<>();

        Long walletCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wal_wallet", Long.class);
        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wal_wallet_transaction", Long.class);

        // I1 余额非负
        addIssues(issues, "I1_BALANCE_NEGATIVE",
                "SELECT id AS wallet_id, principal_balance, gift_balance FROM wal_wallet "
                        + "WHERE principal_balance < 0 OR gift_balance < 0");

        // I2 余额守恒
        addIssues(issues, "I2_BALANCE_MISMATCH",
                "SELECT w.id AS wallet_id, w.principal_balance, w.gift_balance, "
                        + "COALESCE(SUM(t.principal_change),0) AS ledger_principal, "
                        + "COALESCE(SUM(t.gift_change),0) AS ledger_gift "
                        + "FROM wal_wallet w LEFT JOIN wal_wallet_transaction t ON t.wallet_id = w.id "
                        + "GROUP BY w.id, w.principal_balance, w.gift_balance "
                        + "HAVING w.principal_balance <> COALESCE(SUM(t.principal_change),0) "
                        + "   OR w.gift_balance <> COALESCE(SUM(t.gift_change),0)");

        // I3 单笔自洽
        addIssues(issues, "I3_ROW_INCONSISTENT",
                "SELECT id AS txn_id, principal_before, principal_change, principal_after, "
                        + "gift_before, gift_change, gift_after FROM wal_wallet_transaction "
                        + "WHERE principal_after <> principal_before + principal_change "
                        + "   OR gift_after <> gift_before + gift_change");

        // I4 链式衔接
        addIssues(issues, "I4_CHAIN_BROKEN",
                "SELECT wallet_id, txn_id, expect_before, actual_before FROM ( "
                        + "  SELECT wallet_id, id AS txn_id, principal_before AS actual_before, "
                        + "         LAG(principal_after) OVER (PARTITION BY wallet_id ORDER BY id) AS expect_before "
                        + "  FROM wal_wallet_transaction "
                        + ") x WHERE expect_before IS NOT NULL AND expect_before <> actual_before");

        // I5 业务唯一
        addIssues(issues, "I5_DUPLICATE_BIZ",
                "SELECT biz_type, biz_no, COUNT(*) AS cnt FROM wal_wallet_transaction "
                        + "GROUP BY biz_type, biz_no HAVING COUNT(*) > 1");

        // I6 冻结额度可控
        addIssues(issues, "I6_FROZEN_EXCEED",
                "SELECT w.id AS wallet_id, w.principal_balance, w.gift_balance, "
                        + "COALESCE(f.fp,0) AS frozen_principal, COALESCE(f.fg,0) AS frozen_gift "
                        + "FROM wal_wallet w LEFT JOIN ( "
                        + "  SELECT wallet_id, SUM(freeze_principal) fp, SUM(freeze_gift) fg "
                        + "  FROM wal_wallet_freeze WHERE status = 'FROZEN' GROUP BY wallet_id "
                        + ") f ON f.wallet_id = w.id "
                        + "WHERE COALESCE(f.fp,0) > w.principal_balance "
                        + "   OR COALESCE(f.fg,0) > w.gift_balance");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("walletCount", walletCount);
        report.put("ledgerCount", ledgerCount);
        report.put("issues", issues);
        report.put("issueCount", issues.size());
        report.put("passed", issues.isEmpty());
        report.put("checkedAt", java.time.LocalDateTime.now().toString());
        return report;
    }

    private void addIssues(List<Map<String, Object>> issues, String code, String sql) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", code);
                item.putAll(r);
                issues.add(item);
            }
        } catch (Exception e) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("error", e.getMessage());
            issues.add(item);
        }
    }
}
