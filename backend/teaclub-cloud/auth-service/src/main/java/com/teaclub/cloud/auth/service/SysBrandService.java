package com.teaclub.cloud.auth.service;

import com.teaclub.cloud.auth.dto.BrandUpdateRequest;
import com.teaclub.cloud.auth.vo.BrandVO;

public interface SysBrandService {
    BrandVO current();
    void update(BrandUpdateRequest request);
}
