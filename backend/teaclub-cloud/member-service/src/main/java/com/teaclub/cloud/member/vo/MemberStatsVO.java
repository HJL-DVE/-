package com.teaclub.cloud.member.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "会员统计（仪表盘）")
public class MemberStatsVO {

    @Schema(description = "会员总数")
    private Long totalMembers;
    @Schema(description = "近 7 天新增会员数")
    private Long newMembers7d;
    @Schema(description = "近 30 天有消费的会员数")
    private Long activeMembers30d;
    @Schema(description = "会员积分合计")
    private Long totalPoints;
}
