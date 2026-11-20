package com.teaclub.cloud.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "发起支付请求")
public class PayCreateRequest {

    @NotBlank(message = "业务单号不能为空")
    private String bizNo;

    @NotBlank(message = "业务类型不能为空")
    @Pattern(regexp = "^(ORDER|RECHARGE)$", message = "业务类型只能是 ORDER 或 RECHARGE")
    private String bizType;

    /**
     * 支付渠道：WECHAT_NATIVE / WECHAT_JSAPI / MOCK_NATIVE / MOCK。
     *
     * <p><b>不设默认值</b>。历史写法在此给了 {@code = "WECHAT_JSAPI"}，
     * 导致「不传 channel」与「显式传 JSAPI」无法区分，
     * 桌面收银台（需要 Native 二维码）永远拿不到 codeUrl。
     * 现在由调用端按场景决定默认值：
     * <ul>
     *   <li>管理端 {@code /api/admin/payment/create}（桌面收银台）→ Native</li>
     *   <li>会员端 {@code /api/payment/create}（小程序）→ JSAPI</li>
     * </ul>
     */
    @Schema(description = "支付渠道：WECHAT_NATIVE / WECHAT_JSAPI；不传则由调用端按场景取默认值")
    private String channel;

    @Schema(description = "小程序 openid（JSAPI 必填）")
    private String openid;

    private String clientIp;
}
