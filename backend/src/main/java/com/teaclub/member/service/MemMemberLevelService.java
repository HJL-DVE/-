package com.teaclub.member.service;

import com.teaclub.member.dto.MemberLevelSaveRequest;
import com.teaclub.member.vo.MemberLevelVO;

import java.util.List;

public interface MemMemberLevelService {

    List<MemberLevelVO> listAll();

    MemberLevelVO detail(Long id);

    Long create(MemberLevelSaveRequest request);

    void update(Long id, MemberLevelSaveRequest request);

    void delete(Long id);
}
