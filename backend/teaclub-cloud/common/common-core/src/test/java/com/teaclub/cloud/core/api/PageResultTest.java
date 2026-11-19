package com.teaclub.cloud.core.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    @DisplayName("总页数计算正确")
    void totalPages() {
        assertThat(new PageResult<String>(List.of("a"), 101, 1, 20).getTotalPages()).isEqualTo(6);
    }

    @Test
    @DisplayName("pageSize 为 0 不抛除零异常")
    void zeroPageSize() {
        assertThat(new PageResult<String>(List.of(), 0, 1, 0).getTotalPages()).isZero();
    }

    @Test
    @DisplayName("null records 转为空列表")
    void nullRecords() {
        assertThat(new PageResult<String>(null, 0, 1, 20).getRecords()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("empty 工厂方法")
    void emptyFactory() {
        PageResult<String> p = PageResult.empty(2, 10);
        assertThat(p.getTotal()).isZero();
        assertThat(p.getPageNo()).isEqualTo(2);
        assertThat(p.getRecords()).isEmpty();
    }
}
