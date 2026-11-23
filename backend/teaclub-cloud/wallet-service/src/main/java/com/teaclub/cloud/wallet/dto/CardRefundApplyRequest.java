package com.teaclub.cloud.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 会员申请退卡。
 *
 * <p>会员只需说明原因；金额由服务端按当前钱包实付余额计算，
 * <b>不接受客户端指定退款金额</b>（防止篡改）。
 */
@Data
public class CardRefundApplyRequest {

    @NotBlank(message = "请填写退卡原因")
    private String reason;
}
