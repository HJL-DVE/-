package com.teaclub.cloud.member.service.support;

import com.teaclub.cloud.member.client.OrderInternalClient;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.StoreScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 会员可见性解析器（B 口径）。
 *
 * <p><b>可见口径</b>：注册门店在授权范围内 <b>或</b> 在该门店消费过。
 *
 * <p><b>为什么需要后者</b>：会员属于品牌而非门店，会员跨店消费时收银台必须能识别身份；
 * 若只看注册门店，会出现"看得到订单却查不到会员"的矛盾。
 *
 * <p><b>与单体的差异</b>：单体用一个 SQL 的 `EXISTS` 子查询同时查
 * `mem_member` 与 `oms_order`（同库）。拆分后 order_db 归 order-service，
 * 这里改为 <b>Feign 调用</b>获取"在授权门店消费过的会员 ID"，
 * 再与本库会员按注册门店取并集。
 *
 * <p><b>降级策略</b>：order-service 不可用时抛异常而非静默放行 ——
 * 静默放行会导致越权可见。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberVisibilityResolver {

    private final OrderInternalClient orderClient;

    /**
     * 解析可见会员 ID 集合。
     *
     * @param brandId  品牌 ID
     * @param scope   门店数据范围（必须是 limited，all 时无需过滤）
     * @return 可见会员 ID 集合
     */
    public List<Long> resolveVisibleMemberIds(Long brandId, StoreScope scope) {
        if (scope.isAll()) {
            // 总部账号不受门店限制
            return Collections.emptyList();
        }
        if (scope.isEmptyLimited()) {
            return Collections.emptyList();
        }
        Collection<Long> storeIds = scope.getStoreIds();
        if (storeIds == null || storeIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            var resp = orderClient.memberIdsByStoreIds(brandId, List.copyOf(storeIds));
            if (resp == null) {
                throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "订单服务未返回可见会员集合");
            }
            if (resp.getCode() != 0) {
                throw new BizException(resp.getCode(), resp.getMessage());
            }
            return resp.getData() == null ? Collections.emptyList() : resp.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 关键：不可用时必须失败，不能降级为"全部可见"
            log.error("调用 order-service 获取可见会员失败，按拒绝处理: brandId={} stores={}",
                    brandId, storeIds, e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE,
                    "订单服务暂不可用，无法确定会员可见范围");
        }
    }
}
