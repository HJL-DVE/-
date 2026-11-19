package com.teaclub.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.common.api.ResultCode;
import com.teaclub.common.exception.BizException;
import com.teaclub.member.entity.MemMember;
import com.teaclub.member.entity.MemMemberLevel;
import com.teaclub.member.mapper.MemMemberLevelMapper;
import com.teaclub.member.mapper.MemMemberMapper;
import com.teaclub.order.dto.OrderItemRequest;
import com.teaclub.order.vo.OrderItemVO;
import com.teaclub.order.vo.OrderQuoteVO;
import com.teaclub.product.entity.PrdSku;
import com.teaclub.product.entity.PrdSpu;
import com.teaclub.product.entity.PrdStoreSku;
import com.teaclub.product.entity.PrdStoreSpu;
import com.teaclub.product.mapper.PrdSkuMapper;
import com.teaclub.product.mapper.PrdSpuMapper;
import com.teaclub.product.mapper.PrdStoreSkuMapper;
import com.teaclub.product.mapper.PrdStoreSpuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单金额计算器 —— <b>服务端唯一算价入口</b>。
 *
 * <p>价格来源优先级：
 * <ol>
 *   <li>门店差异化定价 {@code prd_store_sku.store_price}（存在且上架时）</li>
 *   <li>回退到总部建议价 {@code prd_sku.price}</li>
 * </ol>
 *
 * <p><b>绝不接受客户端传入的任何金额</b>。请求体里根本没有价格字段。
 *
 * <p>算价顺序（与 docs/04 §4.2 一致）：
 * <pre>
 *   totalAmount      = Σ(门店现价 × 数量)
 *   levelDiscount    = round((total - activity) × (10000 - discountRate) / 10000)
 *   couponDiscount   = min(券面额, total - activity - level)
 *   payableAmount    = total - activity - level - coupon
 *   断言 payableAmount >= 0，否则截断为 0
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAmountCalculator {

    private final PrdStoreSkuMapper storeSkuMapper;
    private final PrdStoreSpuMapper storeSpuMapper;
    private final PrdSkuMapper skuMapper;
    private final PrdSpuMapper spuMapper;
    private final MemMemberMapper memberMapper;
    private final MemMemberLevelMapper levelMapper;

    /**
     * 计算订单金额。
     *
     * @param storeId         门店 ID
     * @param memberId        会员 ID（可为空 = 散客，无折扣）
     * @param items           商品项（只含 skuId + quantity）
     * @param couponDiscount  券可抵扣金额（由 CouponService 校验后传入，本期传 0）
     */
    public OrderQuoteVO calculate(Long storeId, Long memberId,
                                  List<OrderItemRequest> items, long couponDiscount) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "订单商品不能为空");
        }

        List<OrderItemVO> itemVOs = new ArrayList<>();
        long totalAmount = 0L;

        for (OrderItemRequest req : items) {
            PrdSku sku = skuMapper.selectById(req.getSkuId());
            if (sku == null) {
                throw new BizException(ResultCode.STORE_SKU_NOT_FOUND, "商品规格不存在: " + req.getSkuId());
            }
            PrdSpu spu = spuMapper.selectById(sku.getSpuId());
            if (spu == null) {
                throw new BizException(ResultCode.SPU_NOT_FOUND);
            }
            if (!"ON_SALE".equals(spu.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST, "商品已下架: " + spu.getSpuName());
            }

            // 门店必须已上架该商品
            PrdStoreSpu storeSpu = storeSpuMapper.selectOne(Wrappers.<PrdStoreSpu>lambdaQuery()
                    .eq(PrdStoreSpu::getStoreId, storeId)
                    .eq(PrdStoreSpu::getSpuId, spu.getId()));
            if (storeSpu == null || !Integer.valueOf(1).equals(storeSpu.getIsOnSale())) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "该门店未上架此商品: " + spu.getSpuName());
            }

            // 取价：门店价优先，否则总部价
            PrdStoreSku storeSku = storeSkuMapper.selectOne(Wrappers.<PrdStoreSku>lambdaQuery()
                    .eq(PrdStoreSku::getStoreId, storeId)
                    .eq(PrdStoreSku::getSkuId, sku.getId()));
            long unitPrice;
            if (storeSku != null) {
                if (!Integer.valueOf(1).equals(storeSku.getIsOnSale())) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "该规格在门店已停售: " + sku.getSkuName());
                }
                unitPrice = storeSku.getStorePrice();
            } else {
                unitPrice = sku.getPrice();
            }
            if (unitPrice <= 0) {
                throw new BizException(ResultCode.PRICE_INVALID, "商品价格异常: " + sku.getSkuName());
            }

            long lineTotal = BigDecimal.valueOf(unitPrice)
                    .multiply(BigDecimal.valueOf(req.getQuantity()))
                    .longValueExact();
            totalAmount += lineTotal;

            OrderItemVO vo = new OrderItemVO();
            vo.setSpuId(spu.getId());
            vo.setSkuId(sku.getId());
            vo.setSpuName(spu.getSpuName());
            vo.setSkuName(sku.getSkuName());
            vo.setSpecJson(sku.getSpecJson());
            vo.setImageUrl(sku.getImageUrl() != null ? sku.getImageUrl() : spu.getMainImage());
            vo.setUnitPrice(unitPrice);
            vo.setQuantity(req.getQuantity());
            vo.setTotalAmount(lineTotal);
            vo.setDiscountAmount(0L);
            vo.setActualAmount(lineTotal);
            itemVOs.add(vo);
        }

        // ===== 会员等级折扣 =====
        long activityDiscount = 0L;   // 活动优惠本期为 0（营销模块待开发）
        long levelDiscount = 0L;
        Integer discountRate = 10000;
        String levelName = null;

        if (memberId != null) {
            MemMember member = memberMapper.selectById(memberId);
            if (member == null) {
                throw new BizException(ResultCode.MEMBER_NOT_FOUND);
            }
            if (!"NORMAL".equals(member.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST, "会员已被冻结，无法下单");
            }
            if (member.getLevelId() != null) {
                MemMemberLevel level = levelMapper.selectById(member.getLevelId());
                if (level != null && level.getDiscountRate() != null) {
                    discountRate = level.getDiscountRate();
                    levelName = level.getLevelName();
                    long base = totalAmount - activityDiscount;
                    levelDiscount = BigDecimal.valueOf(base)
                            .multiply(BigDecimal.valueOf(10000L - discountRate))
                            .divide(BigDecimal.valueOf(10000L), 0, RoundingMode.HALF_UP)
                            .longValueExact();
                }
            }
        }

        long afterLevel = totalAmount - activityDiscount - levelDiscount;
        if (afterLevel < 0) {
            afterLevel = 0;
        }

        // ===== 券优惠（不得超过剩余应付） =====
        long actualCoupon = Math.min(couponDiscount, afterLevel);
        if (actualCoupon < 0) {
            actualCoupon = 0;
        }

        long payable = afterLevel - actualCoupon;
        if (payable < 0) {
            log.warn("优惠叠加导致应付为负，已截断为 0: total={} level={} coupon={}",
                    totalAmount, levelDiscount, actualCoupon);
            payable = 0;
        }

        long totalDiscount = activityDiscount + levelDiscount + actualCoupon;

        // 分摊到行（用于行实付展示）
        allocateDiscount(itemVOs, totalDiscount, totalAmount);

        OrderQuoteVO vo = new OrderQuoteVO();
        vo.setTotalAmount(totalAmount);
        vo.setActivityDiscountAmount(activityDiscount);
        vo.setLevelDiscountAmount(levelDiscount);
        vo.setCouponAmount(actualCoupon);
        vo.setDiscountAmount(totalDiscount);
        vo.setPayableAmount(payable);
        vo.setMemberId(memberId);
        vo.setMemberLevelName(levelName);
        vo.setDiscountRate(discountRate);
        vo.setItems(itemVOs);
        return vo;
    }

    /**
     * 将总优惠按行金额比例分摊到各行，最后一行用减法兜底避免总额对不平。
     */
    private void allocateDiscount(List<OrderItemVO> items, long totalDiscount, long totalAmount) {
        if (totalDiscount <= 0 || totalAmount <= 0) {
            return;
        }
        long allocated = 0L;
        for (int i = 0; i < items.size(); i++) {
            OrderItemVO item = items.get(i);
            long share;
            if (i == items.size() - 1) {
                share = totalDiscount - allocated;
            } else {
                share = BigDecimal.valueOf(item.getTotalAmount())
                        .multiply(BigDecimal.valueOf(totalDiscount))
                        .divide(BigDecimal.valueOf(totalAmount), 0, RoundingMode.DOWN)
                        .longValueExact();
            }
            if (share < 0) {
                share = 0;
            }
            if (share > item.getTotalAmount()) {
                share = item.getTotalAmount();
            }
            item.setDiscountAmount(share);
            item.setActualAmount(item.getTotalAmount() - share);
            allocated += share;
        }
    }
}
