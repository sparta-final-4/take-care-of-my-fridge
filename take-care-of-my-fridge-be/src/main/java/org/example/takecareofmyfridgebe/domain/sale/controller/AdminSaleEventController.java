package org.example.takecareofmyfridgebe.domain.sale.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventDetailResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventUpdateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventsCreateResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductsRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventCreateService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleCandidateProductQueryService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventDetailQueryService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventListQueryService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventUpdateService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sale-events")
@RequiredArgsConstructor
public class AdminSaleEventController {

    private final SaleEventCreateService saleEventCreateService;
    private final SaleCandidateProductQueryService saleCandidateProductQueryService;
    private final SaleEventListQueryService saleEventListQueryService;
    private final SaleEventDetailQueryService saleEventDetailQueryService;
    private final SaleEventUpdateService saleEventUpdateService;

    @PostMapping("/special")
    public ResponseEntity<ApiResponse<SaleEventsCreateResponse>> createSpecialSale(
            @Valid @RequestBody SpecialSaleCreateRequest request
    ) {
        SaleEventsCreateResponse response = saleEventCreateService.createSpecialSale(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/flash-drop")
    public ResponseEntity<ApiResponse<SaleEventsCreateResponse>> createFlashDrop(
            @Valid @RequestBody FlashDropCreateRequest request
    ) {
        SaleEventsCreateResponse response = saleEventCreateService.createFlashDrop(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // 등록 화면의 상품 표. 판매 중이고 재고가 있으며 예정·진행 중인 행사에 들지 않은 상품만 준다.
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<SaleCandidateProductResponse>>> getCandidateProducts(
            @Valid @ModelAttribute SaleCandidateProductsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(saleCandidateProductQueryService.getCandidates(request)));
    }

    // 행사 목록 탭. 상태·종류·상품 이름·시작일·종료일로 거르고, 정렬을 비우면 최신 행사부터 준다.
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SaleEventListResponse>>> getSaleEvents(
            @Valid @ModelAttribute SaleEventListRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(saleEventListQueryService.getSaleEvents(request)));
    }

    // 행사 수정 화면에 보여 줄 행사 하나와 그 상품들.
    @GetMapping("/{saleEventId}")
    public ResponseEntity<ApiResponse<SaleEventDetailResponse>> getSaleEvent(@PathVariable Long saleEventId) {
        return ResponseEntity.ok(ApiResponse.ok(saleEventDetailQueryService.getSaleEvent(saleEventId)));
    }

    // 시작 전인 행사의 시각과 상품 값을 한 번에 고친다. 상품을 넣거나 빼지는 않는다.
    @PatchMapping("/{saleEventId}")
    public ResponseEntity<ApiResponse<Void>> updateSaleEvent(
            @PathVariable Long saleEventId,
            @Valid @RequestBody SaleEventUpdateRequest request
    ) {
        saleEventUpdateService.update(saleEventId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
