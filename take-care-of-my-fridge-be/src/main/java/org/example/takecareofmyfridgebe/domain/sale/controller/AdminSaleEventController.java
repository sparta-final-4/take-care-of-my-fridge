package org.example.takecareofmyfridgebe.domain.sale.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventCreateResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductsRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventCreateService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleCandidateProductQueryService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventListQueryService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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

    @PostMapping("/special")
    public ResponseEntity<ApiResponse<SaleEventCreateResponse>> createSpecialSale(
            @Valid @RequestBody SpecialSaleCreateRequest request
    ) {
        SaleEventCreateResponse response = saleEventCreateService.createSpecialSale(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/flash-drop")
    public ResponseEntity<ApiResponse<SaleEventCreateResponse>> createFlashDrop(
            @Valid @RequestBody FlashDropCreateRequest request
    ) {
        SaleEventCreateResponse response = saleEventCreateService.createFlashDrop(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // 등록 화면의 상품 표. 판매 중이고 재고가 있으며 예정·진행 중인 행사에 들지 않은 상품만 준다.
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<SaleCandidateProductResponse>>> getCandidateProducts(
            @Valid @ModelAttribute SaleCandidateProductsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(saleCandidateProductQueryService.getCandidates(request)));
    }

    // 행사 목록 탭. 상태·종류·시작일·종료일로 거르고 최신 행사부터 준다.
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SaleEventListResponse>>> getSaleEvents(
            @Valid @ModelAttribute SaleEventListRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(saleEventListQueryService.getSaleEvents(request)));
    }
}
