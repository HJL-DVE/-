package com.teaclub.system.service;

import com.teaclub.common.api.PageResult;
import com.teaclub.system.dto.StoreQuery;
import com.teaclub.system.dto.StoreSaveRequest;
import com.teaclub.system.vo.StoreVO;

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
