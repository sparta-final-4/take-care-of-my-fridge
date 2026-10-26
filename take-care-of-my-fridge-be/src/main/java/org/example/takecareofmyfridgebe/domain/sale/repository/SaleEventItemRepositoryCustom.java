package org.example.takecareofmyfridgebe.domain.sale.repository;

import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductsRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SaleEventItemRepositoryCustom {

    // 행사에 넣을 수 있는 상품(판매 중이고 재고가 있으며 예정·진행 중인 행사에 들지 않은 상품)을 최신 상품부터 찾는다.
    Page<Product> findCandidateProducts(SaleCandidateProductsRequest condition, Pageable pageable);
}
