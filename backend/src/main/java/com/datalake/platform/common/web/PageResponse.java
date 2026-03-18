package com.datalake.platform.common.web;

import java.util.List;

public record PageResponse<T>(
    int pageNum,
    int pageSize,
    long total,
    List<T> records
) {
}

