package org.example.takecareofmyfridgebe.domain.sale.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListResponse;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 행사 목록 탭에 보여 줄 행사 목록
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventListQueryService {

    private final SaleEventRepository saleEventRepository;

    public PageResponse<SaleEventListResponse> getSaleEvents(SaleEventListRequest request) {
        PageRequest pageable = PageRequest.of(request.page() - 1, request.size());
        return PageResponse.of(saleEventRepository.findSaleEvents(request, pageable));
    }
}
