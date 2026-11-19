package com.teaclub.cloud.wallet.controller;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.wallet.dto.TransactionQuery;
import com.teaclub.cloud.wallet.dto.WalletQuery;
import com.teaclub.cloud.wallet.entity.WalWallet;
import com.teaclub.cloud.wallet.service.WalletQueryService;
import com.teaclub.cloud.wallet.service.WalletService;
import com.teaclub.cloud.wallet.vo.WalletStatsVO;
import com.teaclub.cloud.wallet.vo.WalletTransactionVO;
import com.teaclub.cloud.wallet.vo.WalletVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "会员钱包", description = "钱包查询、资金流水、对账")
@RestController
@RequestMapping("/api/admin/wallet")
@RequiredArgsConstructor
public class WalletAdminController {

    private final WalletService walletService;
    private final WalletQueryService walletQueryService;

    @Operation(summary = "钱包分页查询（受门店数据权限约束）")
    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('wallet:account:list')")
    public Result<PageResult<WalletVO>> pageWallet(WalletQuery query) {
        return Result.ok(walletQueryService.pageWallet(query));
    }

    @Operation(summary = "按会员查钱包（含实付/赠送余额构成）")
    @GetMapping("/accounts/member/{memberId}")
    @PreAuthorize("hasAuthority('wallet:account:list')")
    public Result<WalletVO> byMember(@PathVariable Long memberId) {
        return Result.ok(walletQueryService.getByMember(memberId));
    }

    @Operation(summary = "开通/获取会员钱包")
    @PostMapping("/accounts/member/{memberId}/open")
    @PreAuthorize("hasAuthority('wallet:account:list')")
    public Result<Map<String, Object>> open(@PathVariable Long memberId) {
        WalWallet wallet = walletService.getOrCreateWallet(UserContext.currentBrandId(), memberId);
        return Result.ok(Map.of(
                "walletId", wallet.getId(),
                "principalBalance", wallet.getPrincipalBalance(),
                "giftBalance", wallet.getGiftBalance()
        ));
    }

    @Operation(summary = "资金流水分页查询")
    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('wallet:txn:list')")
    public Result<PageResult<WalletTransactionVO>> pageTransaction(TransactionQuery query) {
        return Result.ok(walletQueryService.pageTransaction(query));
    }

    @Operation(summary = "某会员的近期流水")
    @GetMapping("/transactions/member/{memberId}")
    @PreAuthorize("hasAuthority('wallet:txn:list')")
    public Result<List<WalletTransactionVO>> memberTransactions(@PathVariable Long memberId,
                                                                @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(walletQueryService.listByMember(memberId, limit));
    }

    @Operation(summary = "资金对账：校验余额与流水是否一致")
    @GetMapping("/reconcile")
    @PreAuthorize("hasAuthority('payment:reconcile')")
    public Result<Map<String, Object>> reconcile() {
        List<Map<String, Object>> inconsistent = walletQueryService.reconcile();
        return Result.ok(Map.of(
                "consistent", inconsistent.isEmpty(),
                "inconsistentCount", inconsistent.size(),
                "details", inconsistent
        ));
    }

    @Operation(summary = "钱包统计（仪表盘：余额总量/充值总额/赠送总额）")
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('wallet:account:list')")
    public Result<WalletStatsVO> stats() {
        return Result.ok(walletQueryService.stats());
    }
}
