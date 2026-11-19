package com.teaclub.product.service;

import com.teaclub.common.api.PageResult;
import com.teaclub.product.dto.SpuQuery;
import com.teaclub.product.dto.SpuSaveRequest;
import com.teaclub.product.vo.SpuVO;

public interface PrdSpuService {

    PageResult<SpuVO> page(SpuQuery query);

    SpuVO detail(Long id);

    Long create(SpuSaveRequest request);

    void update(Long id, SpuSaveRequest request);

    void delete(Long id);

    void updateStatus(Long id, String status);
}
