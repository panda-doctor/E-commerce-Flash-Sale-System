package com.ghb.ecommerceflashsalesystem.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> records;
    private long page;
    private long pageSize;
    private long total;
    private long pages;
}
