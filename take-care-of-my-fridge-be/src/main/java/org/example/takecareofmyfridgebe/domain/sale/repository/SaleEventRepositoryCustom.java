package org.example.takecareofmyfridgebe.domain.sale.repository;

import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SaleEventRepositoryCustom {

    // 조건에 맞는 행사의 상품을 최신 행사부터 한 행씩 찾는다.
    Page<SaleEventListResponse> findSaleEvents(SaleEventListRequest condition, Pageable pageable);
}
