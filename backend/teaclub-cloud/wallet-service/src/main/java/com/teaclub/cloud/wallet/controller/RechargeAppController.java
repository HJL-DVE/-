package com.teaclub.cloud.wallet.controller;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.wallet.dto.RechargeCreateRequest;
import com.teaclub.cloud.wallet.dto.RechargeOrderQuery;
import com.teaclub.cloud.wallet.service.RechargeService;
import com.teaclub.cloud.wallet.vo.RechargeOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 会员端充值订单（微信小程序，docs/33 §3.6）。
 *
 * <p><b>身份</b>：memberId 强制取自 {@link UserContext#requireMemberId()}，
 * 请求体里即便带了 memberId 也一律忽略。
 *
 * <p><b>金额</b>：由服务端按充值方案计算（{@link RechargeService#createRecharge}），
 * 本层不接收任何金额字段。
 *
 * <p><b>入账</b>：创建充值单<b>不会</b>加余额；余额只由 payment 支付成功通知
 * → {@code /api/wallet/internal/recharge-order/paid} 入账。
 */
@Tag(name = "会员端充值", description = "创建充值单、充值单状态、我的充值记录")
@RestController
@RequestMapping("/api/wallet/recharges")
@RequiredArgsConstructor
public class RechargeAppController {

    private final RechargeService rechargeService;

    @Operation(summary = "创建充值订单（金额由服务端按方案计算）")
    @PostMapping
    public Result<RechargeOrderVO> create(@RequestBody(required = false) Map<String, Object> body) {
        Long memberId = UserContext.requireMemberId();
        Long planId = toLong(body, "planId");
        if (planId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "充值方案不能为空");
        }

        // 自行组装 DTO：memberId 取令牌，绝不信任请求体
        RechargeCreateRequest request = new RechargeCreateRequest();
        request.setMemberId(memberId);
        request.setStoreId(toLong(body, "storeId"));
        request.setPlanId(planId);
        request.setCustomAmount(toLong(body, "customAmount"));
        return Result.ok(WalletAppController.inBrandScope(() -> rechargeService.createRecharge(request)));
    }

    @Operation(summary = "我的充值记录分页")
    @GetMapping
    public Result<PageResult<RechargeOrderVO>> page(@RequestParam(required = false) Integer pageNo,
                                                   @RequestParam(required = false) Integer pageSize,
                                                   @RequestParam(required = false) String status) {
        Long memberId = UserContext.requireMemberId();

        RechargeOrderQuery query = new RechargeOrderQuery();
        if (pageNo != null) {
            query.setPageNo(pageNo);
        }
        if (pageSize != null) {
            query.setPageSize(pageSize);
        }
        query.setStatus(status);
        // 会员维度由令牌决定，忽略任何客户端过滤条件
        query.setMemberId(memberId);
        return Result.ok(WalletAppController.inBrandScope(() -> rechargeService.page(query)));
    }

    @Operation(summary = "充值单详情（校验归属会员）")
    @GetMapping("/{rechargeNo}")
    public Result<RechargeOrderVO> detail(@PathVariable String rechargeNo) {
        Long memberId = UserContext.requireMemberId();
        RechargeOrderVO vo = WalletAppController.inBrandScope(() -> rechargeService.detail(rechargeNo));
        if (vo == null || vo.getMemberId() == null || !memberId.equals(vo.getMemberId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看该充值订单");
        }
        return Result.ok(vo);
    }

    private static Long toLong(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        return StringUtils.hasText(s) ? Long.valueOf(s) : null;
    }
}
