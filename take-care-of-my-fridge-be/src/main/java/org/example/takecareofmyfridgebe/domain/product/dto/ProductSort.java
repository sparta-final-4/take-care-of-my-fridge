package org.example.takecareofmyfridgebe.domain.product.dto;

// 정렬 조건은 ProductRepositoryImpl이 QueryDSL 정렬로 바꾼다.
public enum ProductSort {
    LATEST,
    PRICE_ASC,
    PRICE_DESC
}
