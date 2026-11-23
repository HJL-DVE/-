package com.teaclub.cloud.wallet.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.wallet.dto.CardRefundApplyRequest;
import com.teaclub.cloud.wallet.service.CardRefundService;
import com.teaclub.cloud.wallet.vo.CardRefundVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员端退卡（微信小程序）。
 *
 * <p>身份一律取 {@code UserContext.requireMemberId()}，不信任客户端传参。
 */
@Tag(name = "会员端退卡", description = "会员自助申请退卡、查看申请进度")
@RestController
@RequestMapping("/api/wallet/card-refund")
@RequiredArgsConstructor
public class CardRefundAppController {

    private final CardRefundService cardRefundService;

    @Operation(summary = "申请退卡")
    @PostMapping
    public Result<CardRefundVO> apply(@Valid @RequestBody CardRefundApplyRequest request) {
        Long memberId = UserContext.requireMemberId();
        return Result.ok(CardRefundAppController.scopeOf(() -> cardRefundService.apply(memberId, request)));
    }

    @Operation(summary = "我的退卡申请记录")
    @GetMapping("/mine")
    public Result<List<CardRefundVO>> mine() {
        Long memberId = UserContext.requireMemberId();
        return Result.ok(CardRefundAppController.scopeOf(() -> cardRefundService.myApplications(memberId)));
    }

    /** 复用钱包会员端的「品牌全域视角」包装，绕开管理端门店数据权限 */
    static <T> T scopeOf(java.util.function.Supplier<T> action) {
        return WalletAppController.inBrandScope(action);
    }
}
