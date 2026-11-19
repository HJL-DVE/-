package com.teaclub.cloud.wallet.controller;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.wallet.dto.TransactionQuery;
import com.teaclub.cloud.wallet.service.RechargePlanService;
import com.teaclub.cloud.wallet.service.WalletQueryService;
import com.teaclub.cloud.wallet.vo.RechargePlanVO;
import com.teaclub.cloud.wallet.vo.WalletTransactionVO;
import com.teaclub.cloud.wallet.vo.WalletVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 会员端钱包（微信小程序，docs/33 §3.6）。
 *
 * <p><b>身份</b>：一律取 {@link UserContext#requireMemberId()}（网关注入 X-Member-Id，
 * 缺失即 401），不信任任何客户端传入的 memberId。
 *
 * <p><b>铁律</b>：本层只读。会员端绝不直接加余额，
 * 充值入账只能由 payment 通知 → {@code /api/wallet/internal/recharge-order/paid} 驱动。
 */
@Tag(name = "会员端钱包", description = "我的余额、我的资金流水、可用充值方案")
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletAppController {

    private final WalletQueryService walletQueryService;
    private final RechargePlanService rechargePlanService;

    @Operation(summary = "我的钱包账户")
    @GetMapping("/account")
    public Result<Map<String, Object>> account() {
        Long memberId = UserContext.requireMemberId();
        WalletVO wallet = inBrandScope(() -> walletQueryService.getByMember(memberId));

        long principal = nz(wallet.getPrincipalBalance());
        long gift = nz(wallet.getGiftBalance());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("principalBalance", principal);
        data.put("giftBalance", gift);
        data.put("totalBalance", principal + gift);
        data.put("status", wallet.getStatus());
        return Result.ok(data);
    }

    @Operation(summary = "我的资金流水分页")
    @GetMapping("/transactions")
    public Result<PageResult<WalletTransactionVO>> transactions(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String bizType) {
        Long memberId = UserContext.requireMemberId();

        TransactionQuery query = new TransactionQuery();
        if (pageNo != null) {
            query.setPageNo(pageNo);
        }
        if (pageSize != null) {
            query.setPageSize(pageSize);
        }
        // 会员维度由令牌决定，忽略任何客户端过滤条件
        query.setMemberId(memberId);
        query.setBizType(bizType);
        return Result.ok(inBrandScope(() -> walletQueryService.pageTransaction(query)));
    }

    @Operation(summary = "可用充值方案（含赠送金额与说明）")
    @GetMapping("/plans")
    public Result<List<RechargePlanVO>> plans(@RequestParam(required = false) Long storeId) {
        Long memberId = UserContext.requireMemberId();
        // listAvailable 按品牌 + 会员等级 + 门店参与关系过滤，本身不含门店数据权限校验
        return Result.ok(rechargePlanService.listAvailable(storeId, memberId));
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    /**
     * 以「品牌全域」视角执行既有 service 调用，结束后恢复原上下文。
     *
     * <p><b>为什么需要</b>：wallet-service 的已迁移 service 用 {@code StoreScope}
     * （管理端门店数据权限）做鉴权；会员令牌只带 X-Member-Id，没有门店授权集合，
     * 直接调用会被判成「没有该门店的数据权限」。
     * 会员端的真实数据边界是<b>本人数据</b>，归属校验由本控制器按 memberId 显式完成，
     * 因此这里只放开门店范围，不重写任何资金/充值业务逻辑。
     */
    static <T> T inBrandScope(Supplier<T> action) {
        LoginUser current = UserContext.require();
        UserContext.set(LoginUser.builder()
                .userId(current.getUserId())
                .username(current.getUsername())
                .memberId(current.getMemberId())
                .brandId(current.getBrandId())
                .userType(current.getUserType())
                .roles(current.getRoles())
                .permissions(current.getPermissions())
                .allStoreAccess(true)
                .storeIds(current.getStoreIds())
                .build());
        try {
            return action.get();
        } finally {
            UserContext.set(current);
        }
    }
}
