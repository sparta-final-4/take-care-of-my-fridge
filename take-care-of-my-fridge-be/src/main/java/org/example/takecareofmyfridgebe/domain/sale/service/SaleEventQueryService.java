package org.example.takecareofmyfridgebe.domain.sale.service;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductsRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListResponse;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 화면에 보여 줄 행사 조회. 목록 탭, 수정 화면의 행사 하나와 그 항목, 등록 화면의 후보 상품을 한곳에서 답한다.
// 상세에 붙일 상품은 퍼사드가 상품 서비스에서 읽어 합친다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventQueryService {

    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;

    public PageResponse<SaleEventListResponse> getSaleEvents(SaleEventListRequest request) {
        PageRequest pageable = PageRequest.of(request.page() - 1, request.size());
        return PageResponse.of(saleEventRepository.findSaleEvents(request, pageable));
    }

    public SaleEvent getSaleEvent(Long saleEventId) {
        return saleEventRepository.findById(saleEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_EVENT_NOT_FOUND));
    }

    public List<SaleEventItem> getSaleEventItems(Long saleEventId) {
        return saleEventItemRepository.findBySaleEventIdOrderById(saleEventId);
    }

    // 알림 문구에 행사 시작 시각이 들어가서 행사를 함께 읽는다
    public List<SaleEventItem> getItemsWithSaleEvent(Collection<Long> saleEventIds) {
        return saleEventItemRepository.findAllWithSaleEventBySaleEventIdIn(saleEventIds);
    }

    public PageResponse<SaleCandidateProductResponse> getCandidates(SaleCandidateProductsRequest request) {
        PageRequest pageable = PageRequest.of(request.page() - 1, request.size());
        return PageResponse.of(saleEventItemRepository.findCandidateProducts(request, pageable)
                .map(SaleCandidateProductResponse::from));
    }
}
