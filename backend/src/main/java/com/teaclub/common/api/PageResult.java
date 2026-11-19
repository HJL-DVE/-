package com.teaclub.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应结构。
 */
@Data
@Schema(description = "分页结果")
public class PageResult<T> {

    @Schema(description = "当前页数据")
    private List<T> records;

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "当前页码")
    private long pageNo;

    @Schema(description = "每页条数")
    private long pageSize;

    @Schema(description = "总页数")
    private long totalPages;

    public PageResult() {
        this.records = Collections.emptyList();
    }

    public PageResult(List<T> records, long total, long pageNo, long pageSize) {
        this.records = records == null ? Collections.<T>emptyList() : records;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.totalPages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        List<T> list = new ArrayList<>(page.getRecords().size());
        for (E e : page.getRecords()) {
            list.add(converter.apply(e));
        }
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
