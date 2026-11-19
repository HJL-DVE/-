package com.teaclub.cloud.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 余额支付 Saga 状态机记录。
 *
 * <p>解决「钱已扣但订单未支付」的孤儿扣款：扣款前落库 DEDUCTING，
 * 扣款成功置 DEDUCTED，订单更新成功置 ORDER_PAID/COMPLETED。
 * 若订单更新失败则立即补偿回冲并置 COMPENSATED；
 * 进程崩溃由定时补偿任务扫描 RUNNING/DEDUCTED 超时记录后回冲。
 */
@Data
@TableName("oms_payment_saga")
public class OmsPaymentSaga {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sagaId;

    private String orderNo;

    /** DEDUCTING / DEDUCTED / ORDER_PAID / COMPENSATED */
    private String step;

    /** RUNNING / COMPLETED / COMPENSATED / FAILED */
    private String status;

    private Long principalAmount;

    private Long giftAmount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
