package com.teaclub.cloud.security.context;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import lombok.Getter;

import java.util.Collections;
import java.util.Set;

/**
 * 门店数据范围（与单体项目 StoreScope 语义完全一致）。
 *
 * <p>拆分微服务后，数据权限仍在<b>每个服务内部</b>校验，
 * 不依赖网关，也不依赖其他服务。
 */
@Getter
public final class StoreScope {

    private final boolean all;
    private final Set<Long> storeIds;

    private StoreScope(boolean all, Set<Long> storeIds) {
        this.all = all;
        this.storeIds = storeIds;
    }

    public static StoreScope all() {
        return new StoreScope(true, Collections.emptySet());
    }

    public static StoreScope limited(Set<Long> ids) {
        return new StoreScope(false, ids == null ? Collections.emptySet() : Set.copyOf(ids));
    }

    public static StoreScope from(LoginUser user) {
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user.isAllStoreAccess() ? all() : limited(user.getStoreIds());
    }

    public boolean canAccess(Long storeId) {
        if (storeId == null) {
            return all;
        }
        return all || storeIds.contains(storeId);
    }

    public void assertAccessible(Long storeId) {
        if (!canAccess(storeId)) {
            throw new BizException(ResultCode.NO_STORE_PERMISSION,
                    "没有门店[" + storeId + "]的数据权限");
        }
    }

    public Set<Long> filterIds() {
        return all ? null : storeIds;
    }

    public boolean isEmptyLimited() {
        return !all && storeIds.isEmpty();
    }
}
