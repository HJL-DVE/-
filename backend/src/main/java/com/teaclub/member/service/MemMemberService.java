package com.teaclub.member.service;

import com.teaclub.common.api.PageResult;
import com.teaclub.member.dto.MemberQuery;
import com.teaclub.member.dto.MemberUpdateRequest;
import com.teaclub.member.vo.MemberVO;

public interface MemMemberService {

    PageResult<MemberVO> page(MemberQuery query);

    /** 按手机号精确查询（收银台核验会员身份）。 */
    MemberVO findByPhone(String phone);

    MemberVO detail(Long id);

    void update(Long id, MemberUpdateRequest request);

    void updateStatus(Long id, String status, String reason);

    void delete(Long id);
}
