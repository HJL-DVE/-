package com.teaclub.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.product.dto.SkuSaveRequest;
import com.teaclub.cloud.product.dto.SpuQuery;
import com.teaclub.cloud.product.dto.SpuSaveRequest;
import com.teaclub.cloud.product.entity.PrdCategory;
import com.teaclub.cloud.product.entity.PrdSku;
import com.teaclub.cloud.product.entity.PrdSpu;
import com.teaclub.cloud.product.mapper.PrdCategoryMapper;
import com.teaclub.cloud.product.mapper.PrdSkuMapper;
import com.teaclub.cloud.product.mapper.PrdSpuMapper;
import com.teaclub.cloud.product.service.PrdSpuService;
import com.teaclub.cloud.product.vo.SkuVO;
import com.teaclub.cloud.product.vo.SpuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrdSpuServiceImpl implements PrdSpuService {

    private final PrdSpuMapper spuMapper;
    private final PrdSkuMapper skuMapper;
    private final PrdCategoryMapper categoryMapper;

    @Override
    public PageResult<SpuVO> page(SpuQuery query) {
        LoginUser current = UserContext.require();

        LambdaQueryWrapper<PrdSpu> wrapper = Wrappers.<PrdSpu>lambdaQuery()
                .eq(PrdSpu::getBrandId, current.getBrandId());
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(PrdSpu::getSpuName, kw).or().like(PrdSpu::getSpuCode, kw));
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(PrdSpu::getCategoryId, query.getCategoryId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PrdSpu::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(PrdSpu::getSort).orderByDesc(PrdSpu::getId);

        Page<PrdSpu> page = spuMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);

        Map<Long, String> catNames = categoryMapper.selectList(Wrappers.<PrdCategory>lambdaQuery())
                .stream().collect(Collectors.toMap(PrdCategory::getId, PrdCategory::getCategoryName, (a, b) -> a));

        List<Long> spuIds = page.getRecords().stream().map(PrdSpu::getId).collect(Collectors.toList());
        Map<Long, List<PrdSku>> skuMap = spuIds.isEmpty() ? Collections.emptyMap()
                : skuMapper.selectList(Wrappers.<PrdSku>lambdaQuery().in(PrdSku::getSpuId, spuIds))
                        .stream().collect(Collectors.groupingBy(PrdSku::getSpuId));

        List<SpuVO> records = page.getRecords().stream()
                .map(s -> toVO(s, catNames.get(s.getCategoryId()), skuMap.getOrDefault(s.getId(), List.of())))
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private SpuVO toVO(PrdSpu s, String categoryName, List<PrdSku> skus) {
        SpuVO vo = new SpuVO();
        vo.setId(s.getId());
        vo.setCategoryId(s.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setSpuCode(s.getSpuCode());
        vo.setSpuName(s.getSpuName());
        vo.setSubtitle(s.getSubtitle());
        vo.setDescription(s.getDescription());
        vo.setMainImage(s.getMainImage());
        vo.setImagesJson(s.getImagesJson());
        vo.setBasePrice(s.getBasePrice());
        vo.setMinPrice(s.getMinPrice());
        vo.setUnit(s.getUnit());
        vo.setHasSku(s.getHasSku());
        vo.setSort(s.getSort());
        vo.setStatus(s.getStatus());
        vo.setCreateTime(s.getCreateTime());
        vo.setSkus(skus.stream().map(k -> {
            SkuVO kv = new SkuVO();
            kv.setId(k.getId());
            kv.setSkuCode(k.getSkuCode());
            kv.setSkuName(k.getSkuName());
            kv.setSpecJson(k.getSpecJson());
            kv.setPrice(k.getPrice());
            kv.setCostPrice(k.getCostPrice());
            kv.setImageUrl(k.getImageUrl());
            kv.setSort(k.getSort());
            kv.setStatus(k.getStatus());
            return kv;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public SpuVO detail(Long id) {
        PrdSpu spu = require(id);
        PrdCategory cat = categoryMapper.selectById(spu.getCategoryId());
        List<PrdSku> skus = skuMapper.selectList(Wrappers.<PrdSku>lambdaQuery()
                .eq(PrdSku::getSpuId, id).orderByAsc(PrdSku::getSort));
        return toVO(spu, cat == null ? null : cat.getCategoryName(), skus);
    }

    private PrdSpu require(Long id) {
        PrdSpu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new BizException(ResultCode.SPU_NOT_FOUND);
        }
        return spu;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SpuSaveRequest request) {
        LoginUser current = UserContext.require();
        validateCategory(request.getCategoryId());
        validatePrices(request);

        Long dup = spuMapper.selectCount(Wrappers.<PrdSpu>lambdaQuery()
                .eq(PrdSpu::getBrandId, current.getBrandId())
                .eq(PrdSpu::getSpuCode, request.getSpuCode()));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.SPU_CODE_EXISTS);
        }

        PrdSpu spu = new PrdSpu();
        spu.setBrandId(current.getBrandId());
        copySpu(request, spu);
        spu.setHasSku(request.getSkus() != null && request.getSkus().size() > 1 ? 1 : 0);
        spuMapper.insert(spu);

        saveSkus(spu.getId(), current.getBrandId(), request.getSkus());
        return spu.getId();
    }

    private void validateCategory(Long categoryId) {
        PrdCategory cat = categoryMapper.selectById(categoryId);
        if (cat == null) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }
    }

    private void validatePrices(SpuSaveRequest request) {
        if (request.getMinPrice() != null && request.getBasePrice() != null
                && request.getMinPrice() > request.getBasePrice()) {
            throw new BizException(ResultCode.BAD_REQUEST, "最低保护价不能高于建议售价");
        }
        if (request.getSkus() != null) {
            for (SkuSaveRequest sku : request.getSkus()) {
                if (sku.getPrice() == null || sku.getPrice() <= 0) {
                    throw new BizException(ResultCode.PRICE_INVALID);
                }
                if (sku.getCostPrice() != null && sku.getCostPrice() > sku.getPrice()) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "规格[" + sku.getSkuName() + "]成本价不能高于售价");
                }
            }
        }
    }

    private void copySpu(SpuSaveRequest r, PrdSpu s) {
        s.setCategoryId(r.getCategoryId());
        s.setSpuCode(r.getSpuCode());
        s.setSpuName(r.getSpuName());
        s.setSubtitle(r.getSubtitle());
        s.setDescription(r.getDescription());
        s.setMainImage(r.getMainImage());
        s.setImagesJson(r.getImagesJson());
        s.setBasePrice(r.getBasePrice());
        s.setMinPrice(r.getMinPrice());
        s.setUnit(r.getUnit());
        s.setSort(r.getSort());
        s.setStatus(r.getStatus());
    }

    /**
     * 保存 SKU。
     *
     * <p>匹配规则（按优先级）：
     * <ol>
     *   <li>请求携带 id 且该 SKU 属于当前商品 → 更新该 SKU</li>
     *   <li>未携带 id 但 skuCode 与本商品已有 SKU 相同 → 视为更新该 SKU（避免编辑时误判为重复编码）</li>
     *   <li>其余情况 → 新增 SKU</li>
     * </ol>
     * 请求中未出现的旧 SKU 会被删除。
     */
    private void saveSkus(Long spuId, Long brandId, List<SkuSaveRequest> skus) {
        if (skus == null) {
            return;
        }

        // 本商品现有 SKU，用于编码与 ID 双重匹配
        List<PrdSku> existing = skuMapper.selectList(Wrappers.<PrdSku>lambdaQuery()
                .eq(PrdSku::getSpuId, spuId));
        Map<Long, PrdSku> byId = existing.stream()
                .collect(Collectors.toMap(PrdSku::getId, s -> s, (a, b) -> a));
        Map<String, PrdSku> byCode = existing.stream()
                .collect(Collectors.toMap(PrdSku::getSkuCode, s -> s, (a, b) -> a));

        // 解析每个请求项最终对应的 SKU ID（null 表示新增）
        Map<SkuSaveRequest, Long> resolvedIds = new LinkedHashMap<>();
        Set<String> seenCodes = new HashSet<>();
        for (SkuSaveRequest req : skus) {
            if (!seenCodes.add(req.getSkuCode())) {
                throw new BizException(ResultCode.BAD_REQUEST, "规格编码重复: " + req.getSkuCode());
            }
            Long resolved = null;
            if (req.getId() != null) {
                if (!byId.containsKey(req.getId())) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "规格不属于当前商品: " + req.getId());
                }
                resolved = req.getId();
            } else {
                PrdSku same = byCode.get(req.getSkuCode());
                if (same != null) {
                    resolved = same.getId();
                }
            }
            resolvedIds.put(req, resolved);
        }

        // 编码全局唯一校验（排除本次将复用的 SKU 自身）
        for (Map.Entry<SkuSaveRequest, Long> e : resolvedIds.entrySet()) {
            SkuSaveRequest req = e.getKey();
            Long selfId = e.getValue();
            Long dup = skuMapper.selectCount(Wrappers.<PrdSku>lambdaQuery()
                    .eq(PrdSku::getBrandId, brandId)
                    .eq(PrdSku::getSkuCode, req.getSkuCode())
                    .ne(selfId != null, PrdSku::getId, selfId));
            if (dup != null && dup > 0) {
                throw new BizException(ResultCode.SKU_CODE_EXISTS, "规格编码已存在: " + req.getSkuCode());
            }
        }

        // 删除本次未保留的旧 SKU
        List<Long> keepIds = resolvedIds.values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        LambdaQueryWrapper<PrdSku> delWrapper = Wrappers.<PrdSku>lambdaQuery().eq(PrdSku::getSpuId, spuId);
        if (!keepIds.isEmpty()) {
            delWrapper.notIn(PrdSku::getId, keepIds);
        }
        skuMapper.delete(delWrapper);

        // 按解析结果写库
        for (Map.Entry<SkuSaveRequest, Long> e : resolvedIds.entrySet()) {
            SkuSaveRequest req = e.getKey();
            Long targetId = e.getValue();
            PrdSku sku = new PrdSku();
            sku.setBrandId(brandId);
            sku.setSpuId(spuId);
            sku.setSkuCode(req.getSkuCode());
            sku.setSkuName(req.getSkuName());
            sku.setSpecJson(req.getSpecJson());
            sku.setPrice(req.getPrice());
            sku.setCostPrice(req.getCostPrice());
            sku.setImageUrl(req.getImageUrl());
            sku.setSort(req.getSort());
            sku.setStatus(req.getStatus());
            if (targetId != null) {
                sku.setId(targetId);
                skuMapper.updateById(sku);
            } else {
                skuMapper.insert(sku);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SpuSaveRequest request) {
        PrdSpu spu = require(id);
        validateCategory(request.getCategoryId());
        validatePrices(request);

        Long dup = spuMapper.selectCount(Wrappers.<PrdSpu>lambdaQuery()
                .eq(PrdSpu::getBrandId, spu.getBrandId())
                .eq(PrdSpu::getSpuCode, request.getSpuCode())
                .ne(PrdSpu::getId, id));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.SPU_CODE_EXISTS);
        }

        PrdSpu patch = new PrdSpu();
        patch.setId(id);
        copySpu(request, patch);
        patch.setHasSku(request.getSkus() != null && request.getSkus().size() > 1 ? 1 : 0);
        spuMapper.updateById(patch);

        saveSkus(id, spu.getBrandId(), request.getSkus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        spuMapper.deleteById(id);
        skuMapper.delete(Wrappers.<PrdSku>lambdaQuery().eq(PrdSku::getSpuId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        if (!"ON_SALE".equals(status) && !"OFF_SALE".equals(status)) {
            throw new BizException(ResultCode.PARAM_INVALID, "状态只能是 ON_SALE 或 OFF_SALE");
        }
        require(id);
        PrdSpu patch = new PrdSpu();
        patch.setId(id);
        patch.setStatus(status);
        spuMapper.updateById(patch);
    }
}