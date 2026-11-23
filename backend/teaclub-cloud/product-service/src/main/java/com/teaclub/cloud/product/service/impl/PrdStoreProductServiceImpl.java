package com.teaclub.cloud.product.service.impl;

import com.teaclub.cloud.product.storage.ImageUrlResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.product.dto.StoreShelfRequest;
import com.teaclub.cloud.product.dto.StoreSkuPriceRequest;
import com.teaclub.cloud.product.entity.*;
import com.teaclub.cloud.product.mapper.*;
import com.teaclub.cloud.product.service.PrdStoreProductService;
import com.teaclub.cloud.product.vo.StoreSkuVO;
import com.teaclub.cloud.product.vo.StoreSpuVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrdStoreProductServiceImpl implements PrdStoreProductService {

    private final PrdStoreSpuMapper storeSpuMapper;
    private final ImageUrlResolver imageUrlResolver;
    private final com.teaclub.cloud.product.client.AuthInternalClient authClient;
    private final PrdStoreSkuMapper storeSkuMapper;
    private final PrdSpuMapper spuMapper;
    private final PrdSkuMapper skuMapper;

    /**
     * 校验门店存在且当前用户有数据权限。所有门店商品操作都必须先经过这里。
     */
    private void assertStore(Long storeId) {
        if (storeId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "门店 ID 不能为空");
        }
        // 门店属 auth-service 域，跨服务查询（不得直接访问 iam_db）
        if (!storeExists(storeId)) {
            throw new BizException(ResultCode.STORE_NOT_FOUND);
        }
        StoreScope scope = StoreScope.from(UserContext.require());
        scope.assertAccessible(storeId);
    }

    /**
     * 门店是否存在（通过 auth-service 查询）。
     *
     * <p>门店属 auth-service 域，product-service 不得直接访问 iam_db，
     * 因此走 Feign。若 auth-service 不可用，按"不存在"处理（拒绝操作），
     * 而不是放行。
     */
    private boolean storeExists(Long storeId) {
        try {
            var resp = authClient.store(storeId);
            return resp != null && resp.getCode() == 0 && resp.getData() != null;
        } catch (Exception e) {
            log.error("查询门店失败，按不存在处理: storeId={}", storeId, e);
            return false;
        }
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
        vo.setMainImage(imageUrlResolver.resolve(spu.getMainImage()));
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
            // 表上有唯一键 uk_store_spu(store_id, spu_id)，但该唯一索引**不包含逻辑删除列**。
            // 因此「曾被逻辑删除」的同键记录仍在表里，直接 insert 会撞唯一键（500）。
            // 这里先找已删除的同键记录，有则恢复（deleted=0）并更新状态。
            PrdStoreSpu deleted = storeSpuMapper.selectDeletedRel(storeId, spuId);
            if (deleted != null) {
                PrdStoreSpu restore = new PrdStoreSpu();
                restore.setId(deleted.getId());
                restore.setDeleted(0);
                restore.setIsOnSale(request.getIsOnSale());
                if (request.getSort() != null) {
                    restore.setSort(request.getSort());
                }
                storeSpuMapper.updateById(restore);
                return;
            }
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
