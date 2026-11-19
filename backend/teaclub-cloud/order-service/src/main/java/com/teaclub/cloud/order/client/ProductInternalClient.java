package com.teaclub.cloud.order.client;

import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * order-service 调用 product-service。
 *
 * <p>订单创建时按门店取商品价格，并把结果<b>快照</b>写入 order_db，
 * 之后商品调价不影响历史订单。
 */
@FeignClient(name = "product-service", path = "/api/product/internal", contextId = "orderProductInternalClient")
public interface ProductInternalClient {

    /**
     * 查询门店下的商品最终有效价。
     *
     * @return skuId -> 价格（分）
     */
    @GetMapping("/store-prices")
    Result<Map<Long, Long>> storePrices(@RequestParam("brandId") Long brandId,
                                       @RequestParam("storeId") Long storeId,
                                       @RequestParam("skuIds") List<Long> skuIds);

    /**
     * 查询算价所需的完整商品信息。
     *
     * <p>返回：
     * <ul>
     *   <li>skus —— 每个 SKU 的基础价、门店价、上下架状态、商品名与规格</li>
     *   <li>storeOnSaleSpuIds —— 该门店已上架的 SPU</li>
     * </ul>
     */
    @GetMapping("/pricing-data")
    Result<Map<String, Object>> pricingData(@RequestParam("brandId") Long brandId,
                                            @RequestParam("storeId") Long storeId,
                                            @RequestParam("skuIds") List<Long> skuIds);

    /**
     * 查询 SKU 基础信息（含名称与规格快照）。
     */
    @GetMapping("/skus")
    Result<List<Map<String, Object>>> skus(@RequestParam("skuIds") List<Long> skuIds);
}
