package com.teaclub.product.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.common.api.ResultCode;
import com.teaclub.common.context.LoginUser;
import com.teaclub.common.context.UserContext;
import com.teaclub.common.exception.BizException;
import com.teaclub.product.dto.CategorySaveRequest;
import com.teaclub.product.entity.PrdCategory;
import com.teaclub.product.entity.PrdSpu;
import com.teaclub.product.mapper.PrdCategoryMapper;
import com.teaclub.product.mapper.PrdSpuMapper;
import com.teaclub.product.service.PrdCategoryService;
import com.teaclub.product.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrdCategoryServiceImpl implements PrdCategoryService {

    private final PrdCategoryMapper categoryMapper;
    private final PrdSpuMapper spuMapper;

    @Override
    public List<CategoryVO> tree() {
        LoginUser current = UserContext.require();
        List<PrdCategory> all = categoryMapper.selectList(Wrappers.<PrdCategory>lambdaQuery()
                .eq(PrdCategory::getBrandId, current.getBrandId())
                .orderByAsc(PrdCategory::getSort)
                .orderByAsc(PrdCategory::getId));

        Map<Long, Long> countMap = spuMapper.selectList(Wrappers.<PrdSpu>lambdaQuery()
                        .eq(PrdSpu::getBrandId, current.getBrandId()))
                .stream().collect(Collectors.groupingBy(PrdSpu::getCategoryId, Collectors.counting()));

        Map<Long, CategoryVO> map = new LinkedHashMap<>();
        for (PrdCategory c : all) {
            CategoryVO vo = new CategoryVO();
            vo.setId(c.getId());
            vo.setParentId(c.getParentId());
            vo.setCategoryName(c.getCategoryName());
            vo.setIconUrl(c.getIconUrl());
            vo.setSort(c.getSort());
            vo.setStatus(c.getStatus());
            vo.setProductCount(countMap.getOrDefault(c.getId(), 0L));
            map.put(c.getId(), vo);
        }
        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO vo : map.values()) {
            Long pid = vo.getParentId();
            if (pid == null || pid == 0L || !map.containsKey(pid)) {
                roots.add(vo);
            } else {
                map.get(pid).getChildren().add(vo);
            }
        }
        return roots;
    }

    @Override
    public CategoryVO detail(Long id) {
        PrdCategory c = require(id);
        CategoryVO vo = new CategoryVO();
        vo.setId(c.getId());
        vo.setParentId(c.getParentId());
        vo.setCategoryName(c.getCategoryName());
        vo.setIconUrl(c.getIconUrl());
        vo.setSort(c.getSort());
        vo.setStatus(c.getStatus());
        return vo;
    }

    private PrdCategory require(Long id) {
        PrdCategory c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.CATEGORY_NOT_FOUND);
        }
        return c;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CategorySaveRequest request) {
        LoginUser current = UserContext.require();
        if (request.getParentId() != null && request.getParentId() != 0L) {
            require(request.getParentId());
        }
        PrdCategory c = new PrdCategory();
        c.setBrandId(current.getBrandId());
        c.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        c.setCategoryName(request.getCategoryName());
        c.setIconUrl(request.getIconUrl());
        c.setSort(request.getSort());
        c.setStatus(request.getStatus());
        categoryMapper.insert(c);
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategorySaveRequest request) {
        require(id);
        if (Objects.equals(request.getParentId(), id)) {
            throw new BizException(ResultCode.BAD_REQUEST, "父级分类不能是自己");
        }
        if (request.getParentId() != null && request.getParentId() != 0L) {
            require(request.getParentId());
        }
        PrdCategory patch = new PrdCategory();
        patch.setId(id);
        patch.setParentId(request.getParentId());
        patch.setCategoryName(request.getCategoryName());
        patch.setIconUrl(request.getIconUrl());
        patch.setSort(request.getSort());
        patch.setStatus(request.getStatus());
        categoryMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        Long children = categoryMapper.selectCount(Wrappers.<PrdCategory>lambdaQuery()
                .eq(PrdCategory::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ResultCode.CATEGORY_HAS_CHILDREN);
        }
        Long products = spuMapper.selectCount(Wrappers.<PrdSpu>lambdaQuery()
                .eq(PrdSpu::getCategoryId, id));
        if (products != null && products > 0) {
            throw new BizException(ResultCode.CATEGORY_HAS_PRODUCT);
        }
        categoryMapper.deleteById(id);
    }
}
