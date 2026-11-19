package com.teaclub.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teaclub.common.api.PageResult;
import com.teaclub.common.api.ResultCode;
import com.teaclub.common.context.LoginUser;
import com.teaclub.common.context.StoreScope;
import com.teaclub.common.context.UserContext;
import com.teaclub.common.exception.BizException;
import com.teaclub.member.entity.MemMember;
import com.teaclub.member.mapper.MemMemberMapper;
import com.teaclub.system.entity.SysStore;
import com.teaclub.system.mapper.SysStoreMapper;
import com.teaclub.wallet.dto.TransactionQuery;
import com.teaclub.wallet.dto.WalletQuery;
import com.teaclub.wallet.entity.WalWallet;
import com.teaclub.wallet.entity.WalWalletTransaction;
import com.teaclub.wallet.enums.WalletBizType;
import com.teaclub.wallet.mapper.WalWalletMapper;
import com.teaclub.wallet.mapper.WalWalletTransactionMapper;
import com.teaclub.wallet.service.WalletQueryService;
import com.teaclub.wallet.vo.WalletTransactionVO;
import com.teaclub.wallet.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletQueryServiceImpl implements WalletQueryService {

    private final WalWalletMapper walletMapper;
    private final WalWalletTransactionMapper txnMapper;
    private final MemMemberMapper memberMapper;
    private final SysStoreMapper storeMapper;

    @Override
    public PageResult<WalletVO> pageWallet(WalletQuery query) {
        LoginUser current = UserContext.require();
        StoreScope scope = StoreScope.from(current);

        LambdaQueryWrapper<WalWallet> wrapper = Wrappers.<WalWallet>lambdaQuery()
                .eq(WalWallet::getBrandId, current.getBrandId());

        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(WalWallet::getStatus, query.getStatus());
        }

        // 会员维度过滤（手机号/昵称）
        if (StringUtils.hasText(query.getPhone()) || StringUtils.hasText(query.getKeyword())) {
            LambdaQueryWrapper<MemMember> mw = Wrappers.<MemMember>lambdaQuery()
                    .eq(MemMember::getBrandId, current.getBrandId());
            if (StringUtils.hasText(query.getPhone())) {
                mw.like(MemMember::getPhone, query.getPhone().trim());
            }
            if (StringUtils.hasText(query.getKeyword())) {
                String kw = query.getKeyword().trim();
                mw.and(x -> x.like(MemMember::getNickname, kw).or().like(MemMember::getMemberNo, kw));
            }
            List<Long> ids = memberMapper.selectList(mw).stream()
                    .map(MemMember::getId).collect(Collectors.toList());
            if (ids.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            wrapper.in(WalWallet::getMemberId, ids);
        }

        // 门店角色（B 口径）：可见会员 = 注册门店在范围内 或 在本店消费过，
        // 与 MemMemberServiceImpl 保持完全一致，避免"能看到会员却查不到钱包"的矛盾。
        if (!scope.isAll()) {
            if (scope.isEmptyLimited()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            List<Long> memberIds = memberMapper.selectVisibleMemberIds(
                    current.getBrandId(), scope.getStoreIds());
            if (memberIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, query.safePageNo(), query.safePageSize());
            }
            wrapper.in(WalWallet::getMemberId, memberIds);
        }

        wrapper.orderByDesc(WalWallet::getId);
        Page<WalWallet> page = walletMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);

        List<WalletVO> records = page.getRecords().stream().map(this::toWalletVO).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private WalletVO toWalletVO(WalWallet w) {
        WalletVO vo = new WalletVO();
        vo.setWalletId(w.getId());
        vo.setMemberId(w.getMemberId());
        MemMember m = memberMapper.selectById(w.getMemberId());
        if (m != null) {
            vo.setMemberNo(m.getMemberNo());
            vo.setMemberPhone(maskPhone(m.getPhone()));
            vo.setMemberNickname(m.getNickname());
        }
        vo.setPrincipalBalance(w.getPrincipalBalance());
        vo.setGiftBalance(w.getGiftBalance());
        vo.setTotalBalance(w.getPrincipalBalance() + w.getGiftBalance());
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

    private String maskPhone(String p) {
        if (p == null || p.length() < 7) {
            return p;
        }
        return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
    }

    @Override
    public WalletVO getByMember(Long memberId) {
        LoginUser current = UserContext.require();
        MemMember member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND);
        }
        StoreScope scope = StoreScope.from(current);
        if (!scope.isAll()) {
            // B 口径：注册门店在范围内 或 在本店消费过
            boolean registerInScope = member.getRegisterStoreId() != null
                    && scope.canAccess(member.getRegisterStoreId());
            if (!registerInScope) {
                if (scope.isEmptyLimited()) {
                    throw new BizException(ResultCode.NO_STORE_PERMISSION, "当前账号未授权任何门店");
                }
                List<Long> visible = memberMapper.selectVisibleMemberIds(
                        current.getBrandId(), scope.getStoreIds());
                if (!visible.contains(memberId)) {
                    throw new BizException(ResultCode.NO_STORE_PERMISSION, "无权查看该会员钱包");
                }
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
            vo.setMemberNickname(member.getNickname());
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
        return toWalletVO(w);
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
        return new PageResult<>(page.getRecords().stream().map(this::toTxnVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    private WalletTransactionVO toTxnVO(WalWalletTransaction t) {
        WalletTransactionVO vo = new WalletTransactionVO();
        vo.setId(t.getId());
        vo.setTxnNo(t.getTxnNo());
        vo.setMemberId(t.getMemberId());
        MemMember m = memberMapper.selectById(t.getMemberId());
        if (m != null) {
            vo.setMemberName(m.getNickname());
        }
        vo.setStoreId(t.getStoreId());
        if (t.getStoreId() != null) {
            SysStore s = storeMapper.selectById(t.getStoreId());
            vo.setStoreName(s == null ? null : s.getStoreName());
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

        LambdaQueryWrapper<WalWalletTransaction> wrapper = Wrappers.<WalWalletTransaction>lambdaQuery()
                .eq(WalWalletTransaction::getBrandId, current.getBrandId())
                .eq(WalWalletTransaction::getMemberId, memberId)
                .orderByDesc(WalWalletTransaction::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));

        List<WalWalletTransaction> list = txnMapper.selectList(wrapper);
        if (!scope.isAll()) {
            list = list.stream()
                    .filter(t -> t.getStoreId() == null || scope.canAccess(t.getStoreId()))
                    .collect(Collectors.toList());
        }
        return list.stream().map(this::toTxnVO).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> reconcile() {
        return txnMapper.selectInconsistentWallets();
    }
}
