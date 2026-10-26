package com.example.common.web;

import lombok.Getter;

import java.util.List;

/**
 * 与框架无关的分页响应，由 MyBatis-Plus 的 IPage 转换而来。
 *
 * <p>位于共享模块，供各服务复用同一份分页契约。
 */
@Getter
public class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final long pageNum;
    private final long pageSize;
    private final long pages;

    public PageResult(List<T> records, long total, long pageNum, long pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = pageSize == 0 ? 0 : (total + pageSize - 1) / pageSize;
    }

    /** 静态构造，等价于 {@code new PageResult<>(records, total, pageNum, pageSize)} */
    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        return new PageResult<T>(records, total, pageNum, pageSize);
    }
}