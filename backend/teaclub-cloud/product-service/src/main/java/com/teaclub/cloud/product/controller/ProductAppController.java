package com.teaclub.cloud.product.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.product.entity.PrdCategory;
import com.teaclub.cloud.product.entity.PrdSku;
import com.teaclub.cloud.product.entity.PrdSpu;
import com.teaclub.cloud.product.entity.PrdStoreSku;
import com.teaclub.cloud.product.entity.PrdStoreSpu;
import com.teaclub.cloud.product.mapper.PrdCategoryMapper;
import com.teaclub.cloud.product.mapper.PrdSkuMapper;
import com.teaclub.cloud.product.mapper.PrdSpuMapper;
import com.teaclub.cloud.product.mapper.PrdStoreSkuMapper;
import com.teaclub.cloud.product.mapper.PrdStoreSpuMapper;
import com.teaclub.cloud.product.vo.AppCategoryVO;
import com.teaclub.cloud.product.vo.AppSpuDetailVO;
import com.teaclub.cloud.product.vo.AppSpuItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会员端（微信小程序）商品浏览接口，全部免登录只读。
 *
 * <p>可售口径 = 门店在售（{@code prd_store_spu.is_on_sale=1}）+ 总部上架（{@code prd_spu.status=ON_SALE}）
 * + 规格未在门店被单独下架（{@code prd_store_sku.is_on_sale=1}，未配置视为可售）。
 *
 * <p>价格口径：门店差异化价（{@code prd_store_sku.store_price}）优先，未配置时回落总部价
 * （{@code prd_sku.price}），与 {@code ProductInternalController#storePrices} 一致；单位「分」。
 *
 * <p><b>不编造数据</b>：商品库没有销量字段，热门榜按门店排序位 → 总部排序位 → 上新时间倒序取，
 * 响应里也不返回任何销量/热度数字。
 */
@Tag(name = "会员端商品", description = "小程序商品分类、列表、详情与热门（免登录只读）")
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
@Slf4j
public class ProductAppController {

    private static final String SPU_ON_SALE = "ON_SALE";
    private static final String SKU_ENABLED = "ENABLED";
    private static final String CATEGORY_ENABLED = "ENABLED";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_HOT_LIMIT = 6;
    private static final int MAX_HOT_LIMIT = 20;

    private final PrdStoreSpuMapper storeSpuMapper;
    private final PrdStoreSkuMapper storeSkuMapper;
    private final PrdSpuMapper spuMapper;
    private final PrdSkuMapper skuMapper;
    private final PrdCategoryMapper categoryMapper;

    @Operation(summary = "门店可售分类")
    @GetMapping("/stores/{storeId}/categories")
    public Result<List<AppCategoryVO>> categories(@PathVariable("storeId") Long storeId) {
        Set<Long> categoryIds = orderedOnSaleSpus(storeId).stream()
                .map(PrdSpu::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (categoryIds.isEmpty()) {
            return Result.ok(List.of());
        }
        List<AppCategoryVO> categories = categoryMapper.selectBatchIds(categoryIds).stream()
                .filter(c -> CATEGORY_ENABLED.equals(c.getStatus()))
                .sorted(Comparator.comparingInt((PrdCategory c) -> c.getSort() == null ? 0 : c.getSort())
                        .thenComparing(PrdCategory::getId))
                .map(c -> new AppCategoryVO(c.getId(), c.getCategoryName(), c.getIconUrl(), c.getSort()))
                .collect(Collectors.toList());
        return Result.ok(categories);
    }

    @Operation(summary = "门店商品列表（分页）")
    @GetMapping("/stores/{storeId}/spus")
    public Result<PageResult<AppSpuItemVO>> spus(@PathVariable("storeId") Long storeId,
                                                 @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                 @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);

        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        List<PrdSpu> matched = orderedOnSaleSpus(storeId).stream()
                .filter(spu -> categoryId == null || categoryId.equals(spu.getCategoryId()))
                .filter(spu -> kw == null || (spu.getSpuName() != null
                        && spu.getSpuName().toLowerCase(Locale.ROOT).contains(kw)))
                .collect(Collectors.toList());

        int from = (no - 1) * size;
        if (from >= matched.size()) {
            // 页码越界不报错，但仍返回真实总数，前端分页器不会显示 0 条
            return Result.ok(new PageResult<>(List.of(), matched.size(), no, size));
        }
        int to = Math.min(from + size, matched.size());
        List<AppSpuItemVO> records = toItems(storeId, matched.subList(from, to));
        return Result.ok(new PageResult<>(records, matched.size(), no, size));
    }

    @Operation(summary = "门店商品详情")
    @GetMapping("/stores/{storeId}/spus/{spuId}")
    public Result<AppSpuDetailVO> detail(@PathVariable("storeId") Long storeId,
                                         @PathVariable("spuId") Long spuId) {
        PrdStoreSpu rel = storeSpuMapper.selectOne(Wrappers.<PrdStoreSpu>lambdaQuery()
                .eq(PrdStoreSpu::getStoreId, storeId)
                .eq(PrdStoreSpu::getSpuId, spuId)
                .eq(PrdStoreSpu::getIsOnSale, 1)
                .last("LIMIT 1"));
        if (rel == null) {
            throw new BizException(ResultCode.STORE_SKU_NOT_FOUND, "该门店未上架此商品");
        }
        PrdSpu spu = spuMapper.selectById(spuId);
        if (spu == null || !SPU_ON_SALE.equals(spu.getStatus())) {
            throw new BizException(ResultCode.SPU_NOT_FOUND);
        }

        List<PrdSku> skus = skuMapper.selectList(Wrappers.<PrdSku>lambdaQuery()
                .eq(PrdSku::getSpuId, spuId)
                .eq(PrdSku::getStatus, SKU_ENABLED)
                .orderByAsc(PrdSku::getSort)
                .orderByAsc(PrdSku::getId));
        Map<Long, PrdStoreSku> storeSkuMap = storeSkuMap(storeId,
                skus.stream().map(PrdSku::getId).collect(Collectors.toList()));

        AppSpuDetailVO.Spu spuVO = new AppSpuDetailVO.Spu();
        spuVO.setSpuId(spu.getId());
        spuVO.setSpuName(spu.getSpuName());
        spuVO.setImageUrl(spu.getMainImage());
        spuVO.setDescription(spu.getDescription());
        spuVO.setCategoryId(spu.getCategoryId());

        List<AppSpuDetailVO.Sku> skuVOs = new ArrayList<>(skus.size());
        for (PrdSku sku : skus) {
            Long price = sellablePrice(sku, storeSkuMap.get(sku.getId()));
            if (price == null) {
                // 门店显式下架的规格不对外展示
                continue;
            }
            AppSpuDetailVO.Sku skuVO = new AppSpuDetailVO.Sku();
            skuVO.setSkuId(sku.getId());
            skuVO.setSkuName(sku.getSkuName());
            skuVO.setPrice(price);
            skuVO.setSpecJson(sku.getSpecJson());
            skuVOs.add(skuVO);
        }

        AppSpuDetailVO detail = new AppSpuDetailVO();
        detail.setSpu(spuVO);
        detail.setSkus(skuVOs);
        return Result.ok(detail);
    }

    @Operation(summary = "热门商品")
    @GetMapping("/hot")
    public Result<List<AppSpuItemVO>> hot(@RequestParam("storeId") Long storeId,
                                          @RequestParam(value = "limit", defaultValue = "6") Integer limit) {
        if (storeId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "门店 ID 不能为空");
        }
        int size = limit == null || limit < 1 ? DEFAULT_HOT_LIMIT : Math.min(limit, MAX_HOT_LIMIT);
        List<PrdSpu> onSale = orderedOnSaleSpus(storeId);
        List<PrdSpu> hot = onSale.size() <= size ? onSale : onSale.subList(0, size);
        return Result.ok(toItems(storeId, hot));
    }

    // ====== 查询与装配 ======

    /**
     * 门店在售商品，按「门店排序位 → 总部排序位 → 上新时间倒序 → ID」稳定排序。
     *
     * <p>商品库无销量字段，列表与热门都复用该顺序；门店商品量级为几十到几百，
     * 因此在应用层排序分页（避免为 keyword 过滤写跨库 JOIN）。
     */
    private List<PrdSpu> orderedOnSaleSpus(Long storeId) {
        Map<Long, Integer> storeSort = new LinkedHashMap<>();
        for (PrdStoreSpu rel : onSaleRels(storeId)) {
            storeSort.putIfAbsent(rel.getSpuId(), rel.getSort() == null ? 0 : rel.getSort());
        }
        if (storeSort.isEmpty()) {
            return List.of();
        }
        List<PrdSpu> spus = spuMapper.selectBatchIds(storeSort.keySet()).stream()
                .filter(spu -> SPU_ON_SALE.equals(spu.getStatus()))
                .collect(Collectors.toList());
        spus.sort(Comparator
                .comparingInt((PrdSpu spu) -> storeSort.getOrDefault(spu.getId(), 0))
                .thenComparingInt(spu -> spu.getSort() == null ? 0 : spu.getSort())
                .thenComparing(PrdSpu::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PrdSpu::getId));
        return spus;
    }

    private List<PrdStoreSpu> onSaleRels(Long storeId) {
        return storeSpuMapper.selectList(Wrappers.<PrdStoreSpu>lambdaQuery()
                .eq(PrdStoreSpu::getStoreId, storeId)
                .eq(PrdStoreSpu::getIsOnSale, 1)
                .orderByAsc(PrdStoreSpu::getSort)
                .orderByAsc(PrdStoreSpu::getId));
    }

    /** 批量装配列表项：1 次 SKU 查询 + 1 次门店价查询 + 1 次分类查询。 */
    private List<AppSpuItemVO> toItems(Long storeId, List<PrdSpu> spus) {
        if (spus.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> minPrices = minPrices(storeId, spus);
        Map<Long, String> categoryNames = categoryNames(spus);
        List<AppSpuItemVO> items = new ArrayList<>(spus.size());
        for (PrdSpu spu : spus) {
            items.add(new AppSpuItemVO(spu.getId(), spu.getSpuName(), spu.getMainImage(),
                    minPrices.get(spu.getId()), spu.getCategoryId(), categoryNames.get(spu.getCategoryId())));
        }
        return items;
    }

    /** 每个 SPU 的最低可售价：其可售 SKU 中的最小价。 */
    private Map<Long, Long> minPrices(Long storeId, List<PrdSpu> spus) {
        List<Long> spuIds = spus.stream().map(PrdSpu::getId).collect(Collectors.toList());
        List<PrdSku> skus = skuMapper.selectList(Wrappers.<PrdSku>lambdaQuery()
                .in(PrdSku::getSpuId, spuIds)
                .eq(PrdSku::getStatus, SKU_ENABLED));
        if (skus.isEmpty()) {
            return Map.of();
        }
        Map<Long, PrdStoreSku> storeSkuMap = storeSkuMap(storeId,
                skus.stream().map(PrdSku::getId).collect(Collectors.toList()));
        Map<Long, Long> minPrices = new LinkedHashMap<>();
        for (PrdSku sku : skus) {
            Long price = sellablePrice(sku, storeSkuMap.get(sku.getId()));
            if (price == null) {
                continue;
            }
            minPrices.merge(sku.getSpuId(), price, (left, right) -> left < right ? left : right);
        }
        return minPrices;
    }

    private Map<Long, String> categoryNames(List<PrdSpu> spus) {
        Set<Long> categoryIds = spus.stream()
                .map(PrdSpu::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(categoryIds).stream()
                .filter(c -> CATEGORY_ENABLED.equals(c.getStatus()))
                .collect(Collectors.toMap(PrdCategory::getId, PrdCategory::getCategoryName, (a, b) -> a));
    }

    private Map<Long, PrdStoreSku> storeSkuMap(Long storeId, List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        return storeSkuMapper.selectList(Wrappers.<PrdStoreSku>lambdaQuery()
                        .eq(PrdStoreSku::getStoreId, storeId)
                        .in(PrdStoreSku::getSkuId, skuIds))
                .stream()
                .collect(Collectors.toMap(PrdStoreSku::getSkuId, s -> s, (a, b) -> a));
    }

    /**
     * 单个 SKU 的可售价。
     *
     * <p>门店显式下架（{@code is_on_sale=0}）返回 null（对外不可售）；
     * 门店设了价用门店价，否则回落总部价；两者都没有返回 null。
     */
    private static Long sellablePrice(PrdSku sku, PrdStoreSku storeSku) {
        if (storeSku != null) {
            if (!Integer.valueOf(1).equals(storeSku.getIsOnSale())) {
                return null;
            }
            if (storeSku.getStorePrice() != null) {
                return storeSku.getStorePrice();
            }
        }
        return sku.getPrice();
    }
}
