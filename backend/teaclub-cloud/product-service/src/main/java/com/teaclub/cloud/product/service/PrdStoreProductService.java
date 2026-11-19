package com.teaclub.cloud.product.service;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.product.dto.StoreShelfRequest;
import com.teaclub.cloud.product.dto.StoreSkuPriceRequest;
import com.teaclub.cloud.product.vo.StoreSpuVO;

import java.util.List;

public interface PrdStoreProductService {

    /** 门店商品分页（storeId 必填，后端校验数据权限）。 */
    PageResult<StoreSpuVO> page(Long storeId, String keyword, Integer pageNo, Integer pageSize);

    /** 门店商品详情。 */
    StoreSpuVO detail(Long storeId, Long spuId);

    /** 将商品上架到下架到门店。 */
    void shelf(Long storeId, Long spuId, StoreShelfRequest request);

    /** 批量上架商品到门店。 */
    void batchShelf(Long storeId, List<Long> spuIds, Integer isOnSale);

    /** 设置门店 SKU 售价（校验最低保护价）。 */
    void setSkuPrice(Long storeId, Long skuId, StoreSkuPriceRequest request);

    /** 从门店移除商品。 */
    void remove(Long storeId, Long spuId);
}
