package com.teaclub.cloud.auth.client;

import com.teaclub.cloud.auth.client.dto.MemberBindDTO;
import com.teaclub.cloud.auth.client.dto.MemberCredentialDTO;
import com.teaclub.cloud.auth.client.dto.MemberRegisterDTO;
import com.teaclub.cloud.auth.client.dto.MemberWxBindRequestDTO;
import com.teaclub.cloud.auth.vo.MemberProfileVO;
import com.teaclub.cloud.core.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 会员端身份相关的内部客户端（member-service）。
 *
 * <p>{@code url} 默认为空即走 Nacos 服务发现；本地直连联调时通过
 * {@code teaclub.client.member-url} 指定地址。内部凭证由
 * {@code FeignRequestInterceptor} 自动带上 {@code X-Internal-Token}。
 */
@FeignClient(name = "member-service", path = "/api/member", contextId = "memberWxInternalClient",
        url = "${teaclub.client.member-url:}")
public interface MemberWxInternalClient {

    /** 按 openid 查询会员身份；未绑定时返回 code=404。 */
    @GetMapping("/internal/wx/member")
    Result<MemberBindDTO> findByOpenid(@RequestParam("openid") String openid,
                                       @RequestParam("brandId") Long brandId);

    /** openid find-or-bind 会员。 */
    @PostMapping("/internal/wx/bind")
    Result<MemberBindDTO> bind(@RequestBody MemberWxBindRequestDTO request);

    /** 手机号注册会员。 */
    @PostMapping("/internal/register")
    Result<MemberBindDTO> register(@RequestBody MemberRegisterDTO request);

    /** 按手机号取登录凭据（含 BCrypt 散列）。 */
    @GetMapping("/internal/credential")
    Result<MemberCredentialDTO> credential(@RequestParam("phone") String phone,
                                           @RequestParam("brandId") Long brandId);

    /** 会员档案（会员端 /me 透传）。 */
    @GetMapping("/internal/member/{memberId}")
    Result<MemberProfileVO> profile(@PathVariable("memberId") Long memberId);
}
