package com.teaclub.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "门店授权请求")
public class GrantStoresRequest {

    @Schema(description = "门店 ID 列表，传空数组表示收回全部授权")
    private List<Long> storeIds;

    @Schema(description = "默认门店 ID")
    private Long defaultStoreId;
}
