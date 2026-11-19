package com.teaclub.cloud.order.controller;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.order.dto.OrderCreateRequest;
import com.teaclub.cloud.order.dto.OrderQuery;
import com.teaclub.cloud.order.dto.OrderQuoteRequest;
import com.teaclub.cloud.order.entity.OmsOrder;
import com.teaclub.cloud.order.mapper.OmsOrderMapper;
import com.teaclub.cloud.order.service.OrderService;
import com.teaclub.cloud.order.vo.OrderQuoteVO;
import com.teaclub.cloud.order.vo.OrderVO;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import java.util.function.Supplier;

/**
 * 会员端订单（微信小程序，docs/33 §3.4）。
 *
 * <p><b>身份</b>：一律取 {@link UserContext#requireMemberId()}；
 * 请求体里的 memberId 一律忽略并被令牌覆盖。
 *
 * <p><b>越权防护</b>：列表强制按本人 memberId 过滤；详情/取消/钱包支付
 * 先按订单号取单并校验 {@code order.memberId == 令牌 memberId}，否则 403。
 *
 * <p><b>复用</b>：下单、试算、余额支付全部复用 {@link OrderService}，
 * 不重写金额计算、Saga 补偿与 ORDER_PAID 事件（余额支付事件由 payByBalance 唯一发出）。
 */
@Tag(name = "会员端订单", description = "小程序试算、下单、我的订单、取消与钱包支付")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderAppController {

    private final OrderService orderService;
    private final OmsOrderMapper orderMapper;

    @Operation(summary = "订单金额试算（会员端，memberId 取令牌）")
    @PostMapping("/quote")
    public Result<OrderQuoteVO> quote(@Valid @RequestBody OrderQuoteRequest request) {
        Long memberId = UserContext.requireMemberId();
        request.setMemberId(memberId);
        return Result.ok(inBrandScope(() -> orderService.quote(request)));
    }

    @Operation(summary = "创建订单（memberId 由令牌决定，忽略请求体）")
    @PostMapping("/orders")
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateRequest request) {
        Long memberId = UserContext.requireMemberId();
        request.setMemberId(memberId);
        request.setOrderSource("MINIAPP");
        return Result.ok(inBrandScope(() -> orderService.create(request)));
    }

    @Operation(summary = "我的订单分页")
    @GetMapping("/orders")
    public Result<PageResult<OrderVO>> page(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) Integer pageNo,
                                            @RequestParam(required = false) Integer pageSize) {
        Long memberId = UserContext.requireMemberId();

        OrderQuery query = new OrderQuery();
        if (pageNo != null) {
            query.setPageNo(pageNo);
        }
        if (pageSize != null) {
            query.setPageSize(pageSize);
        }
        query.setStatus(status);
        // 会员维度由令牌决定，忽略任何客户端过滤条件
        query.setMemberId(memberId);
        return Result.ok(inBrandScope(() -> orderService.page(query)));
    }

    @Operation(summary = "订单详情（校验归属，非本人 403）")
    @GetMapping("/orders/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        Long memberId = UserContext.requireMemberId();
        requireOwned(orderNo, memberId);
        return Result.ok(inBrandScope(() -> orderService.detail(orderNo)));
    }

    @Operation(summary = "取消订单（仅未支付可取消）")
    @PostMapping("/orders/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo,
                               @RequestBody(required = false) Map<String, String> body) {
        Long memberId = UserContext.requireMemberId();
        requireOwned(orderNo, memberId);

        String reason = body == null ? null : body.get("reason");
        String cancelReason = StringUtils.hasText(reason) ? reason : "会员取消";
        // 状态机（仅 UNPAID 可取消）由 OrderService#cancel 统一裁决
        inBrandScope(() -> {
            orderService.cancel(orderNo, cancelReason);
            return null;
        });
        return Result.ok();
    }

    @Operation(summary = "钱包支付（复用 payByBalance，ORDER_PAID 事件不重复发）")
    @PostMapping("/orders/{orderNo}/pay-wallet")
    public Result<OrderVO> payByWallet(@PathVariable String orderNo) {
        Long memberId = UserContext.requireMemberId();
        requireOwned(orderNo, memberId);
        // payByBalance 内部已发 ORDER_PAID 事件（含 Saga 补偿），会员端不再额外发事件
        return Result.ok(inBrandScope(() -> orderService.payByBalance(orderNo)));
    }

    /** 按订单号取单并校验归属；非本人一律 403，避免越权查/改他人订单。 */
    private OmsOrder requireOwned(String orderNo, Long memberId) {
        OmsOrder order = orderMapper.selectByNo(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getMemberId() == null || !memberId.equals(order.getMemberId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该订单");
        }
        return order;
    }

    /**
     * 以「品牌全域」视角执行既有 service 调用，结束后恢复原上下文。
     *
     * <p><b>为什么需要</b>：order-service 已迁移的 service 用 {@code StoreScope}
     * （管理端门店数据权限）做鉴权；会员令牌只带 X-Member-Id，没有门店授权集合，
     * 直接调用会被判成「没有该门店的数据权限」。
     * 会员端的真实数据边界是<b>本人数据</b>，归属校验由 {@link #requireOwned} 显式完成，
     * 因此这里只放开门店范围，不重写任何下单/支付/取消业务逻辑。
     */
    private static <T> T inBrandScope(Supplier<T> action) {
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
