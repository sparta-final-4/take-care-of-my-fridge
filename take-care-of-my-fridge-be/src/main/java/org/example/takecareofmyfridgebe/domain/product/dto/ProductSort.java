package org.example.takecareofmyfridgebe.domain.product.dto;

// 정렬 조건은 ProductRepositoryImpl이 QueryDSL 정렬로 바꾼다.
public enum ProductSort {
    LATEST,
    // 검색어가 있을 때 ES 점수 순. MySQL에는 점수가 없어 LATEST와 같다
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC
}
