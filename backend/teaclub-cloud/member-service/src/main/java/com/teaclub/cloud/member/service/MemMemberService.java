package com.teaclub.cloud.member.service;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.member.dto.MemberQuery;
import com.teaclub.cloud.member.dto.MemberSelfUpdateRequest;
import com.teaclub.cloud.member.dto.MemberUpdateRequest;
import com.teaclub.cloud.member.dto.MemberCreateRequest;
import com.teaclub.cloud.member.vo.MemberStatsVO;
import com.teaclub.cloud.member.vo.MemberVO;

public interface MemMemberService {

    PageResult<MemberVO> page(MemberQuery query);

    /** 按手机号精确查询（收银台核验会员身份）。 */
    MemberVO findByPhone(String phone);

    /** 按会员号精确查询（收银台扫码：二维码内容即 memberNo）。 */
    MemberVO findByMemberNo(String memberNo);

    /** 仪表盘会员统计，遵守当前账号的门店数据权限。 */
    MemberStatsVO stats();

    MemberVO detail(Long id);
    /** 后台手工建档：店员录入手机号/称呼，生成会员号、钱包与积分账户 */
    Long create(MemberCreateRequest request);

    void update(Long id, MemberUpdateRequest request);

    void updateStatus(Long id, String status, String reason);

    void delete(Long id);

    /**
     * 会员端：查看本人档案。
     *
     * <p>会员身份即数据范围，不做门店数据权限校验，手机号返回明文。
     */
    MemberVO profile(Long memberId);

    /** 会员端：修改本人资料（仅昵称 / 头像 / 性别）。 */
    void updateProfile(Long memberId, MemberSelfUpdateRequest request);
}
