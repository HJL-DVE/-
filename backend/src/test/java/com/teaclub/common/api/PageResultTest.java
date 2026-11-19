package com.teaclub.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PageResultTest {

    @Test
    @DisplayName("总页数计算正确")
    void totalPages() {
        PageResult<String> p = new PageResult<>(List.of("a"), 101, 1, 20);
        assertThat(p.getTotalPages()).isEqualTo(6);
    }

    @Test
    @DisplayName("pageSize 为 0 时不抛除零异常")
    void zeroPageSize() {
        PageResult<String> p = new PageResult<>(List.of(), 0, 1, 0);
        assertThat(p.getTotalPages()).isZero();
    }

    @Test
    @DisplayName("records 为空时返回空列表而非 null")
    void nullRecordsBecomesEmpty() {
        PageResult<String> p = new PageResult<>(null, 0, 1, 20);
        assertThat(p.getRecords()).isNotNull().isEmpty();
    }
}
