package com.teaclub.cloud.auth.service;

import com.teaclub.cloud.auth.dto.BrandUpdateRequest;
import com.teaclub.cloud.auth.vo.BrandVO;

public interface SysBrandService {
    BrandVO current();
    void update(BrandUpdateRequest request);

    /** 单独更新退款规则（只改退款相关字段，不动品牌基础信息） */
    void updateRefundRule(BrandUpdateRequest request);
}
