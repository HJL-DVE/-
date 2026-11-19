package com.teaclub.common.context;

import com.teaclub.common.api.ResultCode;
import com.teaclub.common.exception.BizException;
import lombok.Getter;

import java.util.Collections;
import java.util.Set;

/**
 * 门店数据范围。
 *
 * <p>ALL 表示可访问品牌下所有门店；LIMITED 表示仅可访问授权门店集合。
 * 所有涉及门店数据的查询与写入都必须经过本对象校验，前端隐藏菜单不构成安全措施。
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
        if (user.isAllStoreAccess()) {
            return all();
        }
        return limited(user.getStoreIds());
    }

    public boolean canAccess(Long storeId) {
        if (storeId == null) {
            return all;
        }
        return all || storeIds.contains(storeId);
    }

    /**
     * 断言可访问指定门店，否则抛出业务异常。
     */
    public void assertAccessible(Long storeId) {
        if (!canAccess(storeId)) {
            throw new BizException(ResultCode.NO_STORE_PERMISSION,
                    "没有门店[" + storeId + "]的数据权限");
        }
    }

    /**
     * 用于查询时的门店过滤条件。返回 null 表示不加限制。
     */
    public Set<Long> filterIds() {
        return all ? null : storeIds;
    }

    public boolean isEmptyLimited() {
        return !all && storeIds.isEmpty();
    }
}
