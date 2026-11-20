package com.teaclub.cloud.marketing.service;

import com.teaclub.cloud.marketing.entity.MktPointsGoods;
import com.teaclub.cloud.marketing.vo.ExchangeResultVO;

import java.util.List;

/**
 * 积分商城。
 *
 * <p><b>兑换的原子性</b>：扣积分与扣库存在同一事务，
 * 任一步失败整体回滚，不会出现「扣了积分没拿到货」。
 *
 * <p><b>防超兑</b>：库存用条件 UPDATE（{@code stock >= qty} 或 {@code stock = -1}），
 * 积分用条件 UPDATE（{@code total_points >= cost}），并发下都不会超卖/超扣。
 */
public interface PointsMallService {

    // ===== 管理端 =====

    Long createGoods(MktPointsGoods goods);

    void updateGoods(Long id, MktPointsGoods goods);

    void deleteGoods(Long id);

    List<MktPointsGoods> listAll(String status);

    // ===== 会员端 =====

    /** 可兑换商品列表。 */
    List<MktPointsGoods> listAvailable(Long memberId);

    /** 兑换。 */
    ExchangeResultVO exchange(Long goodsId, Long memberId);

    /** 会员兑换记录。 */
    List<ExchangeResultVO> myExchanges(Long memberId, int limit);
}
