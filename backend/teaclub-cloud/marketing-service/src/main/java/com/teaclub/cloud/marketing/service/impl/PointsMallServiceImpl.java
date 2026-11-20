package com.teaclub.cloud.marketing.service.impl;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.core.util.BizNoGenerator;
import com.teaclub.cloud.marketing.entity.MktPointsExchange;
import com.teaclub.cloud.marketing.entity.MktPointsGoods;
import com.teaclub.cloud.marketing.mapper.MktPointsExchangeMapper;
import com.teaclub.cloud.marketing.mapper.MktPointsGoodsMapper;
import com.teaclub.cloud.marketing.service.CouponService;
import com.teaclub.cloud.marketing.service.PointsMallService;
import com.teaclub.cloud.marketing.service.PointsService;
import com.teaclub.cloud.marketing.vo.CouponVO;
import com.teaclub.cloud.marketing.vo.ExchangeResultVO;
import com.teaclub.cloud.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsMallServiceImpl implements PointsMallService {

    private final MktPointsGoodsMapper goodsMapper;
    private final MktPointsExchangeMapper exchangeMapper;
    private final PointsService pointsService;
    private final CouponService couponService;

    // ==================== 管理端 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createGoods(MktPointsGoods goods) {
        validateGoods(goods);
        goods.setBrandId(brandId());
        goods.setGoodsNo(BizNoGenerator.of("PG"));
        goods.setDeleted(0);
        if (goods.getStatus() == null) {
            goods.setStatus("ENABLED");
        }
        if (goods.getStock() == null) {
            goods.setStock(0);
        }
        if (goods.getExchangeLimit() == null) {
            goods.setExchangeLimit(1);
        }
        if (goods.getSort() == null) {
            goods.setSort(0);
        }
        goodsMapper.insert(goods);
        log.info("[POINTS-MALL] 新建商品 id={} name={} price={}", goods.getId(), goods.getGoodsName(), goods.getPointsPrice());
        return goods.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGoods(Long id, MktPointsGoods goods) {
        MktPointsGoods exist = goodsMapper.selectById(id);
        if (exist == null || exist.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        validateGoods(goods);
        goods.setId(id);
        goods.setBrandId(exist.getBrandId());
        goods.setGoodsNo(exist.getGoodsNo());
        goodsMapper.updateById(goods);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGoods(Long id) {
        MktPointsGoods exist = goodsMapper.selectById(id);
        if (exist == null || exist.getDeleted() == 1) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        MktPointsGoods patch = new MktPointsGoods();
        patch.setId(id);
        patch.setDeleted(1);
        patch.setStatus("DISABLED");
        goodsMapper.updateById(patch);
    }

    @Override
    public List<MktPointsGoods> listAll(String status) {
        var w = com.baomidou.mybatisplus.core.toolkit.Wrappers.<MktPointsGoods>lambdaQuery()
                .eq(MktPointsGoods::getBrandId, brandId())
                .eq(MktPointsGoods::getDeleted, 0)
                .orderByAsc(MktPointsGoods::getSort)
                .orderByDesc(MktPointsGoods::getId);
        if (status != null && !status.isBlank()) {
            w.eq(MktPointsGoods::getStatus, status);
        }
        return goodsMapper.selectList(w);
    }

    // ==================== 会员端 ====================

    @Override
    public List<MktPointsGoods> listAvailable(Long memberId) {
        return listAll("ENABLED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExchangeResultVO exchange(Long goodsId, Long memberId) {
        MktPointsGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null || goods.getDeleted() == 1 || !"ENABLED".equals(goods.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "商品不存在或已下架");
        }
        int limit = goods.getExchangeLimit() == null ? 1 : goods.getExchangeLimit();
        if (limit > 0) {
            int done = exchangeMapper.countByMember(goodsId, memberId);
            if (done >= limit) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "该商品每人限兑 " + limit + " 次，您已兑换 " + done + " 次");
            }
        }

        long cost = goods.getPointsPrice() == null ? 0L : goods.getPointsPrice();
        if (cost <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "商品积分价格异常");
        }

        // 1. 扣库存（条件 UPDATE，防超卖）
        if (goodsMapper.tryDeductStock(goodsId, 1) == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "商品库存不足");
        }

        // 2. 扣积分（条件 UPDATE，余额不足会抛异常并回滚库存）
        String exchangeNo = BizNoGenerator.of("PX");
        boolean ok = pointsService.adjust(memberId, -cost, "EX-" + exchangeNo,
                "积分兑换: " + goods.getGoodsName());
        if (!ok) {
            throw new BizException(ResultCode.BAD_REQUEST, "积分扣减失败");
        }

        // 3. 写兑换记录
        MktPointsExchange ex = new MktPointsExchange();
        ex.setBrandId(goods.getBrandId());
        ex.setExchangeNo(exchangeNo);
        ex.setMemberId(memberId);
        ex.setGoodsId(goodsId);
        ex.setGoodsName(goods.getGoodsName());
        ex.setPointsCost(cost);
        ex.setStatus("SUCCESS");
        exchangeMapper.insert(ex);

        // 4. 券类商品：发放券
        String couponNo = null;
        if ("COUPON".equals(goods.getGoodsType()) && goods.getTemplateId() != null) {
            try {
                CouponVO c = couponService.grant(goods.getTemplateId(), memberId, "ACTIVITY");
                couponNo = c == null ? null : c.getCouponNo();
                MktPointsExchange patch = new MktPointsExchange();
                patch.setId(ex.getId());
                patch.setCouponId(c == null ? null : c.getId());
                exchangeMapper.updateById(patch);
            } catch (Exception e) {
                log.error("[POINTS-MALL] 兑换券发放失败 exchangeNo={} err={}", exchangeNo, e.getMessage());
                throw new BizException(ResultCode.BAD_REQUEST, "券发放失败，兑换已回滚");
            }
        }

        ExchangeResultVO vo = new ExchangeResultVO();
        vo.setExchangeNo(exchangeNo);
        vo.setGoodsName(goods.getGoodsName());
        vo.setPointsCost(cost);
        vo.setCouponNo(couponNo);
        vo.setCreateTime(ex.getCreateTime());
        vo.setRemainPoints(pointsService.account(memberId).getTotalPoints());
        log.info("[POINTS-MALL] 兑换成功 memberId={} goods={} cost={} exchangeNo={}",
                memberId, goods.getGoodsName(), cost, exchangeNo);
        return vo;
    }

    @Override
    public List<ExchangeResultVO> myExchanges(Long memberId, int limit) {
        var w = com.baomidou.mybatisplus.core.toolkit.Wrappers.<MktPointsExchange>lambdaQuery()
                .eq(MktPointsExchange::getMemberId, memberId)
                .orderByDesc(MktPointsExchange::getId)
                .last("LIMIT " + (limit <= 0 ? 50 : Math.min(limit, 200)));
        return exchangeMapper.selectList(w).stream().map(e -> {
            ExchangeResultVO vo = new ExchangeResultVO();
            vo.setExchangeNo(e.getExchangeNo());
            vo.setGoodsName(e.getGoodsName());
            vo.setPointsCost(e.getPointsCost());
            vo.setCreateTime(e.getCreateTime());
            return vo;
        }).toList();
    }

    private void validateGoods(MktPointsGoods g) {
        if (g.getGoodsName() == null || g.getGoodsName().isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "商品名称不能为空");
        }
        if (g.getPointsPrice() == null || g.getPointsPrice() <= 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "积分价格必须大于 0");
        }
        String type = g.getGoodsType();
        if (type == null || !List.of("COUPON", "PHYSICAL", "POINTS_PACK").contains(type)) {
            throw new BizException(ResultCode.PARAM_INVALID, "商品类型只能是 COUPON/PHYSICAL/POINTS_PACK");
        }
        if ("COUPON".equals(type) && g.getTemplateId() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "券类商品必须指定券模板");
        }
    }

    private Long brandId() {
        Long b = UserContext.currentBrandIdOrNull();
        return b == null ? 1L : b;
    }
}
