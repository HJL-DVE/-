package com.teaclub.cloud.member.service.support;

import com.teaclub.cloud.member.mapper.MemMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 会员号分配器。
 *
 * <p>格式沿用库内既有数据（{@code M + yyyy + 4 位序号}，例 {@code M20250001}）：
 * 取该品牌当年最大流水号的序号 +1。并发下仍依赖 {@code uk_member_no(brand_id, member_no)}
 * 兜底，由调用方捕获唯一键冲突后重试。
 */
@Component
@RequiredArgsConstructor
public class MemberNoAllocator {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final int SEQ_WIDTH = 4;

    private final MemMemberMapper memberMapper;

    public String next(Long brandId) {
        String prefix = "M" + LocalDate.now().format(YEAR);
        String maxNo = memberMapper.selectMaxMemberNo(brandId, prefix + "%");
        int seq = 1;
        if (maxNo != null && maxNo.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(maxNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        return prefix + String.format("%0" + SEQ_WIDTH + "d", seq);
    }
}
