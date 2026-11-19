package com.teaclub.cloud.wallet.client;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.wallet.dto.MemberBriefDTO;
import com.teaclub.cloud.wallet.dto.MemberLevelBriefDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * wallet-service 调用 member-service 获取会员信息。
 *
 * <p>会员资料与等级属 member-service 域（member_db），
 * 钱包侧只保存 memberId，<b>不直接访问 member_db</b>。
 */
@FeignClient(name = "member-service", path = "/api/member/internal",
        contextId = "walletMemberInternalClient")
public interface MemberInternalClient {

    /** 会员简要信息。 */
    @GetMapping("/user/{memberId}/brief")
    Result<MemberBriefDTO> brief(@PathVariable("memberId") Long memberId);

    /** 会员简要信息（批量）。 */
    @PostMapping("/users/batch")
    Result<List<MemberBriefDTO>> batch(@RequestBody List<Long> memberIds);

    /** 会员等级信息（批量）。 */
    @PostMapping("/levels/batch")
    Result<List<MemberLevelBriefDTO>> levelsBatch(@RequestBody List<Long> levelIds);

    /**
     * 当前门店数据范围内可见的会员 ID（注册门店在范围内 或 在范围内门店消费过）。
     */
    @GetMapping("/visible-member-ids")
    Result<List<Long>> visibleMemberIds(@RequestParam("brandId") Long brandId,
                                        @RequestParam("storeIds") List<Long> storeIds);

    /** 按手机号/昵称/会员号检索会员 ID（钱包列表按会员维度过滤用）。 */
    @GetMapping("/member-ids")
    Result<List<Long>> memberIds(@RequestParam("brandId") Long brandId,
                                 @RequestParam(value = "phone", required = false) String phone,
                                 @RequestParam(value = "keyword", required = false) String keyword);
}
