package com.teaclub.cloud.member.service;

import com.teaclub.cloud.member.dto.MemberRegisterRequest;
import com.teaclub.cloud.member.dto.MemberWxBindRequest;
import com.teaclub.cloud.member.vo.MemberCredentialVO;
import com.teaclub.cloud.member.vo.MemberWxBindVO;
import com.teaclub.cloud.member.vo.MemberWxMemberVO;

/**
 * 会员开户与微信绑定（会员端身份体系的落库侧）。
 *
 * <p>由 auth-service 的内部调用驱动：
 * 小程序登录 → 查/绑定 openid；手机号注册 → 建会员。
 */
public interface MemMemberOnboardingService {

    /** 按 openid 查会员身份；无绑定关系返回 null。 */
    MemberWxMemberVO findByOpenid(String openid, Long brandId);

    /**
     * openid 绑定会员：已绑定直接返回；未绑定时若手机号命中既有会员则直接绑定，
     * 否则新建会员再绑定。
     */
    MemberWxBindVO bind(MemberWxBindRequest request);

    /** 手机号注册会员；手机号已存在抛 MEMBER_PHONE_EXISTS。 */
    MemberWxBindVO register(MemberRegisterRequest request);

    /** 按手机号取登录凭据（含密码散列）；不存在返回 null。 */
    MemberCredentialVO credential(String phone, Long brandId);
}
