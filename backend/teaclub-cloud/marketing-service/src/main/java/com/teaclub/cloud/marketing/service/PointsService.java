package com.teaclub.cloud.marketing.service;

import com.teaclub.cloud.marketing.vo.PointsAccountVO;
import com.teaclub.cloud.marketing.vo.PointsRuleVO;
import com.teaclub.cloud.marketing.vo.PointsTransactionVO;
import com.teaclub.cloud.marketing.vo.SignInResultVO;

import java.util.List;

/**
 * 积分服务。
 *
 * <p><b>事实来源</b>：业务账本唯一的来源就是 {@code mkt_points_account}；
 * member_db 的 {@code mem_member.points} 只是快照，仅供会员列表展示用，
 * 任何业务判断都不读它。
 *
 * <p><b>幂等</b>：{@code mkt_points_transaction} 上的
 * {@code uk_points_biz(brand_id, biz_type, biz_no)} 是最终防线。
 * 同一个订单号重复投递，第二次 INSERT 会直接抛唯一键冲突，
 * 服务捕获后返回 false（表示"已处理过"），不会重复发积分。
 *
 * <p><b>退款冲正</b>：严格按原始发放流水冲正 ——
 * 先查 {@code ORDER_EARN + 订单号} 那条流水，取其 {@code changePoints}
 * 作为冲正金额，<b>不重新计算</b>（重新计算会因规则变更导致金额对不上）。
 */
public interface PointsService {

    /** 开通/查询积分账户。 */
    PointsAccountVO account(Long memberId);

    /** 订单支付成功 → 发积分。返回 false 表示该订单已发过（幂等）。 */
    boolean earnByOrder(Long brandId, Long memberId, Long storeId, String orderNo,
                        Long paidAmount, String traceId);

    /**
     * 带等级倍率的发放（levelId 来自事件载荷/会员档案）。
     * levelId 为 null 时等同无倍率（1.0）。
     */
    boolean earnByOrderWithLevel(Long brandId, Long memberId, Long storeId, String orderNo,
                                 Long paidAmount, Long levelId, String traceId);

    /**
     * 订单退款 → 按原流水冲正。
     *
     * <p>{@code memberId} 允许为空：REFUND_SUCCEEDED 事件的载荷里<b>没有 memberId</b>，
     * 此时按订单号反查原发放流水，从流水里拿到会员。
     *
     * @return false 表示无积分可冲 / 已冲正过 / 余额为 0
     */
    boolean reverseByRefund(Long brandId, Long memberId, String orderNo,
                            Long refundAmount, String traceId);

    /** 签到。重复签到返回 firstTime=false。 */
    SignInResultVO signIn(Long memberId);

    /** 我的积分流水。 */
    List<PointsTransactionVO> transactions(Long memberId, String bizType, int limit);

    /** 管理端：读取当前品牌生效的积分规则（真实表数据）。 */
    List<PointsRuleVO> listRules();

    /** 人工调整（管理端）。正数增加，负数扣减。 */
    boolean adjust(Long memberId, long points, String bizNo, String remark);

    /** 过期处理（定时任务）。返回处理的批次数。 */
    int expireBatches(int limit);

    /** 把积分总额回写到 member-service（快照）。失败不影响主流程。 */
    void syncSnapshot(Long memberId, long points);
}
