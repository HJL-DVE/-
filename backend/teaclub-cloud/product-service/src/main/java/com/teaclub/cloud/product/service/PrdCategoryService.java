package com.teaclub.cloud.product.service;

import com.teaclub.cloud.product.dto.CategorySaveRequest;
import com.teaclub.cloud.product.vo.CategoryVO;

import java.util.List;

public interface PrdCategoryService {
    List<CategoryVO> tree();
    CategoryVO detail(Long id);
    Long create(CategorySaveRequest request);
    void update(Long id, CategorySaveRequest request);
    void delete(Long id);
}
