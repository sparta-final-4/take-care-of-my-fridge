package org.example.takecareofmyfridgebe.domain.sale.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductsRequest;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 행사 등록 화면의 상품 표에 보여 줄, 행사에 넣을 수 있는 상품 목록
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleCandidateProductQueryService {

    private final SaleEventItemRepository saleEventItemRepository;

    public PageResponse<SaleCandidateProductResponse> getCandidates(SaleCandidateProductsRequest request) {
        PageRequest pageable = PageRequest.of(request.page() - 1, request.size());
        return PageResponse.of(saleEventItemRepository.findCandidateProducts(request, pageable)
                .map(SaleCandidateProductResponse::from));
    }
}
