package com.teaclub.cloud.member.service;

import com.teaclub.cloud.member.dto.MemberLevelSaveRequest;
import com.teaclub.cloud.member.vo.MemberLevelVO;

import java.util.List;

public interface MemMemberLevelService {

    List<MemberLevelVO> listAll();

    /** 会员端：该会员所属品牌的启用等级列表（按等级序号升序）。 */
    List<MemberLevelVO> listForMember(Long memberId);

    MemberLevelVO detail(Long id);

    Long create(MemberLevelSaveRequest request);

    void update(Long id, MemberLevelSaveRequest request);

    void delete(Long id);
}
