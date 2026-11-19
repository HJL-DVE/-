package com.teaclub.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "员工查询条件")
public class UserQuery {

    @Schema(description = "页码", example = "1")
    private Integer pageNo = 1;

    @Schema(description = "每页条数", example = "20")
    private Integer pageSize = 20;

    @Schema(description = "账号/姓名关键字")
    private String keyword;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "账号类型 HQ/STORE")
    private String userType;

    @Schema(description = "状态 ENABLED/DISABLED/LOCKED")
    private String status;

    @Schema(description = "门店 ID（筛选被授权该门店的员工）")
    private Long storeId;

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
