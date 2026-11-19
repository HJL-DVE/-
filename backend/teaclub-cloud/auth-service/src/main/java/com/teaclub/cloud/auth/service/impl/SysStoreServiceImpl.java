package com.teaclub.cloud.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.auth.dto.StoreQuery;
import com.teaclub.cloud.auth.dto.StoreSaveRequest;
import com.teaclub.cloud.auth.entity.SysStore;
import com.teaclub.cloud.auth.entity.SysUserStore;
import com.teaclub.cloud.auth.mapper.SysStoreMapper;
import com.teaclub.cloud.auth.mapper.SysUserStoreMapper;
import com.teaclub.cloud.auth.service.SysStoreService;
import com.teaclub.cloud.auth.vo.StoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysStoreServiceImpl implements SysStoreService {

    private final SysStoreMapper storeMapper;
    private final SysUserStoreMapper userStoreMapper;

    @Override
    public PageResult<StoreVO> page(StoreQuery query) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        // 门店级角色若无任何授权门店，直接返回空集（不泄露任何门店数据）
        if (scope.isEmptyLimited()) {
            return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
        }

        LambdaQueryWrapper<SysStore> wrapper = Wrappers.<SysStore>lambdaQuery()
                .eq(SysStore::getBrandId, current.getBrandId());
        if (!scope.isAll()) {
            wrapper.in(SysStore::getId, scope.getStoreIds());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysStore::getStoreName, kw).or().like(SysStore::getStoreCode, kw));
        }
        if (StringUtils.hasText(query.getCity())) {
            wrapper.eq(SysStore::getCity, query.getCity().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(SysStore::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(SysStore::getId);

        Page<SysStore> page = storeMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        List<StoreVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<StoreVO> listAccessible() {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);
        if (scope.isEmptyLimited()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SysStore> wrapper = Wrappers.<SysStore>lambdaQuery()
                .eq(SysStore::getBrandId, current.getBrandId())
                .eq(SysStore::getStatus, "OPEN")
                .orderByAsc(SysStore::getId);
        if (!scope.isAll()) {
            wrapper.in(SysStore::getId, scope.getStoreIds());
        }
        return storeMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    private StoreVO toVO(SysStore s) {
        StoreVO vo = new StoreVO();
        vo.setId(s.getId());
        vo.setBrandId(s.getBrandId());
        vo.setStoreCode(s.getStoreCode());
        vo.setStoreName(s.getStoreName());
        vo.setProvince(s.getProvince());
        vo.setCity(s.getCity());
        vo.setDistrict(s.getDistrict());
        vo.setAddress(s.getAddress());
        vo.setLongitude(s.getLongitude());
        vo.setLatitude(s.getLatitude());
        vo.setPhone(s.getPhone());
        vo.setManagerName(s.getManagerName());
        vo.setBusinessHours(s.getBusinessHours());
        vo.setOpenDate(s.getOpenDate());
        vo.setStatus(s.getStatus());
        vo.setRemark(s.getRemark());
        vo.setCreateTime(s.getCreateTime());
        vo.setUpdateTime(s.getUpdateTime());
        return vo;
    }

    @Override
    public StoreVO detail(Long id) {
        StoreScope scope = StoreScope.from(UserContext.require());
        scope.assertAccessible(id);
        return toVO(requireStore(id));
    }

    private SysStore requireStore(Long id) {
        SysStore store = storeMapper.selectById(id);
        if (store == null) {
            throw new BizException(ResultCode.STORE_NOT_FOUND);
        }
        return store;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(StoreSaveRequest request) {
        LoginUser current = UserContext.require();
        // 只有全部门店范围的角色可新增门店
        StoreScope scope = StoreScope.from(current);
        if (!scope.isAll()) {
            throw new BizException(ResultCode.FORBIDDEN, "门店级账号不能新增门店");
        }

        // 唯一索引包含已逻辑删除的数据，必须显式统计，避免出现 500
        long dup = storeMapper.countByCodeIncludingDeleted(current.getBrandId(), request.getStoreCode());
        if (dup > 0) {
            throw new BizException(ResultCode.STORE_CODE_EXISTS,
                    "门店编码 " + request.getStoreCode() + " 已被占用（可能属于已删除的门店）");
        }

        SysStore store = new SysStore();
        store.setBrandId(current.getBrandId());
        copyToEntity(request, store);
        storeMapper.insert(store);
        return store.getId();
    }

    private void copyToEntity(StoreSaveRequest request, SysStore store) {
        store.setStoreCode(request.getStoreCode());
        store.setStoreName(request.getStoreName());
        store.setProvince(request.getProvince());
        store.setCity(request.getCity());
        store.setDistrict(request.getDistrict());
        store.setAddress(request.getAddress());
        store.setLongitude(request.getLongitude());
        store.setLatitude(request.getLatitude());
        store.setPhone(request.getPhone());
        store.setManagerName(request.getManagerName());
        store.setBusinessHours(request.getBusinessHours());
        store.setOpenDate(request.getOpenDate());
        store.setStatus(request.getStatus());
        store.setRemark(request.getRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, StoreSaveRequest request) {
        StoreScope scope = StoreScope.from(UserContext.require());
        scope.assertAccessible(id);
        SysStore store = requireStore(id);

        if (!Objects.equals(store.getStoreCode(), request.getStoreCode())) {
            long dup = storeMapper.countByCodeIncludingDeleted(store.getBrandId(), request.getStoreCode());
            if (dup > 0) {
                throw new BizException(ResultCode.STORE_CODE_EXISTS,
                        "门店编码 " + request.getStoreCode() + " 已被占用");
            }
        }

        SysStore patch = new SysStore();
        patch.setId(id);
        copyToEntity(request, patch);
        storeMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);
        if (!scope.isAll()) {
            throw new BizException(ResultCode.FORBIDDEN, "门店级账号不能删除门店");
        }
        requireStore(id);

        Long boundUsers = userStoreMapper.selectCount(Wrappers.<SysUserStore>lambdaQuery()
                .eq(SysUserStore::getStoreId, id));
        if (boundUsers != null && boundUsers > 0) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "该门店已授权给 " + boundUsers + " 个账号，请先解除授权后再删除");
        }

        storeMapper.deleteById(id);
        // 清理可能残留的授权记录
        userStoreMapper.delete(Wrappers.<SysUserStore>lambdaQuery()
                .eq(SysUserStore::getStoreId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        StoreScope scope = StoreScope.from(UserContext.require());
        scope.assertAccessible(id);
        if (!scope.isAll()) {
            throw new BizException(ResultCode.FORBIDDEN, "门店级账号不能变更门店状态");
        }
        requireStore(id);
        SysStore patch = new SysStore();
        patch.setId(id);
        patch.setStatus(status);
        storeMapper.updateById(patch);
    }
}