package com.teaclub.cloud.product.controller;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.product.entity.PrdSku;
import com.teaclub.cloud.product.entity.PrdSpu;
import com.teaclub.cloud.product.entity.PrdStoreSku;
import com.teaclub.cloud.product.entity.PrdStoreSpu;
import com.teaclub.cloud.product.mapper.PrdSkuMapper;
import com.teaclub.cloud.product.mapper.PrdSpuMapper;
import com.teaclub.cloud.product.mapper.PrdStoreSkuMapper;
import com.teaclub.cloud.product.mapper.PrdStoreSpuMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * product-service 内部接口（仅供其他微服务调用）。
 *
 * <p>访问控制：由 `InternalTokenFilter` 校验 `X-Internal-Token`。
 *
 * <p><b>订单服务的依赖</b>：订单创建时需要按门店取商品价格并写快照。
 * 这里提供"门店有效价 = 门店差异化价（若有）否则 SKU 基准价"的查询，
 * 订单服务据此计算金额并<b>写入自己的快照字段</b>（order_db），
 * 之后商品调价不影响历史订单。
 */
@Tag(name = "商品内部接口", description = "服务间调用专用，需携带内部凭证")
@RestController
@RequestMapping("/api/product/internal")
@RequiredArgsConstructor
@Slf4j
public class ProductInternalController {

    private final PrdSkuMapper skuMapper;
    private final PrdSpuMapper spuMapper;
    private final PrdStoreSkuMapper storeSkuMapper;
    private final PrdStoreSpuMapper storeSpuMapper;

    @Operation(summary = "查询门店下商品的最终有效价")
    @GetMapping("/store-prices")
    public Result<Map<Long, Long>> storePrices(@RequestParam Long brandId,
                                                @RequestParam Long storeId,
                                                @RequestParam List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Result.ok(Collections.emptyMap());
        }
        List<PrdSku> skus = skuMapper.selectBatchIds(skuIds);
        Map<Long, Long> basePrices = skus.stream()
                .collect(Collectors.toMap(PrdSku::getId, PrdSku::getPrice, (a, b) -> a));

        // 门店差异化价优先
        List<PrdStoreSku> storeSkus = storeSkuMapper.selectList(
                Wrappers.<PrdStoreSku>lambdaQuery()
                        .eq(PrdStoreSku::getBrandId, brandId)
                        .eq(PrdStoreSku::getStoreId, storeId)
                        .in(PrdStoreSku::getSkuId, skuIds)
                        .eq(PrdStoreSku::getIsOnSale, 1));
        for (PrdStoreSku ss : storeSkus) {
            if (ss.getStorePrice() != null) {
                basePrices.put(ss.getSkuId(), ss.getStorePrice());
            }
        }
        log.info("查询门店有效价: brandId={} storeId={} skus={} -> {}",
                brandId, storeId, skuIds.size(), basePrices.size());
        return Result.ok(basePrices);
    }

    @Operation(summary = "查询算价所需的完整商品信息（含门店上架状态）")
    @GetMapping("/pricing-data")
    public Result<Map<String, Object>> pricingData(@RequestParam Long brandId,
                                                   @RequestParam Long storeId,
                                                   @RequestParam List<Long> skuIds) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (skuIds == null || skuIds.isEmpty()) {
            result.put("skus", Collections.emptyList());
            result.put("storeOnSaleSpuIds", Collections.emptyList());
            result.put("storeOffSaleSkuIds", Collections.emptyList());
            return Result.ok(result);
        }

        List<PrdSku> skus = skuMapper.selectBatchIds(skuIds);
        List<Long> spuIds = skus.stream().map(PrdSku::getSpuId).distinct().toList();
        Map<Long, PrdSpu> spuMap = spuIds.isEmpty() ? Map.of()
                : spuMapper.selectBatchIds(spuIds).stream()
                    .collect(Collectors.toMap(PrdSpu::getId, Function.identity(), (a, b) -> a));

        // 门店上架的 SPU
        List<PrdStoreSpu> storeSpus = storeSpuMapper.selectList(
                Wrappers.<PrdStoreSpu>lambdaQuery()
                        .eq(PrdStoreSpu::getBrandId, brandId)
                        .eq(PrdStoreSpu::getStoreId, storeId)
                        .in(PrdStoreSpu::getSpuId, spuIds));
        List<Long> onSaleSpuIds = storeSpus.stream()
                .filter(s -> Integer.valueOf(1).equals(s.getIsOnSale()))
                .map(PrdStoreSpu::getSpuId)
                .collect(Collectors.toList());

        // 门店的 SKU 定价与上下架
        List<PrdStoreSku> storeSkus = storeSkuMapper.selectList(
                Wrappers.<PrdStoreSku>lambdaQuery()
                        .eq(PrdStoreSku::getBrandId, brandId)
                        .eq(PrdStoreSku::getStoreId, storeId)
                        .in(PrdStoreSku::getSkuId, skuIds));
        Map<Long, PrdStoreSku> storeSkuMap = storeSkus.stream()
                .collect(Collectors.toMap(PrdStoreSku::getSkuId, Function.identity(), (a, b) -> a));

        List<Map<String, Object>> skuList = new ArrayList<>();
        for (PrdSku sku : skus) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skuId", sku.getId());
            m.put("spuId", sku.getSpuId());
            m.put("skuCode", sku.getSkuCode());
            m.put("skuName", sku.getSkuName());
            m.put("specJson", sku.getSpecJson());
            m.put("basePrice", sku.getPrice());
            m.put("skuStatus", sku.getStatus());

            PrdSpu spu = spuMap.get(sku.getSpuId());
            if (spu != null) {
                m.put("spuName", spu.getSpuName());
                m.put("spuCode", spu.getSpuCode());
                m.put("spuStatus", spu.getStatus());
                m.put("mainImage", spu.getMainImage());
                m.put("unit", spu.getUnit());
            }

            PrdStoreSku ss = storeSkuMap.get(sku.getId());
            if (ss != null) {
                m.put("storePrice", ss.getStorePrice());
                m.put("storeSkuOnSale", Integer.valueOf(1).equals(ss.getIsOnSale()));
            } else {
                m.put("storePrice", null);
                m.put("storeSkuOnSale", null);
            }
            skuList.add(m);
        }

        result.put("skus", skuList);
        result.put("storeOnSaleSpuIds", onSaleSpuIds);
        log.info("查询算价数据: brandId={} storeId={} skus={} onSaleSpus={}",
                brandId, storeId, skuList.size(), onSaleSpuIds.size());
        return Result.ok(result);
    }

    @Operation(summary = "查询 SKU 基础信息（含名称与规格快照）")
    @GetMapping("/skus")
    public Result<List<Map<String, Object>>> skus(@RequestParam List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<PrdSku> list = skuMapper.selectBatchIds(skuIds);
        Map<Long, PrdSpu> spuMap = list.stream().map(PrdSku::getSpuId).distinct().toList().isEmpty() ? Map.of()
                : spuMapper.selectBatchIds(list.stream().map(PrdSku::getSpuId).distinct().toList())
                    .stream().collect(Collectors.toMap(PrdSpu::getId, Function.identity(), (a, b) -> a));

        List<Map<String, Object>> result = list.stream().map(sku -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skuId", sku.getId());
            m.put("spuId", sku.getSpuId());
            m.put("skuCode", sku.getSkuCode());
            m.put("skuName", sku.getSkuName());
            m.put("specJson", sku.getSpecJson());
            m.put("price", sku.getPrice());
            m.put("status", sku.getStatus());
            PrdSpu spu = spuMap.get(sku.getSpuId());
            if (spu != null) {
                m.put("spuName", spu.getSpuName());
                m.put("spuCode", spu.getSpuCode());
                m.put("mainImage", spu.getMainImage());
                m.put("unit", spu.getUnit());
            }
            return m;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    @Operation(summary = "查询门店在售商品（SPU 维度）")
    @GetMapping("/store-spus")
    public Result<List<Long>> storeSpuIds(@RequestParam Long brandId,
                                          @RequestParam Long storeId) {
        List<PrdStoreSpu> list = storeSpuMapper.selectList(
                Wrappers.<PrdStoreSpu>lambdaQuery()
                        .eq(PrdStoreSpu::getBrandId, brandId)
                        .eq(PrdStoreSpu::getStoreId, storeId));
        return Result.ok(list.stream().map(PrdStoreSpu::getSpuId).distinct().collect(Collectors.toList()));
    }
}
