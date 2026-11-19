package com.teaclub.cloud.product.service;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.product.dto.SpuQuery;
import com.teaclub.cloud.product.dto.SpuSaveRequest;
import com.teaclub.cloud.product.vo.SpuVO;

public interface PrdSpuService {

    PageResult<SpuVO> page(SpuQuery query);

    SpuVO detail(Long id);

    Long create(SpuSaveRequest request);

    void update(Long id, SpuSaveRequest request);

    void delete(Long id);

    void updateStatus(Long id, String status);
}
