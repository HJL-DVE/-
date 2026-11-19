package com.teaclub.cloud.wallet.service.support;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.wallet.client.MemberInternalClient;
import com.teaclub.cloud.wallet.dto.MemberBriefDTO;
import com.teaclub.cloud.wallet.dto.MemberLevelBriefDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会员信息查询（跨服务）。
 *
 * <p><b>两类语义必须区分</b>：
 * <ul>
 *   <li><b>展示型</b>（会员昵称/手机号/等级名/门店名）：下游不可用时降级为不展示，不影响主流程</li>
 *   <li><b>鉴权型</b>（可见会员范围）：下游不可用时<b>必须失败</b>，
 *       静默放行会导致越权可见</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberLookup {

    private final MemberInternalClient memberClient;

    /** 单个会员；查无返回 null，下游异常抛业务异常（资金操作不允许在会员身份不明时继续）。 */
    public MemberBriefDTO require(Long memberId) {
        try {
            Result<MemberBriefDTO> resp = memberClient.brief(memberId);
            check(resp);
            return resp.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 member-service 查询会员失败: memberId={}", memberId, e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "会员服务暂不可用");
        }
    }

    /** 批量会员（展示用）：下游异常降级为空 Map。 */
    public Map<Long, MemberBriefDTO> batchMap(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = memberIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Result<List<MemberBriefDTO>> resp = memberClient.batch(ids);
            check(resp);
            if (resp.getData() == null) {
                return Collections.emptyMap();
            }
            Map<Long, MemberBriefDTO> map = new LinkedHashMap<>();
            for (MemberBriefDTO m : resp.getData()) {
                if (m != null && m.getId() != null) {
                    map.put(m.getId(), m);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("调用 member-service 批量查询会员失败，降级为不展示会员信息: ids={}", ids.size(), e);
            return Collections.emptyMap();
        }
    }

    /** 批量会员等级（展示用）：下游异常降级为空 Map。 */
    public Map<Long, MemberLevelBriefDTO> levelMap(Collection<Long> levelIds) {
        if (levelIds == null || levelIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = levelIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Result<List<MemberLevelBriefDTO>> resp = memberClient.levelsBatch(ids);
            check(resp);
            if (resp.getData() == null) {
                return Collections.emptyMap();
            }
            Map<Long, MemberLevelBriefDTO> map = new LinkedHashMap<>();
            for (MemberLevelBriefDTO l : resp.getData()) {
                if (l != null && l.getId() != null) {
                    map.put(l.getId(), l);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("调用 member-service 批量查询等级失败，降级为不展示等级信息: ids={}", ids.size(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * 可见会员 ID 集合（鉴权型）。
     *
     * @return 可见会员 ID；scope 为全部门店时返回 null 表示不加限制
     */
    public List<Long> visibleMemberIds(Long brandId, StoreScope scope) {
        if (scope.isAll()) {
            return null;
        }
        if (scope.isEmptyLimited()) {
            return Collections.emptyList();
        }
        try {
            Result<List<Long>> resp = memberClient.visibleMemberIds(brandId, List.copyOf(scope.getStoreIds()));
            check(resp);
            return resp.getData() == null ? Collections.emptyList() : resp.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 member-service 获取可见会员失败，按拒绝处理: brandId={} stores={}",
                    brandId, scope.getStoreIds(), e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "会员服务暂不可用，无法确定会员可见范围");
        }
    }

    /** 按手机号/关键字检索会员 ID（展示型过滤）：下游异常降级为空列表。 */
    public List<Long> searchMemberIds(Long brandId, String phone, String keyword) {
        try {
            Result<List<Long>> resp = memberClient.memberIds(brandId, phone, keyword);
            check(resp);
            return resp.getData() == null ? Collections.emptyList() : resp.getData();
        } catch (Exception e) {
            log.warn("调用 member-service 检索会员失败，按无匹配处理: brandId={}", brandId, e);
            return Collections.emptyList();
        }
    }

    private static void check(Result<?> resp) {
        if (resp == null) {
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "会员服务无响应");
        }
        if (resp.getCode() != 0) {
            throw new BizException(resp.getCode(), resp.getMessage());
        }
    }
}
