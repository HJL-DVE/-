package com.teaclub.product.service;

import com.teaclub.product.dto.CategorySaveRequest;
import com.teaclub.product.vo.CategoryVO;

import java.util.List;

public interface PrdCategoryService {
    List<CategoryVO> tree();
    CategoryVO detail(Long id);
    Long create(CategorySaveRequest request);
    void update(Long id, CategorySaveRequest request);
    void delete(Long id);
}
