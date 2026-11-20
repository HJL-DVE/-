package com.teaclub.cloud.member.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.member.dto.MemberRegisterRequest;
import com.teaclub.cloud.member.dto.MemberWxBindRequest;
import com.teaclub.cloud.member.entity.MemMember;
import com.teaclub.cloud.member.entity.MemMemberLevel;
import com.teaclub.cloud.member.entity.MemMemberWx;
import com.teaclub.cloud.member.mapper.MemMemberLevelMapper;
import com.teaclub.cloud.member.mapper.MemMemberMapper;
import com.teaclub.cloud.member.mapper.MemMemberWxMapper;
import com.teaclub.cloud.member.service.MemMemberOnboardingService;
import com.teaclub.cloud.member.service.support.MemberNoAllocator;
import com.teaclub.cloud.member.vo.MemberCredentialVO;
import com.teaclub.cloud.member.vo.MemberWxBindVO;
import com.teaclub.cloud.member.vo.MemberWxMemberVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 会员开户与微信绑定实现。
 *
 * <p><b>手机号占位规则</b>：{@code mem_member.phone} 为 {@code NOT NULL} 且与 brand_id 联合唯一，
 * 而小程序首次静默登录未必拿到手机号。此时写入 {@code WX + sha256(openid) 前 16 位大写十六进制}
 * 作为占位号（真实手机号为 11 位数字，不会与占位号冲突）；后续拿到手机号时若命中既有会员，
 * 绑定到该会员而不是新建，从而避免占位号沉淀成脏数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemMemberOnboardingServiceImpl implements MemMemberOnboardingService {

    private static final String REGISTER_CHANNEL = "MINIAPP";
    private static final String STATUS_NORMAL = "NORMAL";
    private static final String LEVEL_STATUS_ENABLED = "ENABLED";
    private static final int MEMBER_NO_RETRY = 5;

    private final MemMemberMapper memberMapper;
    private final MemMemberLevelMapper levelMapper;
    private final MemMemberWxMapper wxMapper;
    private final MemberNoAllocator memberNoAllocator;

    @Override
    public MemberWxMemberVO findByOpenid(String openid, Long brandId) {
        MemMemberWx wx = findWx(brandId, openid);
        if (wx == null) {
            return null;
        }
        MemMember member = memberMapper.selectById(wx.getMemberId());
        if (member == null) {
            return null;
        }
        return new MemberWxMemberVO(member.getId(), member.getMemberNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberWxBindVO bind(MemberWxBindRequest request) {
        Long brandId = request.getBrandId();
        String openid = request.getOpenid().trim();

        MemMemberWx wx = findWx(brandId, openid);
        if (wx != null) {
            MemMember bound = memberMapper.selectById(wx.getMemberId());
            if (bound != null) {
                syncProfile(bound, request.getNickname(), request.getAvatarUrl());
                log.info("微信已绑定会员: brandId={} openid={} memberId={}", brandId, openid, bound.getId());
                return new MemberWxBindVO(bound.getId(), bound.getMemberNo(), false);
            }
            log.warn("微信绑定指向的会员已不存在，清理后重新绑定: wxId={} memberId={}", wx.getId(), wx.getMemberId());
            wxMapper.deleteById(wx.getId());
        }

        String phone = normalizePhone(request.getPhone());
        MemMember member = phone == null ? null : findByPhone(brandId, phone);
        boolean isNew = false;
        if (member == null) {
            member = createMember(brandId, phone == null ? placeholderPhone(openid) : phone,
                    request.getNickname(), request.getAvatarUrl());
            isNew = true;
        } else {
            syncProfile(member, request.getNickname(), request.getAvatarUrl());
        }
        saveBinding(brandId, openid, member.getId());
        log.info("微信会员绑定完成: brandId={} openid={} memberId={} isNew={}",
                brandId, openid, member.getId(), isNew);
        return new MemberWxBindVO(member.getId(), member.getMemberNo(), isNew);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberWxBindVO register(MemberRegisterRequest request) {
        Long brandId = request.getBrandId();
        String phone = normalizePhone(request.getPhone());
        if (phone == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "手机号不能为空");
        }
        if (findByPhone(brandId, phone) != null) {
            throw new BizException(ResultCode.MEMBER_PHONE_EXISTS);
        }
        MemMember member = new MemMember();
        member.setBrandId(brandId);
        member.setPhone(phone);
        member.setPassword(request.getPasswordHash());
        member.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname() : defaultNickname(phone));
        member.setRegisterChannel(REGISTER_CHANNEL);
        member.setStatus(STATUS_NORMAL);
        member.setLevelId(defaultLevelId(brandId));
        member.setPoints(0L);
        member.setTotalConsumeAmount(0L);
        member.setTotalRechargeAmount(0L);
        member.setTotalOrderCount(0);
        persist(member, true);
        log.info("会员注册成功: brandId={} memberId={} memberNo={}", brandId, member.getId(), member.getMemberNo());
        return new MemberWxBindVO(member.getId(), member.getMemberNo(), true);
    }

    @Override
    public MemberCredentialVO credential(String phone, Long brandId) {
        if (!StringUtils.hasText(phone)) {
            throw new BizException(ResultCode.PARAM_INVALID, "手机号不能为空");
        }
        MemMember member = findByPhone(brandId, phone.trim());
        if (member == null) {
            return null;
        }
        return new MemberCredentialVO(member.getId(), member.getMemberNo(), member.getPhone(),
                member.getPassword(), member.getNickname(), member.getAvatarUrl(), member.getStatus());
    }

    // ====== 内部工具 ======

    private MemMemberWx findWx(Long brandId, String openid) {
        return wxMapper.selectOne(Wrappers.<MemMemberWx>lambdaQuery()
                .eq(MemMemberWx::getBrandId, brandId)
                .eq(MemMemberWx::getOpenid, openid)
                .last("LIMIT 1"));
    }

    private MemMember findByPhone(Long brandId, String phone) {
        return memberMapper.selectOne(Wrappers.<MemMember>lambdaQuery()
                .eq(MemMember::getBrandId, brandId)
                .eq(MemMember::getPhone, phone)
                .last("LIMIT 1"));
    }

    private MemMember createMember(Long brandId, String phone, String nickname, String avatarUrl) {
        MemMember member = new MemMember();
        member.setBrandId(brandId);
        member.setPhone(phone);
        member.setNickname(StringUtils.hasText(nickname) ? nickname : defaultNickname(phone));
        member.setAvatarUrl(avatarUrl);
        member.setRegisterChannel(REGISTER_CHANNEL);
        member.setStatus(STATUS_NORMAL);
        member.setLevelId(defaultLevelId(brandId));
        member.setPoints(0L);
        member.setTotalConsumeAmount(0L);
        member.setTotalRechargeAmount(0L);
        member.setTotalOrderCount(0);
        persist(member, false);
        return member;
    }

    /**
     * 落库并分配会员号。
     *
     * @param phoneAssigned 手机号是否来自用户（true 时唯一键冲突代表手机号已注册）
     */
    private void persist(MemMember member, boolean phoneAssigned) {
        for (int attempt = 1; attempt <= MEMBER_NO_RETRY; attempt++) {
            member.setId(null);
            member.setMemberNo(memberNoAllocator.next(member.getBrandId()));
            try {
                memberMapper.insert(member);
                return;
            } catch (DataIntegrityViolationException e) {
                if (phoneAssigned) {
                    throw new BizException(ResultCode.MEMBER_PHONE_EXISTS);
                }
                log.warn("会员号并发冲突，重试: brandId={} memberNo={} attempt={}",
                        member.getBrandId(), member.getMemberNo(), attempt);
            }
        }
        throw new BizException(ResultCode.SYSTEM_ERROR, "会员号分配冲突，请稍后重试");
    }

    private void saveBinding(Long brandId, String openid, Long memberId) {
        MemMemberWx wx = new MemMemberWx();
        wx.setBrandId(brandId);
        wx.setOpenid(openid);
        wx.setMemberId(memberId);
        try {
            wxMapper.insert(wx);
        } catch (DuplicateKeyException e) {
            // 并发首登：另一请求已写入同一 openid 的绑定关系，以既有关系为准
            MemMemberWx existing = findWx(brandId, openid);
            if (existing == null) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "微信绑定写入冲突");
            }
            log.info("微信绑定并发写入，复用既有关系: brandId={} openid={} memberId={}",
                    brandId, openid, existing.getMemberId());
        }
    }

    /** 同步小程序带来的昵称/头像（仅在本次请求带了新值且与库内不同时更新）。 */
    private void syncProfile(MemMember member, String nickname, String avatarUrl) {
        MemMember patch = new MemMember();
        boolean dirty = false;
        if (StringUtils.hasText(nickname) && !nickname.equals(member.getNickname())) {
            patch.setNickname(nickname);
            dirty = true;
        }
        if (StringUtils.hasText(avatarUrl) && !avatarUrl.equals(member.getAvatarUrl())) {
            patch.setAvatarUrl(avatarUrl);
            dirty = true;
        }
        if (!dirty) {
            return;
        }
        patch.setId(member.getId());
        memberMapper.updateById(patch);
    }

    private Long defaultLevelId(Long brandId) {
        MemMemberLevel level = levelMapper.selectOne(Wrappers.<MemMemberLevel>lambdaQuery()
                .eq(MemMemberLevel::getBrandId, brandId)
                .eq(MemMemberLevel::getStatus, LEVEL_STATUS_ENABLED)
                .orderByAsc(MemMemberLevel::getLevelNo)
                .last("LIMIT 1"));
        return level == null ? null : level.getId();
    }

    private static String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }

    private static String defaultNickname(String seed) {
        if (seed == null || seed.isEmpty()) {
            return "会员";
        }
        return "会员" + (seed.length() <= 4 ? seed : seed.substring(seed.length() - 4));
    }

    /**
     * 小程序静默登录（未拿到真实手机号）时的占位手机号：
     * <b>1 开头的 11 位数字</b>，由 openid 的 SHA-256 派生 —— 同一 openid 稳定得到同一号码。
     * 空间 10^10，与新注册真实手机号偶发碰撞时由 persist 的重试逻辑兜底
     * （占位号场景 phoneAssigned=false，冲突重试换号重插）。
     */
    private static String placeholderPhone(String openid) {
        String hash = sha256Hex(openid);
        // 取哈希前 10 个十进制位拼在「1」后面：1 + 10 位 = 11 位，1 开头
        StringBuilder sb = new StringBuilder(11);
        sb.append('1');
        for (int i = 0; i < 10; i++) {
            sb.append(Character.forDigit(Character.digit(hash.charAt(i % hash.length()), 16) % 10, 10));
        }
        return sb.toString();
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
