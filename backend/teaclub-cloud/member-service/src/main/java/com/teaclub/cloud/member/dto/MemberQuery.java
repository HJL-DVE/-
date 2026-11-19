package com.teaclub.cloud.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "会员查询条件")
public class MemberQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 20;

    @Schema(description = "手机号（精确/模糊）")
    private String phone;

    @Schema(description = "昵称/会员号关键字")
    private String keyword;

    private Long levelId;
    private String status;

    @Schema(description = "注册门店 ID")
    private Long registerStoreId;

    private LocalDate createTimeStart;
    private LocalDate createTimeEnd;

    public int safePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
