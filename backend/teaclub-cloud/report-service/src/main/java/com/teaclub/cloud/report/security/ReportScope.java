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

    /**
     * 解析有效门店过滤。
     * <ul>
     *   <li>全量账号（SUPER_ADMIN / HQ_ADMIN）：requested 为 null 表示全部门店，否则按指定门店；</li>
     *   <li>受限账号（STORE_MANAGER）：requested 非空时先校验越权；requested 为空时
     *       <b>强制收敛到其绑定门店</b>（绑定多店时取最小 ID 作为默认门店，前端可显式传
     *       storeId 切换），绝不会因为前端未传参而退化成「全部门店」。</li>
     * </ul>
     */
    public Long resolveStoreId(Long requested) {
        if (all) {
            return requested;
        }
        if (requested != null) {
            assertAccessible(requested);
            return requested;
        }
        if (storeIds == null || storeIds.isEmpty()) {
            throw new BizException(ResultCode.NO_STORE_PERMISSION,
                    "当前账号未绑定门店，无法查看经营报表");
        }
        return storeIds.stream().min(Long::compareTo).orElseThrow();
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
