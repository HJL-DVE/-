package com.teaclub.cloud.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.StoreScope;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.wallet.client.BrandInternalClient;
import com.teaclub.cloud.wallet.dto.MemberBriefDTO;
import com.teaclub.cloud.wallet.dto.TransactionQuery;
import com.teaclub.cloud.wallet.dto.WalletQuery;
import com.teaclub.cloud.wallet.entity.WalWallet;
import com.teaclub.cloud.wallet.entity.WalWalletTransaction;
import com.teaclub.cloud.wallet.enums.WalletBizType;
import com.teaclub.cloud.wallet.mapper.WalRechargeOrderMapper;
import com.teaclub.cloud.wallet.mapper.WalWalletMapper;
import com.teaclub.cloud.wallet.mapper.WalWalletTransactionMapper;
import com.teaclub.cloud.wallet.service.WalletQueryService;
import com.teaclub.cloud.wallet.service.support.MemberLookup;
import com.teaclub.cloud.wallet.vo.WalletStatsVO;
import com.teaclub.cloud.wallet.vo.WalletTransactionVO;
import com.teaclub.cloud.wallet.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 钱包查询服务（由单体 backend 的 WalletQueryServiceImpl 迁移而来）。
 *
 * <p><b>与单体的差异</b>：单体在同一库里 JOIN {@code mem_member} / {@code sys_store} 做展示字段补齐；
 * 微服务下这两个库分别归 member-service 与 auth-service，
 * 改为<b>内部接口批量查询</b>（只影响展示字段，不影响资金口径）。
 *
 * <p>会员可见口径（B 方案：注册门店在范围内 <b>或</b> 在该门店消费过）
 * 由 member-service 统一裁决，钱包侧不再自行拼口径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletQueryServiceImpl implements WalletQueryService {

    private final WalWalletMapper walletMapper;
    private final WalWalletTransactionMapper txnMapper;
    private final WalRechargeOrderMapper rechargeOrderMapper;
    private final MemberLookup memberLookup;
    private final BrandInternalClient authClient;

    @Override
    public PageResult<WalletVO> pageWallet(WalletQuery query) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        LambdaQueryWrapper<WalWallet> wrapper = Wrappers.<WalWallet>lambdaQuery()
                .eq(WalWallet::getBrandId, current.getBrandId());

        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(WalWallet::getStatus, query.getStatus());
        }

        // 会员维度过滤（手机号/昵称/会员号），会员检索由 member-service 完成
        if (StringUtils.hasText(query.getPhone()) || StringUtils.hasText(query.getKeyword())) {
            List<Long> ids = memberLookup.searchMemberIds(current.getBrandId(),
                    query.getPhone() == null ? null : query.getPhone().trim(),
                    query.getKeyword() == null ? null : query.getKeyword().trim());
            if (ids.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            wrapper.in(WalWallet::getMemberId, ids);
        }

        // 门店数据权限：可见会员 = 注册门店在范围内 或 在范围内门店消费过
        List<Long> visibleMemberIds = memberLookup.visibleMemberIds(current.getBrandId(), scope);
        if (visibleMemberIds != null) {
            if (visibleMemberIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            wrapper.in(WalWallet::getMemberId, visibleMemberIds);
        }

        wrapper.orderByDesc(WalWallet::getId);
        Page<WalWallet> page = walletMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);

        Map<Long, MemberBriefDTO> memberMap = memberLookup.batchMap(
                page.getRecords().stream().map(WalWallet::getMemberId).toList());

        List<WalletVO> records = page.getRecords().stream()
                .map(x -> toWalletVO(x, memberMap.get(x.getMemberId())))
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private WalletVO toWalletVO(WalWallet w, MemberBriefDTO m) {
        WalletVO vo = new WalletVO();
        vo.setWalletId(w.getId());
        vo.setId(w.getId());
        vo.setMemberId(w.getMemberId());
        if (m != null) {
            String masked = maskPhone(m.getPhone());
            vo.setMemberNo(m.getMemberNo());
            vo.setMemberPhone(masked);
            vo.setPhone(masked);
            vo.setMemberNickname(m.getNickname());
            vo.setMemberName(m.getNickname());
        }
        vo.setPrincipalBalance(w.getPrincipalBalance());
        vo.setGiftBalance(w.getGiftBalance());
        vo.setTotalBalance(nz(w.getPrincipalBalance()) + nz(w.getGiftBalance()));
        vo.setTotalPrincipalIn(w.getTotalPrincipalIn());
        vo.setTotalGiftIn(w.getTotalGiftIn());
        vo.setTotalPrincipalOut(w.getTotalPrincipalOut());
        vo.setTotalGiftOut(w.getTotalGiftOut());
        vo.setTotalRefundIn(w.getTotalRefundIn());
        vo.setStatus(w.getStatus());
        vo.setCreateTime(w.getCreateTime());
        vo.setUpdateTime(w.getUpdateTime());
        return vo;
    }

    private static String maskPhone(String p) {
        if (p == null || p.length() < 7) {
            return p;
        }
        return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
    }

    @Override
    public WalletVO getByMember(Long memberId) {
        LoginUser current = UserContext.require();
        MemberBriefDTO member = memberLookup.require(memberId);
        if (member == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND);
        }
        StoreScope scope = StoreScope.from(current);
        List<Long> visible = memberLookup.visibleMemberIds(current.getBrandId(), scope);
        if (visible != null) {
            // B 口径：注册门店在范围内 或 在范围内门店消费过
            boolean registerInScope = member.getRegisterStoreId() != null
                    && scope.canAccess(member.getRegisterStoreId());
            if (!registerInScope && !visible.contains(memberId)) {
                throw new BizException(ResultCode.NO_STORE_PERMISSION, "无权查看该会员钱包");
            }
        }

        WalWallet w = walletMapper.selectOne(Wrappers.<WalWallet>lambdaQuery()
                .eq(WalWallet::getBrandId, current.getBrandId())
                .eq(WalWallet::getMemberId, memberId));
        if (w == null) {
            // 未开通时返回零余额视图，而非 404，便于前端直接展示
            WalletVO vo = new WalletVO();
            vo.setMemberId(memberId);
            vo.setMemberNo(member.getMemberNo());
            vo.setMemberPhone(member.getPhone());
            vo.setPhone(member.getPhone());
            vo.setMemberNickname(member.getNickname());
            vo.setMemberName(member.getNickname());
            vo.setPrincipalBalance(0L);
            vo.setGiftBalance(0L);
            vo.setTotalBalance(0L);
            vo.setTotalPrincipalIn(0L);
            vo.setTotalGiftIn(0L);
            vo.setTotalPrincipalOut(0L);
            vo.setTotalGiftOut(0L);
            vo.setTotalRefundIn(0L);
            vo.setStatus("NORMAL");
            return vo;
        }
        return toWalletVO(w, member);
    }

    @Override
    public PageResult<WalletTransactionVO> pageTransaction(TransactionQuery query) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        LambdaQueryWrapper<WalWalletTransaction> wrapper = Wrappers.<WalWalletTransaction>lambdaQuery()
                .eq(WalWalletTransaction::getBrandId, current.getBrandId());

        if (!scope.isAll()) {
            if (scope.isEmptyLimited()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            wrapper.in(WalWalletTransaction::getStoreId, scope.getStoreIds());
        }
        if (query.getMemberId() != null) {
            wrapper.eq(WalWalletTransaction::getMemberId, query.getMemberId());
        }
        if (query.getStoreId() != null) {
            scope.assertAccessible(query.getStoreId());
            wrapper.eq(WalWalletTransaction::getStoreId, query.getStoreId());
        }
        if (StringUtils.hasText(query.getBizType())) {
            wrapper.eq(WalWalletTransaction::getBizType, query.getBizType());
        }
        if (StringUtils.hasText(query.getBizNo())) {
            wrapper.eq(WalWalletTransaction::getBizNo, query.getBizNo());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(WalWalletTransaction::getCreateTime, query.getStartDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            wrapper.lt(WalWalletTransaction::getCreateTime, query.getEndDate().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(WalWalletTransaction::getId);

        Page<WalWalletTransaction> page = txnMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);

        List<WalletTransactionVO> records = toTxnVOs(current.getBrandId(), page.getRecords());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 批量补齐会员名与门店名，避免逐行 N+1 查询。 */
    private List<WalletTransactionVO> toTxnVOs(Long brandId, List<WalWalletTransaction> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, MemberBriefDTO> memberMap = memberLookup.batchMap(
                list.stream().map(WalWalletTransaction::getMemberId).toList());
        Map<Long, String> storeNames = loadStoreNames(brandId,
                list.stream().map(WalWalletTransaction::getStoreId)
                        .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)));

        return list.stream().map(t -> {
            WalletTransactionVO vo = new WalletTransactionVO();
            vo.setId(t.getId());
            vo.setTxnNo(t.getTxnNo());
            vo.setWalletId(t.getWalletId());
            vo.setMemberId(t.getMemberId());
            MemberBriefDTO m = memberMap.get(t.getMemberId());
            if (m != null) {
                vo.setMemberName(m.getNickname() == null ? m.getMemberNo() : m.getNickname());
            }
            vo.setStoreId(t.getStoreId());
            if (t.getStoreId() != null) {
                vo.setStoreName(storeNames.get(t.getStoreId()));
            }
            vo.setBizType(t.getBizType());
            vo.setBizTypeName(bizTypeName(t.getBizType()));
            vo.setBizNo(t.getBizNo());
            vo.setPrincipalChange(t.getPrincipalChange());
            vo.setPrincipalBefore(t.getPrincipalBefore());
            vo.setPrincipalAfter(t.getPrincipalAfter());
            vo.setGiftChange(t.getGiftChange());
            vo.setGiftBefore(t.getGiftBefore());
            vo.setGiftAfter(t.getGiftAfter());
            vo.setAmountPaid(t.getAmountPaid());
            vo.setGiftAmount(t.getGiftAmount());
            vo.setGiftExpireTime(t.getGiftExpireTime());
            vo.setRemark(t.getRemark());
            vo.setOperatorType(t.getOperatorType());
            vo.setCreateTime(t.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 门店名称来自 auth-service（展示型数据，下游异常降级为不展示）。 */
    private Map<Long, String> loadStoreNames(Long brandId, Set<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            var resp = authClient.storeNames(brandId, List.copyOf(storeIds));
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                return resp.getData();
            }
        } catch (Exception e) {
            log.warn("获取门店名称失败，降级为不展示: brandId={} stores={}", brandId, storeIds, e);
        }
        return Collections.emptyMap();
    }

    private String bizTypeName(String code) {
        try {
            return switch (WalletBizType.valueOf(code)) {
                case RECHARGE_IN -> "充值入账";
                case CONSUME_OUT -> "消费扣款";
                case REFUND_IN -> "退款回冲";
                case RECHARGE_REFUND_OUT -> "充值退款";
                case ADMIN_ADJUST -> "人工调整";
                case GIFT_EXPIRE -> "赠送金过期";
            };
        } catch (Exception e) {
            return code;
        }
    }

    @Override
    public List<WalletTransactionVO> listByMember(Long memberId, int limit) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        List<WalWalletTransaction> list = txnMapper.selectList(Wrappers.<WalWalletTransaction>lambdaQuery()
                .eq(WalWalletTransaction::getBrandId, current.getBrandId())
                .eq(WalWalletTransaction::getMemberId, memberId)
                .orderByDesc(WalWalletTransaction::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));

        if (!scope.isAll()) {
            list = list.stream()
                    .filter(t -> t.getStoreId() == null || scope.canAccess(t.getStoreId()))
                    .collect(Collectors.toList());
        }
        return toTxnVOs(current.getBrandId(), list);
    }

    @Override
    public List<Map<String, Object>> reconcile() {
        return txnMapper.selectInconsistentWallets();
    }

    @Override
    public WalletStatsVO stats() {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);
        Long brandId = current.getBrandId();

        WalletStatsVO vo = new WalletStatsVO();
        // null 表示不按会员过滤（总部账号）；空集合表示当前账号无可见会员
        List<Long> memberIds = memberLookup.visibleMemberIds(brandId, scope);
        if (memberIds != null && memberIds.isEmpty()) {
            vo.setWalletCount(0L);
            vo.setPrincipalTotal(0L);
            vo.setGiftTotal(0L);
            vo.setTotalBalance(0L);
            vo.setRechargeTotal(0L);
            vo.setGiftGrantTotal(0L);
            return vo;
        }

        long principalTotal = nz(walletMapper.sumPrincipalBalance(brandId, memberIds));
        long giftTotal = nz(walletMapper.sumGiftBalance(brandId, memberIds));
        vo.setWalletCount(walletMapper.countWallets(brandId, memberIds));
        vo.setPrincipalTotal(principalTotal);
        vo.setGiftTotal(giftTotal);
        vo.setTotalBalance(principalTotal + giftTotal);
        vo.setRechargeTotal(nz(rechargeOrderMapper.sumPaidRechargeAmount(brandId, memberIds)));
        vo.setGiftGrantTotal(nz(rechargeOrderMapper.sumPaidGiftAmount(brandId, memberIds)));
        return vo;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }
}
