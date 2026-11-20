package com.teaclub.cloud.marketing.service;

import com.teaclub.cloud.marketing.vo.CouponQuoteVO;
import com.teaclub.cloud.marketing.vo.CouponVO;

import java.util.List;

/**
 * 会员优惠券服务。
 *
 * <p><b>幂等设计（三层）</b>：
 * <ol>
 *   <li>Redis {@code mkt:coupon:lock:{couponNo}:{orderNo}} 快速拦截</li>
 *   <li>数据库条件 UPDATE（{@code WHERE status = 前态}），靠受影响行数判定</li>
 *   <li>唯一索引 / 日志表兜底</li>
 * </ol>
 */
public interface CouponService {

    /** 领取模板券。重复领取受 per_member_limit 与 Redis 锁双重约束。 */
    List<CouponVO> receive(Long templateId, Long memberId, int qty);

    /** 会员券列表。status 为空则返回全部。 */
    List<CouponVO> listMine(Long memberId, String status, int limit);

    /** 会员可用券（未使用 + 有效期内）。 */
    List<CouponVO> listUsable(Long memberId);

    /**
     * 试算：给定订单金额与商品，判断该券能抵多少。
     *
     * <p>不修改任何状态，供 order-service 在算价阶段调用。
     */
    CouponQuoteVO quote(String couponNo, Long memberId, Long storeId,
                        long orderAmount, List<Long> spuIds);

    /**
     * 锁定券（下单占位）。
     *
     * <p>已锁定且订单号相同则视为幂等成功（重复调用不报错）。
     */
    boolean lock(String couponNo, Long memberId, String orderNo);

    /**
     * 核销券（支付成功）。
     *
     * <p>已经是 USED 且订单号一致 → 幂等返回 true；
     * 其他状态不匹配 → 返回 false。
     */
    boolean use(String couponNo, String orderNo, Long discountAmount);

    /**
     * 释放券（订单取消）。
     *
     * <p>按订单号释放，找不到锁定中的券则返回 false（不算异常）。
     */
    boolean releaseByOrder(String orderNo);

    /** 过期处理（定时任务调用），返回处理条数。 */
    int expireBatch(int limit);
}
