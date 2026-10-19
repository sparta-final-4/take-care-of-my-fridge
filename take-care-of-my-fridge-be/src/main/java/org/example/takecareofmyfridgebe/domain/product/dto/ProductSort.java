package org.example.takecareofmyfridgebe.domain.product.dto;

import org.springframework.data.domain.Sort;

public enum ProductSort {
    LATEST(Sort.by(Sort.Direction.DESC, "id")),
    PRICE_ASC(Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id"))),
    PRICE_DESC(Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id")));

    private final Sort sort;

    ProductSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
