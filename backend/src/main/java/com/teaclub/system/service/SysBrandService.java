package com.teaclub.system.service;

import com.teaclub.system.dto.BrandUpdateRequest;
import com.teaclub.system.vo.BrandVO;

public interface SysBrandService {
    BrandVO current();
    void update(BrandUpdateRequest request);
}
