package com.teaclub.cloud.auth.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.auth.client.MemberWxInternalClient;
import com.teaclub.cloud.auth.client.dto.MemberBindDTO;
import com.teaclub.cloud.auth.client.dto.MemberCredentialDTO;
import com.teaclub.cloud.auth.client.dto.MemberRegisterDTO;
import com.teaclub.cloud.auth.client.dto.MemberWxBindRequestDTO;
import com.teaclub.cloud.auth.config.MemberBrandProperties;
import com.teaclub.cloud.auth.dto.MemberLoginRequest;
import com.teaclub.cloud.auth.dto.MemberRegisterRequest;
import com.teaclub.cloud.auth.dto.WxLoginRequest;
import com.teaclub.cloud.auth.entity.SysStore;
import com.teaclub.cloud.auth.mapper.SysStoreMapper;
import com.teaclub.cloud.auth.vo.MemberProfileVO;
import com.teaclub.cloud.auth.vo.MemberTokenVO;
import com.teaclub.cloud.auth.vo.PublicStoreVO;
import com.teaclub.cloud.auth.wx.WxOpenIdResolver;
import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import com.teaclub.cloud.security.context.LoginUser;
import com.teaclub.cloud.security.context.UserContext;
import com.teaclub.cloud.security.jwt.JwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 会员端（微信小程序）身份接口。
 *
 * <p>本控制器只做「会员身份 + 出入参适配」：登录态、注册、档案与门店浏览，
 * 业务数据全部落在各自领域的服务里（会员档案在 member-service，门店在 auth-service 自己的 iam_db）。
 *
 * <p>会员令牌使用 {@code JwtProvider#issueMemberToken}（audience=member），
 * 与管理员令牌密钥/受众完全隔离；网关解析后注入 {@code X-Member-Id} / {@code X-Brand-Id}。
 *
 * <p>白名单接口（{@code /wx-login}、{@code /member-login}、{@code /member-register}、
 * {@code /stores**}）不携带登录态，品牌归属取 {@code teaclub.member.default-brand-id}
 * （单品牌部署的既定配置）。
 */
@Tag(name = "会员端认证", description = "小程序登录、注册、会员档案、门店浏览")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class MemberAuthController {

    /** 会员状态：正常。 */
    private static final String MEMBER_STATUS_NORMAL = "NORMAL";
    /** 营业时间文本里的时刻，例如 "09:00-22:00" 中的两个时刻。 */
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}:\\d{2})");
    /** 真实手机号形态；member-service 在未授权手机号时会写 WX 前缀占位号。 */
    private static final Pattern REAL_PHONE = Pattern.compile("^1\\d{10}$");

    private final WxOpenIdResolver wxOpenIdResolver;
    private final MemberWxInternalClient memberClient;
    private final com.teaclub.cloud.auth.client.MarketingInternalClient marketingClient;
    private final MemberBrandProperties memberBrandProperties;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final SysStoreMapper storeMapper;

    @Operation(summary = "小程序登录（openid find-or-bind）")
    @PostMapping("/wx-login")
    public Result<MemberTokenVO> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        Long brandId = memberBrandProperties.getDefaultBrandId();
        String openid = wxOpenIdResolver.resolveOpenId(request.getCode());

        MemberBindDTO bind = findBoundMember(openid, brandId);
        if (bind == null) {
            MemberWxBindRequestDTO bindRequest = new MemberWxBindRequestDTO();
            bindRequest.setOpenid(openid);
            bindRequest.setBrandId(brandId);
            bindRequest.setNickname(request.getNickname());
            bindRequest.setAvatarUrl(request.getAvatarUrl());
            bindRequest.setPhone(blankToNull(request.getPhone()));
            bind = unwrap(callBind(bindRequest), "微信登录绑定会员失败");
        }
        log.info("小程序登录成功: brandId={} memberId={} isNew={}", brandId, bind.getMemberId(), bind.getIsNew());
        return Result.ok(buildToken(bind, brandId));
    }

    @Operation(summary = "会员手机号注册")
    @PostMapping("/member-register")
    public Result<MemberTokenVO> register(@Valid @RequestBody MemberRegisterRequest request) {
        Long brandId = memberBrandProperties.getDefaultBrandId();

        MemberRegisterDTO registerRequest = new MemberRegisterDTO();
        registerRequest.setBrandId(brandId);
        registerRequest.setPhone(request.getPhone().trim());
        // 密码散列在本服务生成，member-service 只落库、不持有明文
        registerRequest.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        registerRequest.setNickname(request.getNickname());

        MemberBindDTO bind = unwrap(callRegister(registerRequest), "会员注册失败");
        log.info("会员注册成功: brandId={} memberId={} memberNo={}", brandId, bind.getMemberId(), bind.getMemberNo());

        // 新人礼包：注册成功后立即发放（营销服务不可用不影响注册成功）
        try {
            var granted = marketingClient.triggerNewMember(brandId, bind.getMemberId());
            if (granted != null && granted.getCode() == 0 && granted.getData() != null && granted.getData() > 0) {
                log.info("[NEW-MEMBER-GIFT] 新人礼包已发放 memberId={} 活动数={}",
                        bind.getMemberId(), granted.getData());
            }
        } catch (Exception e) {
            log.warn("[NEW-MEMBER-GIFT] 新人礼包发放失败（不影响注册） memberId={} err={}",
                    bind.getMemberId(), e.getMessage());
        }
        return Result.ok(buildToken(bind, brandId));
    }

    @Operation(summary = "会员手机号密码登录")
    @PostMapping("/member-login")
    public Result<MemberTokenVO> memberLogin(@Valid @RequestBody MemberLoginRequest request) {
        Long brandId = memberBrandProperties.getDefaultBrandId();
        MemberCredentialDTO credential = loadCredential(request.getPhone().trim(), brandId);

        if (credential.getPasswordHash() == null) {
            throw new BizException(ResultCode.BAD_CREDENTIALS, "该手机号未设置密码，请使用微信登录");
        }
        if (!passwordEncoder.matches(request.getPassword(), credential.getPasswordHash())) {
            throw new BizException(ResultCode.BAD_CREDENTIALS);
        }
        if (!MEMBER_STATUS_NORMAL.equals(credential.getStatus())) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED, "会员账号当前不可用，请联系客服");
        }
        log.info("会员登录成功: brandId={} memberId={}", brandId, credential.getMemberId());
        return Result.ok(MemberTokenVO.builder()
                .token(issueToken(credential.getMemberId(), brandId))
                .expiresIn(jwtProvider.getExpireSeconds())
                .memberId(credential.getMemberId())
                .memberNo(credential.getMemberNo())
                .isNew(false)
                .nickname(credential.getNickname())
                .avatarUrl(credential.getAvatarUrl())
                .phone(realPhone(credential.getPhone()))
                .build());
    }

    @Operation(summary = "当前会员档案")
    @GetMapping("/member/me")
    public Result<MemberProfileVO> me() {
        Long memberId = UserContext.requireMemberId();
        Result<MemberProfileVO> response;
        try {
            response = memberClient.profile(memberId);
        } catch (Exception e) {
            log.error("查询会员档案失败: memberId={}", memberId, e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "会员服务暂不可用，请稍后重试");
        }
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            throw new BizException(ResultCode.MEMBER_NOT_FOUND);
        }
        return Result.ok(response.getData());
    }

    @Operation(summary = "小程序门店列表（免登录）")
    @GetMapping("/stores")
    public Result<List<PublicStoreVO>> stores() {
        List<SysStore> stores = storeMapper.selectList(Wrappers.<SysStore>lambdaQuery()
                .eq(SysStore::getBrandId, memberBrandProperties.getDefaultBrandId())
                // CLOSED（已停业）不进小程序选店列表；SUSPENDED 保留并如实返回 businessStatus
                .ne(SysStore::getStatus, "CLOSED")
                .orderByAsc(SysStore::getId));
        return Result.ok(stores.stream().map(MemberAuthController::toPublicStore).collect(Collectors.toList()));
    }

    @Operation(summary = "小程序门店详情（免登录）")
    @GetMapping("/stores/{id}")
    public Result<PublicStoreVO> store(@PathVariable("id") Long id) {
        SysStore store = storeMapper.selectById(id);
        if (store == null) {
            throw new BizException(ResultCode.STORE_NOT_FOUND);
        }
        return Result.ok(toPublicStore(store));
    }

    // ====== member-service 内部调用 ======

    /**
     * 按 openid 查已绑定会员。
     *
     * <p>未绑定（下游客端返回非 0 业务码）与调用异常都返回 null，交由上层走 bind，
     * 保证首次登录一定能开通会员；真正的绑定失败会在 bind 调用处抛出。
     */
    private MemberBindDTO findBoundMember(String openid, Long brandId) {
        Result<MemberBindDTO> response;
        try {
            response = memberClient.findByOpenid(openid, brandId);
        } catch (Exception e) {
            log.warn("查询 openid 绑定关系失败，转绑定流程: openid={} msg={}", openid, e.getMessage());
            return null;
        }
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            return null;
        }
        return response.getData();
    }

    private Result<MemberBindDTO> callBind(MemberWxBindRequestDTO request) {
        try {
            return memberClient.bind(request);
        } catch (Exception e) {
            log.error("调用 member-service 绑定接口失败: openid={}", request.getOpenid(), e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "会员服务暂不可用，请稍后重试");
        }
    }

    private Result<MemberBindDTO> callRegister(MemberRegisterDTO request) {
        try {
            return memberClient.register(request);
        } catch (Exception e) {
            log.error("调用 member-service 注册接口失败: phone={}", maskPhone(request.getPhone()), e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "会员服务暂不可用，请稍后重试");
        }
    }

    /**
     * 取登录凭据。
     *
     * <p>手机号未注册与密码错误统一返回 {@code BAD_CREDENTIALS}，避免被用来枚举手机号。
     */
    private MemberCredentialDTO loadCredential(String phone, Long brandId) {
        Result<MemberCredentialDTO> response;
        try {
            response = memberClient.credential(phone, brandId);
        } catch (Exception e) {
            log.error("调用 member-service 凭据接口失败", e);
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, "会员服务暂不可用，请稍后重试");
        }
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            throw new BizException(ResultCode.BAD_CREDENTIALS);
        }
        return response.getData();
    }

    /**
     * 拆包内部调用响应：下游业务码原样透传（例如手机号已注册 40002），
     * 便于前端按码提示；downstream 通信异常在调用处已转成 10404。
     */
    private <T> T unwrap(Result<T> response, String action) {
        if (response == null) {
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE, action + "：会员服务无响应");
        }
        if (response.getCode() != 0) {
            log.warn("{}: 下游返回 code={} message={}", action, response.getCode(), response.getMessage());
            throw new BizException(response.getCode(), response.getMessage());
        }
        if (response.getData() == null) {
            throw new BizException(ResultCode.DOWNSTREAM_NOT_FOUND, action + "：会员服务未返回数据");
        }
        return response.getData();
    }

    // ====== 令牌与出参 ======

    /** 用 find-or-bind 结果签发会员令牌，昵称/头像/手机号回查会员档案补齐。 */
    private MemberTokenVO buildToken(MemberBindDTO bind, Long brandId) {
        MemberProfileVO profile = loadProfile(bind.getMemberId());
        return MemberTokenVO.builder()
                .token(issueToken(bind.getMemberId(), brandId))
                .expiresIn(jwtProvider.getExpireSeconds())
                .memberId(bind.getMemberId())
                .memberNo(bind.getMemberNo())
                .isNew(Boolean.TRUE.equals(bind.getIsNew()))
                .nickname(profile == null ? null : profile.getNickname())
                .avatarUrl(profile == null ? null : profile.getAvatarUrl())
                .phone(profile == null ? null : realPhone(profile.getPhone()))
                .build();
    }

    private String issueToken(Long memberId, Long brandId) {
        LoginUser principal = LoginUser.builder()
                .memberId(memberId)
                .brandId(brandId)
                .userType("MEMBER")
                .roles(Set.of("MEMBER"))
                .build();
        return jwtProvider.issueMemberToken(principal);
    }

    /** 回查会员档案；失败不影响登录成功（昵称等可由 /member/me 再取）。 */
    private MemberProfileVO loadProfile(Long memberId) {
        try {
            Result<MemberProfileVO> response = memberClient.profile(memberId);
            if (response != null && response.getCode() == 0) {
                return response.getData();
            }
        } catch (Exception e) {
            log.warn("回查会员档案失败，登录结果不含昵称/头像: memberId={} msg={}", memberId, e.getMessage());
        }
        return null;
    }

    // ====== 门店出参适配 ======

    private static PublicStoreVO toPublicStore(SysStore store) {
        PublicStoreVO vo = new PublicStoreVO();
        vo.setId(store.getId());
        vo.setStoreName(store.getStoreName());
        vo.setAddress(store.getAddress());
        vo.setPhone(store.getPhone());
        // 表里只有 status（OPEN/SUSPENDED/CLOSED），契约里叫 businessStatus，直接映射，不另造状态
        vo.setBusinessStatus(store.getStatus());
        vo.setLatitude(store.getLatitude());
        vo.setLongitude(store.getLongitude());
        String[] hours = splitBusinessHours(store.getBusinessHours());
        vo.setOpenTime(hours == null ? null : hours[0]);
        vo.setCloseTime(hours == null ? null : hours[1]);
        return vo;
    }

    /**
     * 拆分营业时间文本，例如 "09:00-22:00" / "09:00~22:00"。
     *
     * <p>取不到两个时刻就返回 null（openTime/closeTime 均为 null），不做任何猜测性填充。
     */
    private static String[] splitBusinessHours(String businessHours) {
        if (!StringUtils.hasText(businessHours)) {
            return null;
        }
        Matcher matcher = CLOCK.matcher(businessHours);
        List<String> clocks = new ArrayList<>(2);
        while (matcher.find()) {
            clocks.add(matcher.group(1));
        }
        return clocks.size() < 2 ? null : new String[] {clocks.get(0), clocks.get(1)};
    }

    /** 占位手机号（WX 开头）不对小程序暴露。 */
    private static String realPhone(String phone) {
        return phone != null && REAL_PHONE.matcher(phone).matches() ? phone : null;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
