package com.teaclub.cloud.payment.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.payment.client.OrderInternalClient;
import com.teaclub.cloud.payment.client.WalletInternalClient;
import com.teaclub.cloud.payment.dto.PayCreateRequest;
import com.teaclub.cloud.payment.entity.PayPaymentOrder;
import com.teaclub.cloud.payment.mapper.PayPaymentOrderMapper;
import com.teaclub.cloud.payment.service.PaymentService;
import com.teaclub.cloud.payment.vo.PayCreateVO;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 会员端支付（微信小程序，docs/33 §3.5）。
 *
 * <p><b>越权防护</b>：发起支付前先按业务单反查归属会员 ——
 * ORDER 走 order-service 内部接口、RECHARGE 走 wallet-service 内部接口，
 * 与令牌会员不一致一律 403；查询支付单同样只返回本人支付单。
 *
 * <p><b>复用</b>：真正的下单/金额校验/通知入账全部复用 {@link PaymentService}，
 * 本层不计算金额、不改支付单状态机。
 */
@Tag(name = "会员端支付", description = "小程序发起支付、支付结果轮询")
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentAppController {

    private final PaymentService paymentService;
    private final PayPaymentOrderMapper paymentMapper;
    private final OrderInternalClient orderClient;
    private final WalletInternalClient walletClient;

    @Operation(summary = "发起支付（业务单归属校验通过后复用管理端下单逻辑）")
    @PostMapping("/create")
    public Result<PayCreateVO> create(@Valid @RequestBody PayCreateRequest request) {
        Long memberId = UserContext.requireMemberId();

        Long ownerMemberId = requireBizOwnerMemberId(request.getBizType(), request.getBizNo());
        if (ownerMemberId == null || !memberId.equals(ownerMemberId)) {
            log.warn("会员越权发起支付 memberId={} bizType={} bizNo={} 归属={}",
                    memberId, request.getBizType(), request.getBizNo(), ownerMemberId);
            throw new BizException(ResultCode.FORBIDDEN, "无权为该业务单发起支付");
        }

        // 无 openid 时 JSAPI 无法调起（微信要求 openid），降级为 Native 二维码
        downgradeIfNoOpenid(request);
        return Result.ok(inBrandScope(() -> paymentService.createPaymentForMember(request)));
    }

    @Operation(summary = "支付结果轮询（只返回本人支付单）")
    @GetMapping("/orders/{paymentNo}")
    public Result<Map<String, Object>> order(@PathVariable String paymentNo) {
        Long memberId = UserContext.requireMemberId();

        PayPaymentOrder payment = paymentMapper.selectByNo(paymentNo);
        if (payment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "支付单不存在");
        }
        if (payment.getMemberId() == null || !memberId.equals(payment.getMemberId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看该支付单");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentNo", payment.getPaymentNo());
        data.put("status", payment.getStatus());
        data.put("amount", payment.getAmount());
        data.put("codeUrl", payment.getCodeUrl());
        // JSAPI 调起参数只在 create 时由渠道返回，轮询不重建（小程序复用 create 的返回即可）
        data.put("invokeParams", null);
        return Result.ok(data);
    }

    /** 按业务单反查归属会员 ID；查无此单抛 404。 */
    private Long requireBizOwnerMemberId(String bizType, String bizNo) {
        Map<String, Object> biz;
        if ("ORDER".equals(bizType)) {
            biz = fetchOrder(bizNo);
        } else if ("RECHARGE".equals(bizType)) {
            biz = fetchRechargeOrder(bizNo);
        } else {
            throw new BizException(ResultCode.PARAM_INVALID, "业务类型只能是 ORDER 或 RECHARGE");
        }
        return toLong(biz == null ? null : biz.get("member_id"));
    }

    private Map<String, Object> fetchOrder(String orderNo) {
        Result<Map<String, Object>> resp;
        try {
            resp = orderClient.order(orderNo);
        } catch (Exception e) {
            log.error("查询订单失败: orderNo={}", orderNo, e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "订单服务不可用");
        }
        if (resp == null) {
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "订单服务无响应");
        }
        if (resp.getCode() != 0) {
            throw new BizException(resp.getCode(), resp.getMessage());
        }
        if (resp.getData() == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return resp.getData();
    }

    private Map<String, Object> fetchRechargeOrder(String rechargeNo) {
        Result<Map<String, Object>> resp;
        try {
            resp = walletClient.rechargeOrder(rechargeNo);
        } catch (Exception e) {
            log.error("查询充值订单失败: rechargeNo={}", rechargeNo, e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "钱包服务不可用");
        }
        if (resp == null) {
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "钱包服务无响应");
        }
        if (resp.getCode() != 0) {
            throw new BizException(resp.getCode(), resp.getMessage());
        }
        if (resp.getData() == null) {
            throw new BizException(ResultCode.NOT_FOUND, "充值订单不存在");
        }
        return resp.getData();
    }

    /**
     * 无 openid 时的降级。
     *
     * <p>小程序走 JSAPI 必须带 openid（微信硬性要求）；没带就调不起，
     * 此时降级为 Native 二维码（返回 codeUrl，小程序用 canvas 展示）。
     * <p>默认渠道由 service 的会员端入口决定（{@code WECHAT_JSAPI}），
     * 这里只处理「不降级就会失败」的那一种情况。
     */
    private static void downgradeIfNoOpenid(PayCreateRequest request) {
        if (StringUtils.hasText(request.getOpenid())) {
            return;
        }
        String channel = request.getChannel();
        if (!StringUtils.hasText(channel) || "WECHAT_JSAPI".equals(channel)) {
            request.setChannel("WECHAT_NATIVE");
        }
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }

    /**
     * 以「品牌全域」视角执行既有 service 调用，结束后恢复原上下文。
     *
     * <p><b>为什么需要</b>：{@code PaymentServiceImpl} 用 {@code StoreScope}
     * （管理端门店数据权限）校验业务单门店；会员令牌只带 X-Member-Id，
     * 没有门店授权集合，直接调用会被判成「没有该门店的数据权限」。
     * 会员端的真实数据边界是<b>本人数据</b>，归属校验已在本控制器完成，
     * 因此这里只放开门店范围，不重写支付下单与通知链路。
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
