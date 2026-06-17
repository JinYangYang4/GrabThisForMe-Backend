package com.study.grabthisforme.service.view;

import java.util.List;

public record PageView<T>(
    List<T> items,
    long total,
    int limit,
    int offset,
    boolean hasMore
) {
}
