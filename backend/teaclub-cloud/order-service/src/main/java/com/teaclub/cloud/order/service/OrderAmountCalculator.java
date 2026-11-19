package com.teaclub.cloud.order.service;

import com.teaclub.cloud.order.client.MemberInternalClient;
import com.teaclub.cloud.order.client.ProductInternalClient;
import com.teaclub.cloud.order.dto.OrderItemRequest;
import com.teaclub.cloud.order.vo.OrderItemVO;
import com.teaclub.cloud.order.vo.OrderQuoteVO;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订单金额计算器 —— <b>服务端唯一算价入口</b>。
 *
 * <p>价格来源优先级：
 * <ol>
 *   <li>门店差异化定价（该门店有定价且上架时）</li>
 *   <li>回退到总部建议价</li>
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
 *
 * <p><b>跨服务改造</b>：原实现直接注入 product 的 4 个 Mapper 与 member 的 2 个 Mapper，
 * 拆分后分别属于 product-service 与 member-service，改为 <b>Feign 调用</b>。
 * order-service 不访问 product_db / member_db。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAmountCalculator {

    private final ProductInternalClient productClient;
    private final MemberInternalClient memberClient;

    /**
     * 计算订单金额。
     *
     * @param brandId        品牌 ID
     * @param storeId        门店 ID
     * @param memberId       会员 ID（可为空 = 散客，无折扣）
     * @param items          商品项（只含 skuId + quantity）
     * @param couponDiscount 券可抵扣金额（由 CouponService 校验后传入，本期传 0）
     */
    public OrderQuoteVO calculate(Long brandId, Long storeId, Long memberId,
                                  List<OrderItemRequest> items, long couponDiscount) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "订单商品不能为空");
        }

        // ===== 取商品与门店价（product-service）=====
        List<Long> skuIds = items.stream().map(OrderItemRequest::getSkuId).distinct().toList();
        Map<String, Object> pricing = fetchPricingData(brandId, storeId, skuIds);
        List<?> skuList = (List<?>) pricing.get("skus");
        Collection<?> onSaleSpuIds = (Collection<?>) pricing.get("storeOnSaleSpuIds");
        Set<Long> onSaleSpuIdSet = new HashSet<>();
        if (onSaleSpuIds != null) {
            for (Object o : onSaleSpuIds) {
                onSaleSpuIdSet.add(((Number) o).longValue());
            }
        }
        Map<Long, Map<String, Object>> skuMap = new java.util.HashMap<>();
        for (Object o : skuList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            skuMap.put(((Number) m.get("skuId")).longValue(), m);
        }

        List<OrderItemVO> itemVOs = new ArrayList<>();
        long totalAmount = 0L;

        for (OrderItemRequest req : items) {
            Map<String, Object> sku = skuMap.get(req.getSkuId());
            if (sku == null) {
                throw new BizException(ResultCode.STORE_SKU_NOT_FOUND, "商品规格不存在: " + req.getSkuId());
            }
            Long spuId = toLong(sku.get("spuId"));
            String spuStatus = toStr(sku.get("spuStatus"));
            String spuName = toStr(sku.get("spuName"));
            if (!"ON_SALE".equals(spuStatus)) {
                throw new BizException(ResultCode.BAD_REQUEST, "商品已下架: " + spuName);
            }

            // 门店必须已上架该商品
            if (!onSaleSpuIdSet.contains(spuId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "该门店未上架此商品: " + spuName);
            }

            // 取价：门店价优先，否则总部价
            Long storePrice = toLong(sku.get("storePrice"));
            boolean storeSkuExists = sku.get("storePrice") != null;
            if (storeSkuExists) {
                Object onSale = sku.get("storeSkuOnSale");
                if (Boolean.FALSE.equals(onSale)) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "该规格在门店已停售: " + toStr(sku.get("skuName")));
                }
            }
            long unitPrice = storeSkuExists ? storePrice : toLong(sku.get("basePrice"));
            if (unitPrice <= 0) {
                throw new BizException(ResultCode.PRICE_INVALID, "商品价格异常: " + toStr(sku.get("skuName")));
            }

            long lineTotal = BigDecimal.valueOf(unitPrice)
                    .multiply(BigDecimal.valueOf(req.getQuantity()))
                    .longValueExact();
            totalAmount += lineTotal;

            OrderItemVO vo = new OrderItemVO();
            vo.setSpuId(spuId);
            vo.setSkuId(req.getSkuId());
            vo.setSpuName(spuName);
            vo.setSkuName(toStr(sku.get("skuName")));
            vo.setSpecJson(toStr(sku.get("specJson")));
            vo.setImageUrl(toStr(sku.get("mainImage")));
            vo.setUnitPrice(unitPrice);
            vo.setQuantity(req.getQuantity());
            vo.setTotalAmount(lineTotal);
            vo.setDiscountAmount(0L);
            vo.setActualAmount(lineTotal);
            itemVOs.add(vo);
        }

        // ===== 会员等级折扣（member-service）=====
        long activityDiscount = 0L;   // 活动优惠本期为 0（营销模块待开发）
        long levelDiscount = 0L;
        Integer discountRate = 10000;
        String levelName = null;

        if (memberId != null) {
            Map<String, Object> member = fetchMember(memberId);
            if (member == null) {
                throw new BizException(ResultCode.MEMBER_NOT_FOUND);
            }
            String memberStatus = toStr(member.get("status"));
            if (!"NORMAL".equals(memberStatus)) {
                throw new BizException(ResultCode.BAD_REQUEST, "会员已被冻结，无法下单");
            }
            Long levelId = toLong(member.get("level_id"));
            if (levelId != null) {
                Map<String, Object> level = fetchMemberLevel(levelId);
                if (level != null && level.get("discount_rate") != null) {
                    discountRate = ((Number) level.get("discount_rate")).intValue();
                    levelName = toStr(level.get("level_name"));
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

    // ==================== 跨服务调用 ====================

    private Map<String, Object> fetchPricingData(Long brandId, Long storeId, List<Long> skuIds) {
        var resp = productClient.pricingData(brandId, storeId, skuIds);
        if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE,
                    "商品服务不可用，无法计算订单金额");
        }
        return resp.getData();
    }

    private Map<String, Object> fetchMember(Long memberId) {
        var resp = memberClient.userInfo(memberId);
        if (resp == null || resp.getCode() != 0) {
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE,
                    "会员服务不可用，无法计算订单金额");
        }
        return resp.getData();
    }

    private Map<String, Object> fetchMemberLevel(Long levelId) {
        var resp = memberClient.levelInfo(levelId);
        if (resp == null || resp.getCode() != 0) {
            return null;
        }
        return resp.getData();
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static String toStr(Object v) {
        return v == null ? null : String.valueOf(v);
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
