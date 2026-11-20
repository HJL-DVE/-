package com.teaclub.cloud.report.security;

import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import lombok.Getter;

import java.util.Set;

/**
 * 报表数据权限。
 *
 * <p>口径与各业务服务的 {@code StoreScope} 完全一致：
 * <ul>
 *   <li>{@code allStoreAccess=true}（SUPER_ADMIN / HQ_ADMIN）→ 全部门店</li>
 *   <li>否则以 {@code storeIds} 为准（STORE_MANAGER 绑定的门店）</li>
 *   <li>传入 {@code storeId} 参数时额外校验：越权直接 403，而不是静默缩小范围</li>
 * </ul>
 *
 * <p><b>CASHIER 不给报表权限点</b>（sys_permission 不分配），在 Controller 的
 * {@code @PreAuthorize} 层拒绝 —— 而不是这里，这样职责清晰。
 */
@Getter
public class ReportScope {

    private final boolean all;
    private final Set<Long> storeIds;

    private ReportScope(boolean all, Set<Long> storeIds) {
        this.all = all;
        this.storeIds = storeIds;
    }

    public static ReportScope current() {
        LoginUser user = UserContext.require();
        if (user.isAllStoreAccess()) {
            return new ReportScope(true, Set.of());
        }
        Set<Long> ids = user.getStoreIds() == null ? Set.of() : user.getStoreIds();
        return new ReportScope(false, ids);
    }

    /** 解析有效门店过滤：null 表示不过滤（全部），否则必须是授权门店。 */
    public Long resolveStoreId(Long requested) {
        if (requested == null) {
            return null;
        }
        assertAccessible(requested);
        return requested;
    }

    public boolean canAccess(Long storeId) {
        return all || (storeId != null && storeIds.contains(storeId));
    }

    public void assertAccessible(Long storeId) {
        if (!canAccess(storeId)) {
            throw new BizException(ResultCode.NO_STORE_PERMISSION,
                    "没有门店[" + storeId + "]的数据权限");
        }
    }
}
