package com.teaclub.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.common.api.PageResult;
import com.teaclub.common.api.ResultCode;
import com.teaclub.common.context.StoreScope;
import com.teaclub.common.context.UserContext;
import com.teaclub.common.exception.BizException;
import com.teaclub.product.dto.StoreShelfRequest;
import com.teaclub.product.dto.StoreSkuPriceRequest;
import com.teaclub.product.entity.*;
import com.teaclub.product.mapper.*;
import com.teaclub.product.service.PrdStoreProductService;
import com.teaclub.product.vo.StoreSkuVO;
import com.teaclub.product.vo.StoreSpuVO;
import com.teaclub.system.entity.SysStore;
import com.teaclub.system.mapper.SysStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrdStoreProductServiceImpl implements PrdStoreProductService {

    private final PrdStoreSpuMapper storeSpuMapper;
    private final PrdStoreSkuMapper storeSkuMapper;
    private final PrdSpuMapper spuMapper;
    private final PrdSkuMapper skuMapper;
    private final SysStoreMapper sysStoreMapper;

    /**
     * 校验门店存在且当前用户有数据权限。所有门店商品操作都必须先经过这里。
     */
    private void assertStore(Long storeId) {
        if (storeId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "门店 ID 不能为空");
        }
        SysStore store = sysStoreMapper.selectById(storeId);
        if (store == null) {
            throw new BizException(ResultCode.STORE_NOT_FOUND);
        }
        StoreScope scope = StoreScope.from(UserContext.require());
        scope.assertAccessible(storeId);
    }

    @Override
    public PageResult<StoreSpuVO> page(Long storeId, String keyword, Integer pageNo, Integer pageSize) {
        assertStore(storeId);
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);

        LambdaQueryWrapper<PrdStoreSpu> wrapper = Wrappers.<PrdStoreSpu>lambdaQuery()
                .eq(PrdStoreSpu::getStoreId, storeId);
        if (StringUtils.hasText(keyword)) {
            // 先按名称过滤出 spuId，再在门店商品中过滤
            String kw = keyword.trim();
            List<Long> spuIds = spuMapper.selectList(Wrappers.<PrdSpu>lambdaQuery()
                            .like(PrdSpu::getSpuName, kw).or().like(PrdSpu::getSpuCode, kw))
                    .stream().map(PrdSpu::getId).collect(Collectors.toList());
            if (spuIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, no, size);
            }
            wrapper.in(PrdStoreSpu::getSpuId, spuIds);
        }
        wrapper.orderByAsc(PrdStoreSpu::getSort).orderByAsc(PrdStoreSpu::getId);

        Page<PrdStoreSpu> page = storeSpuMapper.selectPage(new Page<>(no, size), wrapper);
        List<StoreSpuVO> records = page.getRecords().stream()
                .map(r -> buildVO(storeId, r))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private StoreSpuVO buildVO(Long storeId, PrdStoreSpu rel) {
        PrdSpu spu = spuMapper.selectById(rel.getSpuId());
        if (spu == null) {
            return null;
        }
        StoreSpuVO vo = new StoreSpuVO();
        vo.setId(rel.getId());
        vo.setStoreId(storeId);
        vo.setSpuId(spu.getId());
        vo.setSpuCode(spu.getSpuCode());
        vo.setSpuName(spu.getSpuName());
        vo.setMainImage(spu.getMainImage());
        vo.setBasePrice(spu.getBasePrice());
        vo.setMinPrice(spu.getMinPrice());
        vo.setSpuStatus(spu.getStatus());
        vo.setIsOnSale(rel.getIsOnSale());
        vo.setSort(rel.getSort());

        List<PrdSku> skus = skuMapper.selectList(Wrappers.<PrdSku>lambdaQuery()
                .eq(PrdSku::getSpuId, spu.getId()).orderByAsc(PrdSku::getSort));
        Map<Long, PrdStoreSku> overrideMap = storeSkuMapper.selectList(Wrappers.<PrdStoreSku>lambdaQuery()
                        .eq(PrdStoreSku::getStoreId, storeId)
                        .in(PrdStoreSku::getSkuId, skus.stream().map(PrdSku::getId).collect(Collectors.toList())))
                .stream().collect(Collectors.toMap(PrdStoreSku::getSkuId, s -> s, (a, b) -> a));

        vo.setSkus(skus.stream().map(k -> {
            StoreSkuVO kv = new StoreSkuVO();
            kv.setSkuId(k.getId());
            kv.setSkuCode(k.getSkuCode());
            kv.setSkuName(k.getSkuName());
            kv.setBasePrice(k.getPrice());
            kv.setMinPrice(spu.getMinPrice());
            PrdStoreSku ov = overrideMap.get(k.getId());
            kv.setStorePrice(ov != null ? ov.getStorePrice() : k.getPrice());
            kv.setIsOnSale(ov != null ? ov.getIsOnSale() : (rel.getIsOnSale() == 1 ? 1 : 0));
            return kv;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public StoreSpuVO detail(Long storeId, Long spuId) {
        assertStore(storeId);
        PrdStoreSpu rel = storeSpuMapper.selectOne(Wrappers.<PrdStoreSpu>lambdaQuery()
                .eq(PrdStoreSpu::getStoreId, storeId)
                .eq(PrdStoreSpu::getSpuId, spuId));
        if (rel == null) {
            throw new BizException(ResultCode.STORE_SKU_NOT_FOUND, "该门店未配置此商品");
        }
        StoreSpuVO vo = buildVO(storeId, rel);
        if (vo == null) {
            throw new BizException(ResultCode.SPU_NOT_FOUND);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shelf(Long storeId, Long spuId, StoreShelfRequest request) {
        assertStore(storeId);
        PrdSpu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException(ResultCode.SPU_NOT_FOUND);
        }
        PrdStoreSpu rel = storeSpuMapper.selectOne(Wrappers.<PrdStoreSpu>lambdaQuery()
                .eq(PrdStoreSpu::getStoreId, storeId)
                .eq(PrdStoreSpu::getSpuId, spuId));

        if (rel == null) {
            rel = new PrdStoreSpu();
            rel.setBrandId(spu.getBrandId());
            rel.setStoreId(storeId);
            rel.setSpuId(spuId);
            rel.setIsOnSale(request.getIsOnSale());
            rel.setSort(request.getSort() == null ? 0 : request.getSort());
            storeSpuMapper.insert(rel);
        } else {
            PrdStoreSpu patch = new PrdStoreSpu();
            patch.setId(rel.getId());
            patch.setIsOnSale(request.getIsOnSale());
            if (request.getSort() != null) {
                patch.setSort(request.getSort());
            }
            storeSpuMapper.updateById(patch);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchShelf(Long storeId, List<Long> spuIds, Integer isOnSale) {
        assertStore(storeId);
        if (spuIds == null || spuIds.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "请选择要操作的商品");
        }
        if (isOnSale == null || (isOnSale != 0 && isOnSale != 1)) {
            throw new BizException(ResultCode.PARAM_INVALID, "上架状态只能是 0 或 1");
        }
        for (Long spuId : spuIds) {
            StoreShelfRequest req = new StoreShelfRequest();
            req.setIsOnSale(isOnSale);
            shelf(storeId, spuId, req);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setSkuPrice(Long storeId, Long skuId, StoreSkuPriceRequest request) {
        assertStore(storeId);
        PrdSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ResultCode.STORE_SKU_NOT_FOUND, "规格不存在");
        }
        PrdSpu spu = spuMapper.selectById(sku.getSpuId());
        if (spu == null) {
            throw new BizException(ResultCode.SPU_NOT_FOUND);
        }

        // 最低保护价校验：门店定价不得低于总部分级保护价
        if (spu.getMinPrice() != null && spu.getMinPrice() > 0
                && request.getStorePrice() < spu.getMinPrice()) {
            throw new BizException(ResultCode.PRICE_BELOW_MIN,
                    "门店售价不得低于最低保护价 " + spu.getMinPrice() + " 分");
        }

        PrdStoreSku existing = storeSkuMapper.selectOne(Wrappers.<PrdStoreSku>lambdaQuery()
                .eq(PrdStoreSku::getStoreId, storeId)
                .eq(PrdStoreSku::getSkuId, skuId));

        if (existing == null) {
            PrdStoreSku rel = new PrdStoreSku();
            rel.setBrandId(sku.getBrandId());
            rel.setStoreId(storeId);
            rel.setSkuId(skuId);
            rel.setStorePrice(request.getStorePrice());
            rel.setIsOnSale(request.getIsOnSale() == null ? 1 : request.getIsOnSale());
            storeSkuMapper.insert(rel);
        } else {
            PrdStoreSku patch = new PrdStoreSku();
            patch.setId(existing.getId());
            patch.setStorePrice(request.getStorePrice());
            if (request.getIsOnSale() != null) {
                patch.setIsOnSale(request.getIsOnSale());
            }
            storeSkuMapper.updateById(patch);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long storeId, Long spuId) {
        assertStore(storeId);
        PrdStoreSpu rel = storeSpuMapper.selectOne(Wrappers.<PrdStoreSpu>lambdaQuery()
                .eq(PrdStoreSpu::getStoreId, storeId)
                .eq(PrdStoreSpu::getSpuId, spuId));
        if (rel == null) {
            throw new BizException(ResultCode.STORE_SKU_NOT_FOUND, "该门店未配置此商品");
        }
        storeSpuMapper.deleteById(rel.getId());

        List<Long> skuIds = skuMapper.selectList(Wrappers.<PrdSku>lambdaQuery()
                        .eq(PrdSku::getSpuId, spuId))
                .stream().map(PrdSku::getId).collect(Collectors.toList());
        if (!skuIds.isEmpty()) {
            storeSkuMapper.delete(Wrappers.<PrdStoreSku>lambdaQuery()
                    .eq(PrdStoreSku::getStoreId, storeId)
                    .in(PrdStoreSku::getSkuId, skuIds));
        }
    }
}
