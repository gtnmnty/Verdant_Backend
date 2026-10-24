package com.verdant.salon_ecomm.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

// One place for turning the API's 1-based page / pageSize arguments into a Spring PageRequest.
// Every list endpoint caps its page size, so a client can't ask for an unbounded page.
public final class PageRequests {

    public static final int DEFAULT_MAX_SIZE = 100;

    private PageRequests() {}

    // 1-based page number, at least 1.
    public static int page(int page) {
        return Math.max(page, 1);
    }

    public static int size(int pageSize, int maxSize) {
        return Math.clamp(pageSize, 1, maxSize);
    }

    public static PageRequest of(int page, int pageSize, int maxSize, Sort sort) {
        return PageRequest.of(page(page) - 1, size(pageSize, maxSize), sort);
    }
}
