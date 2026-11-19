package com.teaclub.cloud.auth.service;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.auth.dto.StoreQuery;
import com.teaclub.cloud.auth.dto.StoreSaveRequest;
import com.teaclub.cloud.auth.vo.StoreVO;

import java.util.List;

public interface SysStoreService {

    PageResult<StoreVO> page(StoreQuery query);

    /** 当前用户可访问的门店列表（用于下拉选择）。 */
    List<StoreVO> listAccessible();

    StoreVO detail(Long id);

    Long create(StoreSaveRequest request);

    void update(Long id, StoreSaveRequest request);

    void delete(Long id);

    void updateStatus(Long id, String status);
}
